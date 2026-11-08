package com.aiblog.controller;

import com.aiblog.common.ApiResponse;
import com.aiblog.dto.article.ArticlePageResponse;
import com.aiblog.dto.tech.TechWordingRequest;
import com.aiblog.entity.TechTerm;
import com.aiblog.security.CurrentUser;
import com.aiblog.service.TechArticleService;
import com.aiblog.service.TechAssistService;
import com.aiblog.service.TechTermService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 技术专项读者/写作侧接口：术语表与术语检查。
 * 技术文章列表与 AI 建议接口在后续任务中追加。
 */
@RestController
@RequestMapping("/api/tech")
@RequiredArgsConstructor
public class TechController {

    private final TechTermService termService;
    private final TechArticleService articleService;
    private final TechAssistService assistService;

    /** 公开技术板块：只返回 category.code=tech 的已发布文章 */
    @GetMapping("/articles")
    public ApiResponse<ArticlePageResponse> articles(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(articleService.page(page, size, keyword));
    }

    /** 公开术语列表：只返回启用项，支持关键词与分类筛选 */
    @GetMapping("/terms")
    public ApiResponse<List<TechTerm>> terms(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category) {
        return ApiResponse.ok(termService.list(keyword, category, true));
    }

    /** 术语检查：需登录；只做规则匹配，不调用 LLM */
    @PostMapping("/terms/check")
    public ApiResponse<Map<String, Object>> check(@Valid @RequestBody TermCheckRequest request) {
        return ApiResponse.ok(Map.of("issues", termService.check(request.text())));
    }

    /** 术语与措辞建议：规则 + 检索证据 + AI，需登录且有频率限制 */
    @PostMapping("/assist/wording")
    public ApiResponse<Map<String, Object>> wording(@Valid @RequestBody TechWordingRequest request) {
        return ApiResponse.ok(assistService.wording(CurrentUser.id(), request));
    }

    public record TermCheckRequest(
            @NotBlank(message = "text 不能为空")
            @Size(max = 5000, message = "检查内容不能超过 5000 字") String text) {}
}
