package com.aiblog.controller;

import com.aiblog.common.ApiResponse;
import com.aiblog.entity.AiOutput;
import com.aiblog.security.CurrentUser;
import com.aiblog.service.AdminLogService;
import com.aiblog.service.AiAuditService;
import com.aiblog.service.AiUsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端 AI 治理：调用量与采纳率，以及 AI 输出内容的审计与人工审核。
 */
@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAiController {

    private final AiUsageService aiUsageService;
    private final AiAuditService aiAuditService;
    private final AdminLogService adminLogService;

    @GetMapping("/usage")
    public ApiResponse<Map<String, Object>> usage(@RequestParam(defaultValue = "14") int days) {
        return ApiResponse.ok(aiUsageService.stats(days));
    }

    /** AI 输出列表：可按动作、终态、审核状态、用户与关键词过滤 */
    @GetMapping("/outputs")
    public ApiResponse<Map<String, Object>> outputs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long userId) {
        return ApiResponse.ok(aiAuditService.page(
                page, size, action, status, reviewStatus, keyword, userId));
    }

    @GetMapping("/outputs/pending-count")
    public ApiResponse<Map<String, Object>> pendingCount() {
        return ApiResponse.ok(Map.of("count", aiAuditService.pendingCount()));
    }

    /** 输出详情：完整返回内容、请求快照、追踪号与用量 */
    @GetMapping("/outputs/{id}")
    public ApiResponse<AiOutput> output(@PathVariable Long id) {
        return ApiResponse.ok(aiAuditService.detail(id));
    }

    /** 人工审核：passed 通过 / flagged 标记问题 / pending 重置待审 */
    @PutMapping("/outputs/{id}/review")
    public ApiResponse<AiOutput> review(@PathVariable Long id,
                                        @RequestBody ReviewRequest request) {
        AiOutput output = aiAuditService.review(
                id, CurrentUser.id(), request.status(), request.note());
        adminLogService.record(CurrentUser.get(), "AI_OUTPUT_REVIEW", "ai_output:" + id,
                Map.of("status", request.status(), "note", request.note() == null ? "" : request.note()));
        return ApiResponse.ok(output);
    }

    public record ReviewRequest(String status, String note) {
    }
}
