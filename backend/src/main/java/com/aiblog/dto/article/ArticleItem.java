package com.aiblog.dto.article;

import com.aiblog.entity.Article;
import com.aiblog.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 列表项：不含正文大字段。
 */
@Data
public class ArticleItem {

    private Long id;
    private String title;
    private String summary;
    private Long authorId;
    private String authorNickname;
    private String authorAvatar;
    private Long categoryId;
    private String categoryName;
    private String status;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ArticleItem of(Article article, String categoryName, User author) {
        ArticleItem item = new ArticleItem();
        item.setId(article.getId());
        item.setTitle(article.getTitle());
        item.setSummary(article.getSummary());
        item.setAuthorId(article.getAuthorId());
        item.setCategoryId(article.getCategoryId());
        item.setCategoryName(categoryName);
        item.setStatus(article.getStatus());
        item.setPublishedAt(article.getPublishedAt());
        item.setCreatedAt(article.getCreatedAt());
        item.setUpdatedAt(article.getUpdatedAt());
        if (author != null) {
            item.setAuthorNickname(author.getNickname());
            item.setAuthorAvatar(author.getAvatarUrl());
        }
        return item;
    }
}
