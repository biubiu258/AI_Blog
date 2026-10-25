package com.aiblog.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 流式输出采集器：从 SSE 帧中还原 AI 返回内容与元数据，供审计落库。
 * 纯内存逻辑，不依赖 Spring，便于单测。
 */
@Slf4j
@RequiredArgsConstructor
public class AiStreamRecorder {

    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_CANCELED = "canceled";

    private final ObjectMapper objectMapper;

    private final StringBuilder deltas = new StringBuilder();
    private String doneContent;
    private String errorCode;
    private String errorMessage;
    private String model;
    private String promptVersion;
    private int tokensIn;
    private int tokensOut;
    private boolean done;

    /** 消费一帧（event 名称 + data 原文），解析失败不影响转发。 */
    public void accept(String event, String data) {
        if (data == null || data.isBlank()) {
            return;
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(data);
        } catch (Exception e) {
            log.debug("SSE 帧不是合法 JSON，跳过采集 event={}", event);
            return;
        }
        if ("delta".equals(event)) {
            String content = node.path("content").asText("");
            if (!content.isEmpty()) {
                deltas.append(content);
            }
        } else if ("done".equals(event)) {
            done = true;
            String content = node.path("content").asText("");
            if (!content.isEmpty()) {
                doneContent = content;
            }
            JsonNode usage = node.path("usage");
            tokensIn = usage.path("prompt_tokens").asInt(0);
            tokensOut = usage.path("completion_tokens").asInt(0);
            model = textOrNull(node, "model");
            promptVersion = textOrNull(node, "prompt_version");
        } else if ("error".equals(event)) {
            errorCode = textOrNull(node, "code");
            errorMessage = textOrNull(node, "message");
        }
    }

    /** 完整内容：done 帧的 content 为权威终稿，否则用 delta 累积。 */
    public String content() {
        if (doneContent != null) {
            return doneContent;
        }
        return deltas.toString();
    }

    public boolean done() {
        return done;
    }

    public boolean failed() {
        return errorCode != null || errorMessage != null;
    }

    /** done → success；error 帧 → failed；其余（断开/超时）→ canceled。 */
    public String status() {
        if (failed()) {
            return STATUS_FAILED;
        }
        return done ? STATUS_SUCCESS : STATUS_CANCELED;
    }

    public String errorCode() {
        return errorCode;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public String model() {
        return model;
    }

    public String promptVersion() {
        return promptVersion;
    }

    public int tokensIn() {
        return tokensIn;
    }

    public int tokensOut() {
        return tokensOut;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText("").isBlank()
                ? null : value.asText();
    }
}
