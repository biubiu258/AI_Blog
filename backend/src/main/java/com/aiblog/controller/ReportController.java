package com.aiblog.controller;

import com.aiblog.common.ApiResponse;
import com.aiblog.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 举报入口：文章与评论。管理端处理见 AdminContentController。
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody ReportRequest request) {
        return ApiResponse.ok(reportService.create(request.targetType(), request.targetId(),
                request.reason() == null ? "" : request.reason()));
    }

    public record ReportRequest(String targetType, Long targetId, String reason) {}
}
