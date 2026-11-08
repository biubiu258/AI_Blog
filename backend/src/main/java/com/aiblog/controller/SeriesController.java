package com.aiblog.controller;

import com.aiblog.common.ApiResponse;
import com.aiblog.service.SeriesService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 专题/连载：作者维护目录，读者浏览与订阅。
 */
@RestController
@RequestMapping("/api/series")
@RequiredArgsConstructor
public class SeriesController {

    private final SeriesService seriesService;

    @GetMapping("/mine")
    public ApiResponse<List<Map<String, Object>>> mine() {
        return ApiResponse.ok(seriesService.mine());
    }

    @GetMapping("/subscribed")
    public ApiResponse<List<Map<String, Object>>> subscribed() {
        return ApiResponse.ok(seriesService.subscribed());
    }

    /** 连载详情（公开，含目录与上下篇） */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(seriesService.detail(id));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody SeriesRequest request) {
        return ApiResponse.ok(seriesService.create(request.title(), request.description()));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id,
                                                   @RequestBody SeriesRequest request) {
        return ApiResponse.ok(seriesService.update(id, request.title(), request.description(),
                request.status()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        seriesService.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/articles")
    public ApiResponse<Map<String, Object>> addArticle(@PathVariable Long id,
                                                      @RequestBody SeriesArticleRequest request) {
        return ApiResponse.ok(seriesService.addArticle(id, request.articleId(), request.orderNo()));
    }

    @DeleteMapping("/{id}/articles/{articleId}")
    public ApiResponse<Void> removeArticle(@PathVariable Long id, @PathVariable Long articleId) {
        seriesService.removeArticle(id, articleId);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/order")
    public ApiResponse<Void> reorder(@PathVariable Long id, @RequestBody OrderRequest request) {
        seriesService.reorder(id, request.articleIds());
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/subscribe")
    public ApiResponse<Map<String, Object>> subscribe(@PathVariable Long id) {
        return ApiResponse.ok(seriesService.subscribe(id));
    }

    @DeleteMapping("/{id}/subscribe")
    public ApiResponse<Map<String, Object>> unsubscribe(@PathVariable Long id) {
        return ApiResponse.ok(seriesService.unsubscribe(id));
    }

    public record SeriesRequest(String title, String description, String status) {}

    public record SeriesArticleRequest(Long articleId, Integer orderNo) {}

    public record OrderRequest(List<Long> articleIds) {}
}
