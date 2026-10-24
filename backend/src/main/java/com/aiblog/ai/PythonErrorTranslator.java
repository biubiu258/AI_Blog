package com.aiblog.ai;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Python 错误体 {"error":{code,message}} -> Java 业务码。
 * UNAUTHORIZED 属内部配置故障不外传；未知错误统一按 AI 服务异常处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PythonErrorTranslator {

    private final ObjectMapper objectMapper;

    public BusinessException translate(PythonResponseException e, String timeoutMessage) {
        String code = null;
        String message = null;
        try {
            JsonNode root = objectMapper.readTree(e.getBody() == null ? "" : e.getBody());
            JsonNode error = root.path("error");
            code = error.path("code").asText(null);
            message = error.path("message").asText(null);
        } catch (Exception ignored) {
            // 非 JSON 错误体，按通用错误处理
        }
        log.warn("Python 返回错误: http={}, code={}", e.getStatus().value(), code);
        return switch (code == null ? "" : code) {
            case "LLM_TIMEOUT" -> new BusinessException(ErrorCode.AI_TIMEOUT,
                    message == null ? timeoutMessage : message);
            case "EMPTY_OUTPUT" -> new BusinessException(ErrorCode.AI_EMPTY_OUTPUT,
                    message == null ? "生成结果为空，请重试" : message);
            case "INVALID_PARAMS" -> new BusinessException(ErrorCode.PARAM_ERROR,
                    message == null ? "AI 生成参数不合法" : message);
            default -> new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 服务异常，请稍后重试");
        };
    }
}
