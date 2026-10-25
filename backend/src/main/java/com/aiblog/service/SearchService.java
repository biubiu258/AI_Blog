package com.aiblog.service;

import com.aiblog.config.AiProperties;
import com.aiblog.dto.ai.AiSearchResponse;
import com.aiblog.dto.ai.SearchItem;
import com.aiblog.entity.Article;
import com.aiblog.mapper.ArticleMapper;
import com.aiblog.security.CurrentUser;
import com.aiblog.util.HtmlSanitizer;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * P1 站内语义搜索：双通道合并（向量语义通道 + 本人草稿/私密关键词通道）。
 * 可见性由 Java 裁决；Python 不可用时 fail-open 降级为关键词检索（契约 5.5 / 5.6）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private static final int DEFAULT_TOP_K = 10;
    private static final int SNIPPET_LEN = 120;

    private final WebClient aiWebClient;
    private final AiProperties properties;
    private final ArticleMapper articleMapper;
    private final ObjectMapper objectMapper;
    private final AiAuditService auditService;

    public AiSearchResponse search(String query, Integer topK) {
        String q = query == null ? "" : query.trim();
        int limit = clampTopK(topK);
        Long userId = CurrentUser.id();

        String traceId = UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("trace_id", traceId);
        request.put("query", q);
        request.put("top_k", limit);
        Long auditId = auditService.begin(traceId, userId, null, "search", null, request);
        long startedAt = System.nanoTime();

        SemanticResult semantic = semanticHits(q, limit);
        List<SearchItem> semanticItems = buildSemanticItems(semantic.hits(), userId);
        List<SearchItem> keywordItems = buildKeywordItems(q, userId, limit);
        AiSearchResponse response =
                new AiSearchResponse(semantic.degraded(), assemble(semanticItems, keywordItems, limit));

        // 语义命中/降级后关键词结果都是 AI 通道的返回，全部留存
        auditService.finish(auditId, AiAuditService.AiResult.success(
                toJson(response), semantic.degraded() ? "fallback" : "python",
                0, 0, (System.nanoTime() - startedAt) / 1_000_000L, null, null));
        return response;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    /** 通道一：转发 Python /v1/search；异常/超时/非法响应一律降级（degraded=true）。 */
    private SemanticResult semanticHits(String q, int limit) {
        String mock = properties.getSearchMockHits();
        if (StringUtils.hasText(mock)) {
            try {
                return new SemanticResult(false, parseHits(objectMapper.readTree(mock)));
            } catch (Exception e) {
                log.warn("search-mock-hits 解析失败，按降级处理: {}", e.getMessage());
                return new SemanticResult(true, List.of());
            }
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", q);
            body.put("top_k", limit);
            JsonNode node = aiWebClient.post()
                    .uri("/v1/search")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofMillis(properties.getSearchReadTimeoutMs()))
                    .block();
            return new SemanticResult(false, node == null ? List.of() : parseHits(node.path("hits")));
        } catch (Exception e) {
            log.warn("语义检索不可用，降级为关键词检索(fail-open): {}", e.getMessage());
            return new SemanticResult(true, List.of());
        }
    }

    /** 语义命中 -> 可见的 SearchItem（保留命中顺序，由 assemble 统一排序）。 */
    private List<SearchItem> buildSemanticItems(List<RawHit> hits, Long userId) {
        if (hits.isEmpty()) {
            return List.of();
        }
        Map<Long, Article> byId = loadArticles(hits.stream().map(RawHit::id).toList());
        List<SearchItem> items = new ArrayList<>();
        for (RawHit hit : hits) {
            Article article = byId.get(hit.id());
            if (!isVisible(article, userId)) {
                continue;
            }
            String snippet = StringUtils.hasText(hit.snippet()) ? hit.snippet() : snippetOf(article);
            items.add(SearchItem.semantic(article.getId(),
                    StringUtils.hasText(hit.title()) ? hit.title() : article.getTitle(),
                    snippet, hit.score(), article.getStatus()));
        }
        return items;
    }

    /** 通道二：本人草稿/私密的关键词命中（草稿/私密不入向量库，见设计 D3）。 */
    private List<SearchItem> buildKeywordItems(String q, Long userId, int limit) {
        if (!StringUtils.hasText(q) || userId == null) {
            return List.of();
        }
        List<Article> articles = articleMapper.selectList(Wrappers.<Article>lambdaQuery()
                .eq(Article::getAuthorId, userId)
                .in(Article::getStatus, "draft", "private")
                .and(w -> w.like(Article::getTitle, q).or().like(Article::getContentHtml, q))
                .orderByDesc(Article::getUpdatedAt)
                .last("LIMIT " + limit));
        return articles.stream()
                .map(a -> SearchItem.keyword(a.getId(), a.getTitle(), snippetOf(a), a.getStatus()))
                .toList();
    }

    /** 合并去重：语义优先（score 降序），关键词按时间续接，截断到 limit。 */
    static List<SearchItem> assemble(List<SearchItem> semantic, List<SearchItem> keyword, int limit) {
        List<SearchItem> sorted = new ArrayList<>(semantic);
        sorted.sort(Comparator.comparing(SearchItem::getScore,
                Comparator.nullsLast(Comparator.reverseOrder())));
        LinkedHashMap<Long, SearchItem> merged = new LinkedHashMap<>();
        sorted.forEach(item -> merged.putIfAbsent(item.getId(), item));
        keyword.forEach(item -> merged.putIfAbsent(item.getId(), item));
        List<SearchItem> result = new ArrayList<>(merged.values());
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }

    /** 可见性（设计 §3.4）：已发布公开人人可见；草稿/私密仅作者本人可见。 */
    static boolean isVisible(Article article, Long currentUserId) {
        if (article == null) {
            return false;
        }
        if ("published".equals(article.getStatus())) {
            return true;
        }
        return currentUserId != null && Objects.equals(article.getAuthorId(), currentUserId);
    }

    private Map<Long, Article> loadArticles(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return articleMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Article::getId, Function.identity()));
    }

    private List<RawHit> parseHits(JsonNode hits) {
        if (hits == null || !hits.isArray()) {
            return List.of();
        }
        List<RawHit> result = new ArrayList<>();
        for (JsonNode node : hits) {
            Long id = parseId(node.path("article_id"));
            if (id == null) {
                continue;
            }
            Double score = node.hasNonNull("score") ? node.get("score").asDouble() : null;
            result.add(new RawHit(id, node.path("title").asText(""),
                    node.path("snippet").asText(""), score));
        }
        return result;
    }

    /** article_id 契约上是字符串（"88"），也兼容 JSON number。 */
    private static Long parseId(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asLong();
        }
        String text = node.asText(null);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String snippetOf(Article article) {
        String text = HtmlSanitizer.toPlainText(article.getContentHtml());
        return text.length() > SNIPPET_LEN ? text.substring(0, SNIPPET_LEN) : text;
    }

    private int clampTopK(Integer topK) {
        int max = properties.getSearchMaxTopK();
        if (topK == null) {
            return Math.min(DEFAULT_TOP_K, max);
        }
        if (topK < 1) {
            return 1;
        }
        return Math.min(topK, max);
    }

    /** 语义通道结果：degraded 表示已降级为关键词检索。 */
    private record SemanticResult(boolean degraded, List<RawHit> hits) {
    }

    /** Python 命中的原始条目（article_id 可能为字符串或数字）。 */
    private record RawHit(Long id, String title, String snippet, Double score) {
    }
}
