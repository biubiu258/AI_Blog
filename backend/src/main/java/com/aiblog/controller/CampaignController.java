package com.aiblog.controller;

import com.aiblog.common.ApiResponse;
import com.aiblog.service.CampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 创作激励活动：读者侧只读进行中的活动。
 */
@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(campaignService.publicList());
    }
}
