package com.aiblog.controller;

import com.aiblog.common.ApiResponse;
import com.aiblog.dto.comment.CommentCreateRequest;
import com.aiblog.dto.comment.CommentItem;
import com.aiblog.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping("/articles/{articleId}/comments")
    public ApiResponse<List<CommentItem>> list(@PathVariable Long articleId) {
        return ApiResponse.ok(commentService.listByArticle(articleId));
    }

    @PostMapping("/articles/{articleId}/comments")
    public ApiResponse<Map<String, Object>> create(@PathVariable Long articleId,
                                                   @Valid @RequestBody CommentCreateRequest request) {
        return ApiResponse.ok(commentService.create(articleId, request.getContent(),
                request.getParentId()));
    }

    @DeleteMapping("/comments/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        commentService.delete(id);
        return ApiResponse.ok();
    }
}
