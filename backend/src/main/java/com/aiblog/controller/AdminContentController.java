package com.aiblog.controller;

import com.aiblog.common.ApiResponse;
import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.dto.article.ArticlePageResponse;
import com.aiblog.entity.ContentLabelLog;
import com.aiblog.security.CurrentUser;
import com.aiblog.service.AdminLogService;
import com.aiblog.service.AiDisclosureService;
import com.aiblog.service.ArticleService;
import com.aiblog.service.CampaignService;
import com.aiblog.service.CommentService;
import com.aiblog.service.CuratorService;
import com.aiblog.service.HumanBadgeService;
import com.aiblog.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
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
    private final AiDisclosureService aiDisclosureService;
    private final HumanBadgeService humanBadgeService;
    private final CuratorService curatorService;
    private final CampaignService campaignService;

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

    /** AI 内容标识台账：按等级（none / assisted / generated）与标题筛选 */
    @GetMapping("/content-labels")
    public ApiResponse<Map<String, Object>> contentLabels(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(aiDisclosureService.adminPage(page, size, level, keyword));
    }

    /** 管理端修正标识等级，必须填写原因并写入操作日志 */
    @PutMapping("/content-labels/{articleId}")
    public ApiResponse<Map<String, Object>> updateContentLabel(
            @PathVariable Long articleId,
            @RequestBody UpdateLabelRequest request) {
        if (request.reason() == null || request.reason().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "修正标识需填写原因");
        }
        Map<String, Object> result = aiDisclosureService.adminUpdate(
                articleId, request.aiUsageLevel(), request.reason());
        adminLogService.record(CurrentUser.get(), "CONTENT_LABEL_UPDATE", "article:" + articleId,
                Map.of("level", request.aiUsageLevel(), "reason", request.reason()));
        return ApiResponse.ok(result);
    }

    /** 单篇文章的标识变更历史 */
    @GetMapping("/content-labels/{articleId}/logs")
    public ApiResponse<List<ContentLabelLog>> labelLogs(@PathVariable Long articleId) {
        return ApiResponse.ok(aiDisclosureService.logs(articleId));
    }

    public record UpdateLabelRequest(String aiUsageLevel, String reason) {}

    /** 人类创作徽章申请列表：按状态筛选 */
    @GetMapping("/badges")
    public ApiResponse<Map<String, Object>> badges(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(humanBadgeService.adminPage(page, size, status));
    }

    /** 审核徽章申请：passed / rejected，拒绝理由将向申请人展示 */
    @PutMapping("/badges/{id}")
    public ApiResponse<Map<String, Object>> reviewBadge(@PathVariable Long id,
                                                        @RequestBody BadgeReviewRequest request) {
        Map<String, Object> result = humanBadgeService.review(id, request.status(), request.note());
        adminLogService.record(CurrentUser.get(), "BADGE_REVIEW", "badge:" + id,
                Map.of("status", request.status()));
        return ApiResponse.ok(result);
    }

    public record BadgeReviewRequest(String status, String note) {}

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

    /** 伯乐名单 */
    @GetMapping("/curators")
    public ApiResponse<List<Map<String, Object>>> curators() {
        return ApiResponse.ok(curatorService.adminList());
    }

    /** 指定伯乐并设置任期 */
    @PostMapping("/curators")
    public ApiResponse<Map<String, Object>> addCurator(@RequestBody CuratorRequest request) {
        Map<String, Object> result = curatorService.addCurator(
                request.userId(), request.termStart(), request.termEnd());
        adminLogService.record(CurrentUser.get(), "CURATOR_ADD", "user:" + request.userId(),
                Map.of("termStart", String.valueOf(request.termStart())));
        return ApiResponse.ok(result);
    }

    @DeleteMapping("/curators/{id}")
    public ApiResponse<Void> removeCurator(@PathVariable Long id) {
        curatorService.removeCurator(id);
        adminLogService.record(CurrentUser.get(), "CURATOR_REMOVE", "curator:" + id, Map.of());
        return ApiResponse.ok();
    }

    /** 运营导出候选池：含 AI 辅助度与完读率 */
    @GetMapping("/curation/candidates")
    public ApiResponse<List<Map<String, Object>>> curationCandidates() {
        return ApiResponse.ok(curatorService.candidates());
    }

    public record CuratorRequest(Long userId, LocalDate termStart, LocalDate termEnd) {}

    /** 活动列表（含草稿） */
    @GetMapping("/campaigns")
    public ApiResponse<Map<String, Object>> campaigns(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(campaignService.adminPage(page, size, status));
    }

    @PostMapping("/campaigns")
    public ApiResponse<Map<String, Object>> createCampaign(
            @RequestBody CampaignService.CampaignRequest request) {
        return ApiResponse.ok(campaignService.create(request));
    }

    @PutMapping("/campaigns/{id}")
    public ApiResponse<Map<String, Object>> updateCampaign(
            @PathVariable Long id, @RequestBody CampaignService.CampaignRequest request) {
        return ApiResponse.ok(campaignService.update(id, request));
    }

    @DeleteMapping("/campaigns/{id}")
    public ApiResponse<Void> deleteCampaign(@PathVariable Long id) {
        campaignService.delete(id);
        return ApiResponse.ok();
    }

    /** 上线活动并通知全站作者 */
    @PostMapping("/campaigns/{id}/publish")
    public ApiResponse<Map<String, Object>> publishCampaign(@PathVariable Long id) {
        Map<String, Object> result = campaignService.publish(id);
        adminLogService.record(CurrentUser.get(), "CAMPAIGN_PUBLISH", "campaign:" + id, Map.of());
        return ApiResponse.ok(result);
    }

    private static String join(List<Long> ids) {
        return ids == null ? "" : ids.stream().map(String::valueOf)
                .reduce((a, b) -> a + "," + b).orElse("");
    }
}
