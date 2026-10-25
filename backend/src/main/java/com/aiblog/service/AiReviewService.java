package com.aiblog.service;

import com.aiblog.ai.PythonErrorTranslator;
import com.aiblog.ai.PythonResponseException;
import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.config.AiProperties;
import com.aiblog.dto.ai.AiReviewRequest;
import com.aiblog.dto.ai.AiReviewResponse;
import com.aiblog.security.LoginUser;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 全文审阅网关：转发 Python /v1/review，返回结构化局部建议。
 * 非流式调用，独立读超时；返回内容全部写入 ai_output 审计。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiReviewService {

    private final WebClient aiWebClient;
    private final AiProperties properties;
    private final PythonErrorTranslator errorTranslator;
    private final ObjectMapper objectMapper;
    private final AiAuditService auditService;

    public AiReviewResponse review(LoginUser user, AiReviewRequest request) {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("trace_id", traceId);
        payload.put("user_id", user.getId());
        payload.put("title", request.getTitle());
        payload.put("content", request.getContent());
        payload.put("article_type", request.getArticleType());
        payload.put("style", request.getStyle());
        Long auditId = auditService.begin(traceId, user.getId(), null, "review", null, payload);
        long startedAt = System.nanoTime();

        String body;
        try {
            body = aiWebClient.post()
                    .uri("/v1/review")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), response ->
                            response.bodyToMono(String.class).defaultIfEmpty("")
                                    .flatMap(raw -> Mono.error(
                                            new PythonResponseException(response.statusCode(), raw))))
                    .bodyToMono(String.class)
                    .block(Duration.ofMillis(properties.getReviewReadTimeoutMs()));
        } catch (PythonResponseException e) {
            BusinessException translated = errorTranslator.translate(e, "审阅超时，请重试");
            failAudit(auditId, translated, startedAt);
            throw translated;
        } catch (IllegalStateException e) {
            log.warn("等待 Python 审阅超时: {}", e.getMessage());
            BusinessException translated = new BusinessException(ErrorCode.AI_TIMEOUT, "审阅超时，请重试");
            failAudit(auditId, translated, startedAt);
            throw translated;
        } catch (Exception e) {
            log.warn("调用 Python 审阅失败: {}", e.getMessage());
            BusinessException translated = new BusinessException(
                    ErrorCode.AI_SERVICE_ERROR, "AI 服务暂时不可用，请稍后重试");
            failAudit(auditId, translated, startedAt);
            throw translated;
        }

        if (!StringUtils.hasText(body)) {
            BusinessException translated = new BusinessException(
                    ErrorCode.AI_EMPTY_OUTPUT, "审阅结果为空，请重试");
            failAudit(auditId, translated, startedAt);
            throw translated;
        }
        AiReviewResponse response;
        try {
            response = objectMapper.readValue(body, AiReviewResponse.class);
        } catch (Exception e) {
            log.warn("审阅响应解析失败: {}", e.getMessage());
            BusinessException translated = new BusinessException(
                    ErrorCode.AI_SERVICE_ERROR, "AI 服务返回格式异常，请稍后重试");
            failAudit(auditId, translated, startedAt);
            throw translated;
        }
        response.setTraceId(traceId);
        // 审阅返回的完整 JSON（建议 + usage）全部留存
        JsonNode node = readTree(body);
        Map<String, Integer> usage = response.getUsage();
        int tokensIn = usage == null ? 0 : valueOf(usage, "prompt_tokens");
        int tokensOut = usage == null ? 0 : valueOf(usage, "completion_tokens");
        auditService.finish(auditId, AiAuditService.AiResult.success(body, "python",
                tokensIn, tokensOut, elapsedMs(startedAt),
                textOrNull(node, "model"), textOrNull(node, "prompt_version")));
        return response;
    }

    private void failAudit(Long auditId, BusinessException error, long startedAt) {
        auditService.finish(auditId, AiAuditService.AiResult.failed(
                null, "python", error.getErrorCode().name(), error.getMessage(),
                elapsedMs(startedAt)));
    }

    private JsonNode readTree(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private static int valueOf(Map<String, Integer> usage, String key) {
        Integer value = usage.get(key);
        return value == null ? 0 : value;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText("").isBlank()
                ? null : value.asText();
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
