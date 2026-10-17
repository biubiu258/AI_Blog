package com.aiblog.controller;

import com.aiblog.common.ApiResponse;
import com.aiblog.dto.ai.AiGenerateRequest;
import com.aiblog.security.CurrentUser;
import com.aiblog.service.AiGatewayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 网关入口：仅鉴权转发，不落库、不碰文章表（契约 5.1 生成与发布的边界）。
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiGatewayService aiGatewayService;

    /**
     * AI 生成（SSE 流式）。流建立前失败返回统一 JSON（2001/2002 等），
     * 流建立后失败经 error 帧透传 Python 字符串码。
     */
    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generate(@Valid @RequestBody AiGenerateRequest request) {
        return aiGatewayService.generate(CurrentUser.get(), request);
    }
}
