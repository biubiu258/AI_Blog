package com.aiblog.controller;

import com.aiblog.common.ApiResponse;
import com.aiblog.entity.Tag;
import com.aiblog.dto.article.ArticlePageResponse;
import com.aiblog.service.ArticleService;
import com.aiblog.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 标签：全量列表与按标签筛选文章。
 */
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;
    private final ArticleService articleService;

    @GetMapping
    public ApiResponse<List<Tag>> list() {
        return ApiResponse.ok(tagService.list());
    }

    @GetMapping("/{id}/articles")
    public ApiResponse<ArticlePageResponse> articles(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(articleService.pagePublished(page, size, null, null, id));
    }
}
