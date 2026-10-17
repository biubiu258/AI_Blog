package com.aiblog.dto.category;

import com.aiblog.entity.Category;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CategoryItem {

    private Long id;
    private String name;
    private String description;
    /** 分类下文章数（含草稿，供管理端参考） */
    private long articleCount;
    private LocalDateTime createdAt;

    public static CategoryItem of(Category category, long articleCount) {
        CategoryItem item = new CategoryItem();
        item.setId(category.getId());
        item.setName(category.getName());
        item.setDescription(category.getDescription());
        item.setArticleCount(articleCount);
        item.setCreatedAt(category.getCreatedAt());
        return item;
    }
}
