package com.aiblog.controller;

import com.aiblog.common.ApiResponse;
import com.aiblog.dto.article.ArticleDetailResponse;
import com.aiblog.dto.article.ArticlePageResponse;
import com.aiblog.dto.article.ArticleSaveRequest;
import com.aiblog.entity.ArticleVersion;
import com.aiblog.security.CurrentUser;
import com.aiblog.service.AiDisclosureService;
import com.aiblog.service.AiTraceService;
import com.aiblog.service.ArticleService;
import com.aiblog.service.ArticleVersionService;
import com.aiblog.service.InteractionService;
import com.aiblog.service.StatsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;
    private final InteractionService interactionService;
    private final ArticleVersionService articleVersionService;
    private final StatsService statsService;
    private final AiDisclosureService aiDisclosureService;
    private final AiTraceService aiTraceService;

    /** 公开列表：仅已发布，支持分类、标签与关键词筛选 */
    @GetMapping
    public ApiResponse<ArticlePageResponse> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(articleService.pagePublished(page, size, categoryId, keyword, tagId));
    }

    /** 我的文章（含草稿），需登录 */
    @GetMapping("/mine")
    public ApiResponse<ArticlePageResponse> mine(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(articleService.pageMine(page, size, status));
    }

    /** 热门排行：week / month / all，阅读、点赞、收藏加权 */
    @GetMapping("/hot")
    public ApiResponse<List<Map<String, Object>>> hot(
            @RequestParam(defaultValue = "week") String range,
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(statsService.hotArticles(range, limit));
    }

    /** 详情：已发布公开可读，草稿仅作者/管理员（越权 1003） */
    @GetMapping("/{id}")
    public ApiResponse<ArticleDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(articleService.detail(id));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody ArticleSaveRequest request) {
        return ApiResponse.ok(articleService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id,
                                                   @Valid @RequestBody ArticleSaveRequest request) {
        return ApiResponse.ok(articleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        articleService.delete(id);
        return ApiResponse.ok();
    }

    /** 点赞/取消点赞（切换语义，需登录） */
    @PostMapping("/{id}/like")
    public ApiResponse<Map<String, Object>> like(@PathVariable Long id) {
        return ApiResponse.ok(interactionService.toggleLike(id));
    }

    /** 收藏/取消收藏（切换语义，需登录） */
    @PostMapping("/{id}/favorite")
    public ApiResponse<Map<String, Object>> favorite(@PathVariable Long id) {
        return ApiResponse.ok(interactionService.toggleFavorite(id));
    }

    /** 阅读计数：同一访客每天每篇只计一次，游客可调用 */
    @GetMapping("/{id}/view")
    public ApiResponse<Map<String, Object>> view(@PathVariable Long id,
                                                 HttpServletRequest request) {
        String viewerKey;
        try {
            viewerKey = "u:" + CurrentUser.id();
        } catch (RuntimeException e) {
            String forwarded = request.getHeader("X-Forwarded-For");
            viewerKey = "ip:" + (forwarded == null || forwarded.isBlank()
                    ? request.getRemoteAddr() : forwarded.split(",")[0].trim());
        }
        return ApiResponse.ok(articleService.recordView(id, viewerKey));
    }

    /** 完读上报：前端到达正文底部时调用，用于策展候选排序 */
    @PostMapping("/{id}/read-complete")
    public ApiResponse<Map<String, Object>> readComplete(@PathVariable Long id) {
        return ApiResponse.ok(articleService.recordComplete(id));
    }

    /** 版本列表（不含正文），仅作者或管理员 */
    @GetMapping("/{id}/versions")
    public ApiResponse<List<ArticleVersion>> versions(@PathVariable Long id) {
        return ApiResponse.ok(articleVersionService.list(id));
    }

    /** 版本详情（含正文），仅作者或管理员 */
    @GetMapping("/{id}/versions/{versionId}")
    public ApiResponse<ArticleVersion> version(@PathVariable Long id,
                                               @PathVariable Long versionId) {
        return ApiResponse.ok(articleVersionService.mustGetVersion(id, versionId));
    }

    /** 创作溯源：AI 参与时间线与辅助度评分（公开摘要，不含提示词原文） */
    @GetMapping("/{id}/trace")
    public ApiResponse<Map<String, Object>> trace(@PathVariable Long id) {
        return ApiResponse.ok(aiTraceService.trace(id));
    }

    /** AI 参与声明建议：按该文章的 AI 调用审计记录推断默认等级，仅作者/管理员 */
    @GetMapping("/{id}/ai-disclosure/suggest")
    public ApiResponse<Map<String, Object>> suggestDisclosure(@PathVariable Long id) {
        return ApiResponse.ok(articleService.suggestDisclosure(id));
    }

    /** 修改 AI 参与声明（none / assisted / generated），变更写入标识日志 */
    @PatchMapping("/{id}/ai-disclosure")
    public ApiResponse<Map<String, Object>> updateDisclosure(@PathVariable Long id,
                                                             @RequestBody DisclosureRequest request) {
        return ApiResponse.ok(aiDisclosureService.updateByAuthor(id, request.aiUsageLevel()));
    }

    /** 导出 Markdown：显式标识置于首尾，隐式标识写入文件元数据 */
    @GetMapping(value = "/{id}/export", produces = "text/markdown;charset=UTF-8")
    public ResponseEntity<String> export(@PathVariable Long id) {
        String markdown = articleService.exportMarkdown(id);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "markdown",
                        java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"article-" + id + ".md\"")
                .body(markdown);
    }

    public record DisclosureRequest(String aiUsageLevel) {}
}
