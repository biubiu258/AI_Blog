package com.aiblog.controller;

import com.aiblog.common.ApiResponse;
import com.aiblog.dto.ai.AiSearchRequest;
import com.aiblog.dto.ai.AiSearchResponse;
import com.aiblog.service.SearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内语义搜索入口（契约 5.2：POST /api/ai/search，需登录）。
 * 与 AiController 分离：搜索会读文章表做可见性裁决，不属于「只管转发」的生成网关。
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiSearchController {

    private final SearchService searchService;

    @PostMapping("/search")
    public ApiResponse<AiSearchResponse> search(@Valid @RequestBody AiSearchRequest request) {
        return ApiResponse.ok(searchService.search(request.getQuery(), request.getTopK()));
    }
}
