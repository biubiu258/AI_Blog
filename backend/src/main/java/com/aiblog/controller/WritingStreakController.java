package com.aiblog.controller;

import com.aiblog.common.ApiResponse;
import com.aiblog.security.CurrentUser;
import com.aiblog.service.WritingStreakService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 日更打卡：日历、连续天数、成就与补签。
 */
@RestController
@RequestMapping("/api/streaks")
@RequiredArgsConstructor
public class WritingStreakController {

    private final WritingStreakService writingStreakService;

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        return ApiResponse.ok(writingStreakService.summary(CurrentUser.id()));
    }

    /** 使用补签卡补签昨天 */
    @PostMapping("/makeup")
    public ApiResponse<Map<String, Object>> makeup() {
        return ApiResponse.ok(writingStreakService.useMakeupCard());
    }
}
