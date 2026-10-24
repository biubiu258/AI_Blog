package com.aiblog.controller;

import com.aiblog.common.ApiResponse;
import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.dto.article.ArticlePageResponse;
import com.aiblog.security.CurrentUser;
import com.aiblog.service.AdminLogService;
import com.aiblog.service.ArticleService;
import com.aiblog.service.CommentService;
import com.aiblog.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理端：全站内容批量管理（文章 / 评论）。
 * ADMIN 与 CONTENT_ADMIN 均可访问；用户管理与审计日志仅限 ADMIN。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','CONTENT_ADMIN')")
public class AdminContentController {

    private final ArticleService articleService;
    private final CommentService commentService;
    private final AdminLogService adminLogService;
    private final ReportService reportService;

    @GetMapping("/articles")
    public ApiResponse<ArticlePageResponse> articles(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(articleService.adminPage(page, size, status, keyword));
    }

    /** 批量下架：published -> draft */
    @PutMapping("/articles/batch-status")
    public ApiResponse<Map<String, Object>> batchUnpublish(@RequestBody BatchStatusRequest request) {
        if (!"draft".equals(request.status())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "仅支持批量下架为 draft");
        }
        int count = articleService.adminBatchUnpublish(request.ids());
        adminLogService.record(CurrentUser.get(), "ARTICLE_UNPUBLISH",
                "article:" + join(request.ids()), Map.of("count", count));
        return ApiResponse.ok(Map.of("count", count));
    }

    @DeleteMapping("/articles/batch")
    public ApiResponse<Map<String, Object>> batchDeleteArticles(@RequestParam List<Long> ids) {
        int count = articleService.adminBatchDelete(ids);
        adminLogService.record(CurrentUser.get(), "ARTICLE_DELETE",
                "article:" + join(ids), Map.of("count", count));
        return ApiResponse.ok(Map.of("count", count));
    }

    @GetMapping("/comments")
    public ApiResponse<Map<String, Object>> comments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(commentService.adminPage(page, size, keyword));
    }

    @DeleteMapping("/comments/batch")
    public ApiResponse<Map<String, Object>> batchDeleteComments(@RequestParam List<Long> ids) {
        int count = commentService.adminBatchDelete(ids);
        adminLogService.record(CurrentUser.get(), "COMMENT_DELETE",
                "comment:" + join(ids), Map.of("count", count));
        return ApiResponse.ok(Map.of("count", count));
    }

    public record BatchStatusRequest(List<Long> ids, String status) {}

    /** 举报列表：status 可传 pending / handled / dismissed */
    @GetMapping("/reports")
    public ApiResponse<Map<String, Object>> reports(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(reportService.adminPage(page, size, status));
    }

    @GetMapping("/reports/pending-count")
    public ApiResponse<Map<String, Object>> pendingReports() {
        return ApiResponse.ok(Map.of("count", reportService.pendingCount()));
    }

    /** action: dismiss 忽略 / delete 删除被举报内容 */
    @PutMapping("/reports/{id}/handle")
    public ApiResponse<Void> handleReport(@PathVariable Long id,
                                          @RequestBody HandleReportRequest request) {
        if (request.action() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "action 不能为空");
        }
        reportService.handle(id, request.action(), CurrentUser.get());
        adminLogService.record(CurrentUser.get(), "REPORT_HANDLE", "report:" + id,
                Map.of("action", request.action()));
        return ApiResponse.ok();
    }

    public record HandleReportRequest(String action) {}

    private static String join(List<Long> ids) {
        return ids == null ? "" : ids.stream().map(String::valueOf)
                .reduce((a, b) -> a + "," + b).orElse("");
    }
}
