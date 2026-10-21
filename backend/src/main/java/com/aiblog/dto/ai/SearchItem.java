package com.aiblog.dto.ai;

import lombok.Data;

/**
 * 搜索结果条目：语义命中带 score；关键词命中 score 为 null。
 * matchType = semantic / keyword；status = published / draft / private。
 */
@Data
public class SearchItem {

    private Long id;
    private String title;
    private String snippet;
    private Double score;
    private String status;
    private String matchType;

    public static SearchItem semantic(Long id, String title, String snippet, Double score, String status) {
        SearchItem item = new SearchItem();
        item.id = id;
        item.title = title;
        item.snippet = snippet;
        item.score = score;
        item.status = status;
        item.matchType = "semantic";
        return item;
    }

    public static SearchItem keyword(Long id, String title, String snippet, String status) {
        SearchItem item = new SearchItem();
        item.id = id;
        item.title = title;
        item.snippet = snippet;
        item.score = null;
        item.status = status;
        item.matchType = "keyword";
        return item;
    }
}
