package com.aiblog.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** 语义搜索结果（契约 5.2：POST /api/ai/search 的 data）。 */
@Data
@AllArgsConstructor
public class AiSearchResponse {

    /** true = Python 不可用，已降级为关键词检索 */
    private boolean degraded;

    private List<SearchItem> items;
}
