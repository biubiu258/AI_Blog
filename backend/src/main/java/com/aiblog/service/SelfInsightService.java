package com.aiblog.service;

import com.aiblog.ai.PythonResponseException;
import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.config.AiProperties;
import com.aiblog.entity.Article;
import com.aiblog.mapper.ArticleMapper;
import com.aiblog.security.CurrentUser;
import com.aiblog.util.HtmlSanitizer;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 洞察式回顾：取当前用户最近 10 篇已发布文章，转发 Python /v1/self-insight，
 * 返回主题脉络、反复意象与写作习惯观察（只回顾，不建议）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SelfInsightService {

    private static final int MAX_ARTICLES = 10;
    private static final int MAX_ARTICLE_TEXT = 2000;
    private static final String STATUS_PUBLISHED = "published";
    private static final String DISCLAIMER = "以上仅为回顾，不构成写作建议";

    private final ArticleMapper articleMapper;
    private final WebClient aiWebClient;
    private final AiProperties properties;
    private final ObjectMapper objectMapper;

    public Map<String, Object> insight() {
        Long userId = CurrentUser.id();
        List<Article> articles = articleMapper.selectList(Wrappers.<Article>lambdaQuery()
                .eq(Article::getAuthorId, userId)
                .eq(Article::getStatus, STATUS_PUBLISHED)
                .orderByDesc(Article::getPublishedAt)
                .last("LIMIT " + MAX_ARTICLES));
        if (articles == null || articles.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "至少需要 1 篇已发布文章");
        }

        List<Map<String, Object>> payloadArticles = new ArrayList<>();
        for (Article article : articles) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", article.getTitle() == null ? "" : article.getTitle());
            item.put("content", plainText(article));
            payloadArticles.add(item);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_id", userId);
        payload.put("articles", payloadArticles);

        JsonNode node = callInsight(userId, payload);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", node.path("summary").asText(""));
        result.put("themes", toStringList(node.path("themes")));
        result.put("motifs", toStringList(node.path("motifs")));
        result.put("habits", toStringList(node.path("habits")));
        String disclaimer = node.path("disclaimer").asText("");
        result.put("disclaimer", StringUtils.hasText(disclaimer) ? disclaimer : DISCLAIMER);
        result.put("articleCount", articles.size());
        result.put("generatedAt", LocalDateTime.now());
        return result;
    }

    private JsonNode callInsight(Long userId, Map<String, Object> payload) {
        String body;
        try {
            body = aiWebClient.post()
                    .uri("/v1/self-insight")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), response ->
                            response.bodyToMono(String.class).defaultIfEmpty("")
                                    .flatMap(raw -> Mono.error(
                                            new PythonResponseException(response.statusCode(), raw))))
                    .bodyToMono(String.class)
                    .block(Duration.ofMillis(properties.getReadTimeoutMs()));
        } catch (Exception e) {
            log.warn("AI 洞察式回顾调用失败 userId={}: {}", userId, e.getMessage());
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 服务暂时不可用，请稍后重试");
        }
        if (!StringUtils.hasText(body)) {
            throw new BusinessException(ErrorCode.AI_EMPTY_OUTPUT, "洞察式回顾结果为空，请重试");
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("AI 洞察式回顾响应解析失败 userId={}: {}", userId, e.getMessage());
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 服务返回格式异常，请稍后重试");
        }
    }

    /** 正文转纯文本后截断到 2000 字；空白正文用摘要兜底，避免 Python 校验失败。 */
    private String plainText(Article article) {
        String text = HtmlSanitizer.toPlainText(
                article.getContentHtml() == null ? "" : article.getContentHtml()).strip();
        if (text.isEmpty()) {
            text = StringUtils.hasText(article.getSummary())
                    ? article.getSummary().strip() : "（本文暂无正文）";
        }
        return text.length() > MAX_ARTICLE_TEXT ? text.substring(0, MAX_ARTICLE_TEXT) : text;
    }

    private static List<String> toStringList(JsonNode node) {
        List<String> items = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                if (item.isTextual() && !item.asText().isBlank()) {
                    items.add(item.asText());
                }
            }
        }
        return items;
    }
}
