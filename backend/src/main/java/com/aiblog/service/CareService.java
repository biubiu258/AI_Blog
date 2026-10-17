package com.aiblog.service;

import com.aiblog.config.CareProperties;
import com.aiblog.entity.CareLog;
import com.aiblog.mapper.CareLogMapper;
import com.aiblog.util.HtmlSanitizer;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 人文关怀：发布时情绪/危机检测（契约 5.8，POST /v1/care/check）。
 * fail-open：Python 超时/异常/响应异常一律按无风险放行——检测是增强能力，
 * 绝不能成为发布的单点故障。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CareService {

    public static final String LEVEL_NONE = "none";
    private static final Set<String> VALID_LEVELS = Set.of("none", "low", "mid", "high");

    private final WebClient aiWebClient;
    private final CareProperties properties;
    private final CareLogMapper careLogMapper;

    /** 检测结果：level 必为 none/low/mid/high；summary 为 Python 侧可选文案 */
    public record CareResult(String level, String summary) {
        public static CareResult none() {
            return new CareResult(LEVEL_NONE, null);
        }

        public boolean high() {
            return "high".equals(level);
        }

        public boolean present() {
            return !LEVEL_NONE.equals(level);
        }
    }

    /**
     * 发布时检测。仅依赖 level；summary 透传给前端做关怀呈现。
     */
    public CareResult check(Long userId, String title, String contentHtml) {
        // 演示开关：注入 mock-level 时跳过 Python 调用直接返回指定级别（联调/答辩用）
        String mock = properties.getMockLevel();
        if (mock != null && !mock.isBlank()) {
            String level = VALID_LEVELS.contains(mock) ? mock : LEVEL_NONE;
            log.info("关怀检测(mock): userId={}, level={}", userId, level);
            return new CareResult(level, null);
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("user_id", userId);
            body.put("title", title == null ? "" : title);
            body.put("content", HtmlSanitizer.toPlainText(contentHtml == null ? "" : contentHtml));
            JsonNode node = aiWebClient.post()
                    .uri("/v1/care/check")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .block();
            String level = node == null ? null : node.path("level").asText(null);
            if (level == null || !VALID_LEVELS.contains(level)) {
                log.warn("关怀检测响应异常，按无风险放行: level={}", level);
                return CareResult.none();
            }
            log.info("关怀检测: userId={}, level={}", userId, level);
            return new CareResult(level, node.path("summary").asText(null));
        } catch (Exception e) {
            log.warn("关怀检测不可用，按无风险放行(fail-open): {}", e.getMessage());
            return CareResult.none();
        }
    }

    /** 审计落库（不含原文），none 不记；失败不影响主业务 */
    public void record(Long userId, Long articleId, String level) {
        if (LEVEL_NONE.equals(level) || userId == null) {
            return;
        }
        try {
            CareLog careLog = new CareLog();
            careLog.setUserId(userId);
            careLog.setArticleId(articleId);
            careLog.setLevel(level);
            careLog.setCreatedAt(LocalDateTime.now());
            careLogMapper.insert(careLog);
        } catch (Exception e) {
            log.warn("care_log 落库失败: {}", e.getMessage());
        }
    }
}
