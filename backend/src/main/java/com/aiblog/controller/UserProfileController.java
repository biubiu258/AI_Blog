package com.aiblog.controller;

import com.aiblog.common.ApiResponse;
import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.dto.article.ArticlePageResponse;
import com.aiblog.dto.user.UserBrief;
import com.aiblog.security.CurrentUser;
import com.aiblog.service.ArticleService;
import com.aiblog.service.InteractionService;
import com.aiblog.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 公开用户主页：资料、文章、关注/粉丝。收藏仅本人可见。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserService userService;
    private final ArticleService articleService;
    private final InteractionService interactionService;

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> profile(@PathVariable Long id) {
        return ApiResponse.ok(userService.profile(id));
    }

    @PostMapping("/{id}/follow")
    public ApiResponse<Map<String, Object>> follow(@PathVariable Long id) {
        return ApiResponse.ok(interactionService.toggleFollow(id));
    }

    @GetMapping("/{id}/followers")
    public ApiResponse<List<UserBrief>> followers(@PathVariable Long id) {
        return ApiResponse.ok(userService.followers(id));
    }

    @GetMapping("/{id}/following")
    public ApiResponse<List<UserBrief>> following(@PathVariable Long id) {
        return ApiResponse.ok(userService.following(id));
    }

    @GetMapping("/{id}/articles")
    public ApiResponse<ArticlePageResponse> articles(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(articleService.pageByAuthor(id, page, size));
    }

    /** 收藏列表仅本人可查 */
    @GetMapping("/{id}/favorites")
    public ApiResponse<ArticlePageResponse> favorites(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (!Objects.equals(CurrentUser.id(), id)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅本人可查看收藏");
        }
        return ApiResponse.ok(articleService.pageFavorites(id, page, size));
    }
}
