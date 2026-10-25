package com.aiblog.controller;

import com.aiblog.common.ApiResponse;
import com.aiblog.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 作者数据面板（公开维度 + 本人可见的草稿维度）。
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/author/{id}")
    public ApiResponse<Map<String, Object>> author(@PathVariable Long id) {
        return ApiResponse.ok(statsService.authorStats(id));
    }
}
