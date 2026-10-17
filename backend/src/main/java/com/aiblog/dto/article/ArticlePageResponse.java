package com.aiblog.dto.article;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ArticlePageResponse {

    private long total;
    private List<ArticleItem> items;
}
