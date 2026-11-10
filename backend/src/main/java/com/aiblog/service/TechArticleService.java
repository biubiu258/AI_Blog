package com.aiblog.service;

import com.aiblog.dto.article.ArticlePageResponse;
import com.aiblog.entity.Category;
import com.aiblog.mapper.CategoryMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 技术板块文章：按 category.code=tech 聚合已发布文章。
 */
@Service
@RequiredArgsConstructor
public class TechArticleService {

    public static final String TECH_CATEGORY_CODE = "tech";

    private final CategoryMapper categoryMapper;
    private final ArticleService articleService;

    public ArticlePageResponse page(int page, int size, String keyword) {
        Category tech = categoryMapper.selectOne(Wrappers.<Category>lambdaQuery()
                .eq(Category::getCode, TECH_CATEGORY_CODE)
                .last("LIMIT 1"));
        if (tech == null) {
            return new ArticlePageResponse(0, List.of());
        }
        return articleService.pagePublished(page, size, tech.getId(), keyword, null);
    }
}
