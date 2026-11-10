package com.aiblog.controller;

import com.aiblog.common.ApiResponse;
import com.aiblog.service.HumanBadgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 人类创作徽章（用户侧）：申请与我的申请记录。
 */
@RestController
@RequestMapping("/api/badges")
@RequiredArgsConstructor
public class BadgeController {

    private final HumanBadgeService humanBadgeService;

    /** 提交徽章申请：需至少 3 篇声明为 none 的已发布文章 */
    @PostMapping("/apply")
    public ApiResponse<Map<String, Object>> apply() {
        return ApiResponse.ok(humanBadgeService.apply());
    }

    /** 我的申请记录（含拒绝理由） */
    @GetMapping("/mine")
    public ApiResponse<List<Map<String, Object>>> mine() {
        return ApiResponse.ok(humanBadgeService.mine());
    }
}
