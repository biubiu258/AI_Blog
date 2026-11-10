package com.aiblog.controller;

import com.aiblog.common.ApiResponse;
import com.aiblog.service.CuratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 伯乐策展：推荐位公开可读；候选池与确认操作需伯乐或运营身份。
 */
@RestController
@RequestMapping("/api/curator")
@RequiredArgsConstructor
public class CuratorController {

    private final CuratorService curatorService;

    /** 当前登录者是否为在任伯乐 */
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        return ApiResponse.ok(curatorService.me());
    }

    /** 本周伯乐推荐（公开，含推荐理由） */
    @GetMapping("/slots")
    public ApiResponse<List<Map<String, Object>>> slots(
            @RequestParam(required = false) String week) {
        return ApiResponse.ok(curatorService.slots(week));
    }

    /** 候选池：AI 辅助度、完读率、违规记录排序 */
    @GetMapping("/candidates")
    public ApiResponse<List<Map<String, Object>>> candidates() {
        return ApiResponse.ok(curatorService.candidates());
    }

    /** 伯乐确认推荐 */
    @PostMapping("/slots")
    public ApiResponse<Map<String, Object>> recommend(@RequestBody RecommendRequest request) {
        return ApiResponse.ok(curatorService.recommend(request.articleId(), request.reason()));
    }

    @DeleteMapping("/slots/{id}")
    public ApiResponse<Void> removeSlot(@PathVariable Long id) {
        curatorService.removeSlot(id);
        return ApiResponse.ok();
    }

    public record RecommendRequest(Long articleId, String reason) {}
}
