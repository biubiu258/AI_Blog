package com.aiblog.dto.ai;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 全文审阅响应：建议列表最多 6 条。
 */
@Data
public class AiReviewResponse {

    private List<AiSuggestion> suggestions = List.of();

    private Map<String, Integer> usage;
}
