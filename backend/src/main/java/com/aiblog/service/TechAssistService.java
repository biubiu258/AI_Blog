package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.config.AiProperties;
import com.aiblog.dto.tech.TechChunkHit;
import com.aiblog.dto.tech.TechWordingRequest;
import com.aiblog.mapper.TechDocChunkMapper;
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
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 技术写作辅助：术语规则 + 混合检索证据 + tech-wording 技能建议。
 * 任何一侧失败都只降级，不阻断写作；建议必须可追溯到规则或证据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TechAssistService {

    private static final int CANDIDATE_LIMIT = 20;
    private static final int TOP_K = 6;
    private static final int RRF_K = 60;
    private static final int MAX_AI_ISSUES = 8;
    private static final int MAX_REPLACEMENT = 200;
    private static final int MAX_SNIPPET = 200;
    private static final int MAX_FULLTEXT_QUERY = 200;
    private static final int WINDOW_SECONDS = 60;
    private static final int MAX_CALLS_PER_WINDOW = 10;
    private static final Set<String> ISSUE_TYPES =
            Set.of("terminology", "fact", "collocation", "clarity");
    private static final Set<String> SEVERITIES = Set.of("high", "mid", "low");
    private static final String DEGRADED_MESSAGE = "依据检索暂不可用，仅展示术语规则";

    private final TechTermService termService;
    private final TechDocChunkMapper chunkMapper;
    private final WebClient aiWebClient;
    private final AiProperties properties;
    private final AiAuditService auditService;
    private final ObjectMapper objectMapper;

    /** 同一用户 60 秒滑动窗口 */
    private final Map<Long, Deque<Long>> windows = new ConcurrentHashMap<>();

    public Map<String, Object> wording(Long userId, TechWordingRequest request) {
        checkRate(userId);
        String text = request.getText().trim();
        List<Map<String, Object>> ruleIssues = termService.check(text);
        List<Evidence> evidence = retrieveEvidence(text);

        String traceId = UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("text", text);
        snapshot.put("section_path", request.getSectionPath());
        snapshot.put("evidence", evidence.stream().map(Evidence::chunkId).toList());
        snapshot.put("rule_issues", ruleIssues.size());
        Long auditId = auditService.begin(traceId, userId, request.getArticleId(),
                "tech_assist", "wording", snapshot);
        long startedAt = System.nanoTime();

        boolean degraded = false;
        String message = null;
        List<Map<String, Object>> aiIssues = List.of();
        try {
            String body = callPythonWording(traceId, userId, text, request.getSectionPath(), evidence);
            JsonNode root = objectMapper.readTree(body);
            aiIssues = validateIssues(text, root.path("issues"), evidence);
            JsonNode usage = root.path("usage");
            auditService.finish(auditId, AiAuditService.AiResult.success(
                    body, "python",
                    usage.path("prompt_tokens").asInt(0),
                    usage.path("completion_tokens").asInt(0),
                    elapsedMs(startedAt),
                    textOrNull(root, "model"),
                    textOrNull(root, "prompt_version")));
        } catch (Exception e) {
            degraded = true;
            message = DEGRADED_MESSAGE;
            log.warn("技术措辞建议降级 traceId={}: {}", traceId, e.getMessage());
            auditService.finish(auditId, AiAuditService.AiResult.failed(
                    null, "python", "TECH_ASSIST_ERROR", e.getMessage(), elapsedMs(startedAt)));
        }

        List<Map<String, Object>> merged = merge(ruleIssues, aiIssues);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("issues", merged);
        response.put("degraded", degraded);
        response.put("traceId", traceId);
        if (message != null) {
            response.put("message", message);
        }
        return response;
    }

    // ------------------------------------------------------------------ 检索

    /** 向量 top20 + MySQL 全文 top20，RRF 融合后取 top6。 */
    List<Evidence> retrieveEvidence(String text) {
        List<Evidence> vectorHits = List.of();
        if (properties.isTechVectorEnabled()) {
            try {
                vectorHits = callPythonRetrieve(text);
            } catch (Exception e) {
                log.warn("技术向量检索失败，改用全文兜底: {}", e.getMessage());
            }
        }
        List<Evidence> fulltextHits = List.of();
        try {
            fulltextHits = mapFulltext(
                    chunkMapper.searchFulltext(buildFulltextQuery(text), CANDIDATE_LIMIT));
        } catch (Exception e) {
            log.warn("技术全文检索失败: {}", e.getMessage());
        }
        return mergeRrf(vectorHits, fulltextHits, TOP_K);
    }

    /**
     * RRF 融合：score = Σ 1/(60 + rank)，按 docId:chunkIndex 去重后降序取 topK。
     */
    static List<Evidence> mergeRrf(List<Evidence> vectorHits, List<Evidence> fulltextHits, int topK) {
        Map<String, Double> scores = new HashMap<>();
        Map<String, Evidence> entries = new LinkedHashMap<>();
        accumulate(vectorHits, scores, entries);
        accumulate(fulltextHits, scores, entries);
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> entries.get(entry.getKey()))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static void accumulate(List<Evidence> hits, Map<String, Double> scores,
                                   Map<String, Evidence> entries) {
        int rank = 0;
        for (Evidence hit : hits) {
            if (hit == null) {
                continue;
            }
            String key = hit.docId() + ":" + hit.chunkIndex();
            scores.merge(key, 1.0 / (RRF_K + rank), Double::sum);
            entries.merge(key, hit, (oldValue, newValue) -> enrich(oldValue, newValue));
            rank++;
        }
    }

    /** 两侧命中同一块时补齐缺失字段：优先保留已有非空值。 */
    private static Evidence enrich(Evidence a, Evidence b) {
        return new Evidence(
                firstText(a.chunkId(), b.chunkId()),
                firstText(a.docId(), b.docId()),
                a.chunkIndex() == null ? b.chunkIndex() : a.chunkIndex(),
                firstText(a.title(), b.title()),
                firstText(a.sourceName(), b.sourceName()),
                firstText(a.version(), b.version()),
                firstText(a.sectionPath(), b.sectionPath()),
                a.pageNo() == null ? b.pageNo() : a.pageNo(),
                firstText(a.snippet(), b.snippet()));
    }

    private static String firstText(String a, String b) {
        return StringUtils.hasText(a) ? a : b;
    }

    private List<Evidence> callPythonRetrieve(String text) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", text);
        payload.put("top_k", CANDIDATE_LIMIT);
        String body = aiWebClient.post()
                .uri("/v1/tech/retrieve")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), response ->
                        response.bodyToMono(String.class).defaultIfEmpty("")
                                .flatMap(raw -> Mono.error(new IllegalStateException(
                                        "向量检索返回 " + response.statusCode())))
                )
                .bodyToMono(String.class)
                .block(Duration.ofMillis(properties.getTechAssistTimeoutMs()));
        if (!StringUtils.hasText(body)) {
            return List.of();
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("向量检索响应格式异常", e);
        }
        JsonNode hits = root.path("hits");
        if (!hits.isArray()) {
            return List.of();
        }
        List<Evidence> list = new ArrayList<>();
        for (JsonNode hit : hits) {
            String chunkId = hit.path("chunk_id").asText("");
            String docId = hit.path("doc_id").asText("");
            Integer chunkIndex = parseChunkIndex(chunkId);
            list.add(new Evidence(chunkId, docId, chunkIndex,
                    hit.path("title").asText(""),
                    hit.path("source_name").asText(""),
                    hit.path("version").asText(""),
                    textOrNull(hit, "section_path"),
                    hit.path("page_no").isMissingNode() || hit.path("page_no").isNull()
                            ? null : hit.path("page_no").asInt(),
                    hit.path("snippet").asText("")));
        }
        return list;
    }

    private List<Evidence> mapFulltext(List<TechChunkHit> hits) {
        List<Evidence> list = new ArrayList<>();
        for (TechChunkHit hit : hits) {
            String chunkId = hit.getDocId() + ":" + hit.getChunkIndex();
            list.add(new Evidence(chunkId, String.valueOf(hit.getDocId()), hit.getChunkIndex(),
                    hit.getTitle(), hit.getSourceName(), hit.getVersion(),
                    hit.getSectionPath(), hit.getPageNo(),
                    snippet(hit.getContent())));
        }
        return list;
    }

    private static Integer parseChunkIndex(String chunkId) {
        if (!StringUtils.hasText(chunkId) || !chunkId.contains(":")) {
            return null;
        }
        try {
            return Integer.parseInt(chunkId.substring(chunkId.lastIndexOf(':') + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String buildFulltextQuery(String text) {
        String query = text.trim().replaceAll("[\\r\\n\\t]+", " ");
        return query.length() <= MAX_FULLTEXT_QUERY ? query : query.substring(0, MAX_FULLTEXT_QUERY);
    }

    private static String snippet(String content) {
        if (content == null) {
            return "";
        }
        String cleaned = content.replaceAll("[\\r\\n\\t]+", " ").trim();
        return cleaned.length() <= MAX_SNIPPET ? cleaned : cleaned.substring(0, MAX_SNIPPET);
    }

    // ------------------------------------------------------------------ 建议

    private String callPythonWording(String traceId, Long userId, String text,
                                     String sectionPath, List<Evidence> evidence) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("trace_id", traceId);
        payload.put("user_id", userId);
        payload.put("text", text);
        payload.put("section_path", sectionPath);
        payload.put("evidence", evidence.stream().map(TechAssistService::evidencePayload).toList());
        String body = aiWebClient.post()
                .uri("/v1/tech/wording")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), response ->
                        response.bodyToMono(String.class).defaultIfEmpty("")
                                .flatMap(raw -> Mono.error(new IllegalStateException(
                                        "措辞建议返回 " + response.statusCode() + ": " + raw)))
                )
                .bodyToMono(String.class)
                .block(Duration.ofMillis(properties.getTechAssistTimeoutMs()));
        if (!StringUtils.hasText(body)) {
            throw new IllegalStateException("措辞建议结果为空");
        }
        return body;
    }

    private static Map<String, Object> evidencePayload(Evidence evidence) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chunk_id", evidence.chunkId());
        payload.put("doc_id", evidence.docId());
        payload.put("title", evidence.title());
        payload.put("source_name", evidence.sourceName());
        payload.put("version", evidence.version());
        payload.put("section_path", evidence.sectionPath());
        payload.put("page_no", evidence.pageNo());
        payload.put("snippet", evidence.snippet());
        return payload;
    }

    /**
     * 服务端强制校验：quote 唯一命中、fact 必须有本次证据、替换长度受限。
     */
    List<Map<String, Object>> validateIssues(String text, JsonNode issuesNode,
                                             List<Evidence> evidenceList) {
        if (!issuesNode.isArray()) {
            return List.of();
        }
        Map<String, Evidence> byChunk = new HashMap<>();
        for (Evidence evidence : evidenceList) {
            byChunk.put(evidence.chunkId(), evidence);
        }
        Set<String> quotes = new HashSet<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode node : issuesNode) {
            if (result.size() >= MAX_AI_ISSUES) {
                break;
            }
            String type = node.path("type").asText("");
            if (!ISSUE_TYPES.contains(type)) {
                continue;
            }
            String quote = node.path("quote").asText("");
            if (!StringUtils.hasText(quote) || countOccurrences(text, quote) != 1
                    || !quotes.add(quote)) {
                continue;
            }
            String replacement = textOrNull(node, "replacement");
            if (replacement != null && replacement.length() > MAX_REPLACEMENT) {
                continue;
            }
            String evidenceChunkId = textOrNull(node, "evidence_chunk_id");
            Evidence evidence = evidenceChunkId == null ? null : byChunk.get(evidenceChunkId);
            if ("fact".equals(type) && (evidenceChunkId == null || evidence == null)) {
                continue;
            }
            String severity = node.path("severity").asText("mid");
            if (!SEVERITIES.contains(severity)) {
                severity = "mid";
            }
            Map<String, Object> issue = new LinkedHashMap<>();
            issue.put("id", node.path("id").asText("ai-" + result.size()));
            issue.put("type", type);
            issue.put("quote", quote);
            issue.put("replacement", replacement);
            issue.put("reason", node.path("reason").asText(""));
            issue.put("severity", severity);
            issue.put("evidenceChunkId", evidence == null ? null : evidence.chunkId());
            issue.put("evidence", evidence == null ? null : evidenceMap(evidence));
            result.add(issue);
        }
        return result;
    }

    private static Map<String, Object> evidenceMap(Evidence evidence) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("chunkId", evidence.chunkId());
        map.put("docId", evidence.docId());
        map.put("title", evidence.title());
        map.put("sourceName", evidence.sourceName());
        map.put("version", evidence.version());
        map.put("sectionPath", evidence.sectionPath());
        map.put("pageNo", evidence.pageNo());
        map.put("snippet", evidence.snippet());
        return map;
    }

    /** 规则项在前，AI 项在后；quote 重复时保留规则项。 */
    private static List<Map<String, Object>> merge(List<Map<String, Object>> rules,
                                                   List<Map<String, Object>> aiIssues) {
        Set<String> quotes = new HashSet<>();
        List<Map<String, Object>> merged = new ArrayList<>();
        for (Map<String, Object> issue : rules) {
            quotes.add(String.valueOf(issue.get("quote")));
            merged.add(issue);
        }
        for (Map<String, Object> issue : aiIssues) {
            if (quotes.add(String.valueOf(issue.get("quote")))) {
                merged.add(issue);
            }
        }
        return merged;
    }

    private static int countOccurrences(String text, String quote) {
        int count = 0;
        int index = text.indexOf(quote);
        while (index >= 0) {
            count++;
            index = text.indexOf(quote, index + quote.length());
        }
        return count;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText("").isBlank()
                ? null : value.asText();
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    // ------------------------------------------------------------------ 限流

    private void checkRate(Long userId) {
        long now = Instant.now().getEpochSecond();
        Deque<Long> window = windows.computeIfAbsent(userId, key -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() >= WINDOW_SECONDS) {
                window.pollFirst();
            }
            if (window.size() >= MAX_CALLS_PER_WINDOW) {
                throw new BusinessException(ErrorCode.TOO_MANY_ATTEMPTS,
                        "1 分钟内最多请求 10 次，请稍后再试");
            }
            window.addLast(now);
        }
    }

    /** 检索证据：块字段 + 来源文档字段，snippet 已裁剪。 */
    record Evidence(String chunkId, String docId, Integer chunkIndex, String title,
                    String sourceName, String version, String sectionPath,
                    Integer pageNo, String snippet) {
    }
}
