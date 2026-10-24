package com.aiblog.service;

import com.aiblog.ai.PythonErrorTranslator;
import com.aiblog.ai.PythonResponseException;
import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.config.AiProperties;
import com.aiblog.dto.ai.AiReviewRequest;
import com.aiblog.dto.ai.AiReviewResponse;
import com.aiblog.security.LoginUser;
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

/**
 * 全文审阅网关：转发 Python /v1/review，返回结构化局部建议。
 * 非流式调用，独立读超时；不落库、不修改文章。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiReviewService {

    private final WebClient aiWebClient;
    private final AiProperties properties;
    private final PythonErrorTranslator errorTranslator;
    private final ObjectMapper objectMapper;

    public AiReviewResponse review(LoginUser user, AiReviewRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_id", user.getId());
        payload.put("title", request.getTitle());
        payload.put("content", request.getContent());
        payload.put("article_type", request.getArticleType());
        payload.put("style", request.getStyle());

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
            throw errorTranslator.translate(e, "审阅超时，请重试");
        } catch (IllegalStateException e) {
            log.warn("等待 Python 审阅超时: {}", e.getMessage());
            throw new BusinessException(ErrorCode.AI_TIMEOUT, "审阅超时，请重试");
        } catch (Exception e) {
            log.warn("调用 Python 审阅失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 服务暂时不可用，请稍后重试");
        }

        if (!StringUtils.hasText(body)) {
            throw new BusinessException(ErrorCode.AI_EMPTY_OUTPUT, "审阅结果为空，请重试");
        }
        try {
            return objectMapper.readValue(body, AiReviewResponse.class);
        } catch (Exception e) {
            log.warn("审阅响应解析失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 服务返回格式异常，请稍后重试");
        }
    }
}
