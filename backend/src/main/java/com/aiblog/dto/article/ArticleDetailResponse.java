package com.aiblog.dto.article;

import com.aiblog.entity.Article;
import com.aiblog.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ArticleDetailResponse {

    private Long id;
    private String title;
    private String contentHtml;
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

    public static ArticleDetailResponse of(Article article, String categoryName, User author) {
        ArticleDetailResponse r = new ArticleDetailResponse();
        r.setId(article.getId());
        r.setTitle(article.getTitle());
        r.setContentHtml(article.getContentHtml());
        r.setSummary(article.getSummary());
        r.setAuthorId(article.getAuthorId());
        r.setCategoryId(article.getCategoryId());
        r.setCategoryName(categoryName);
        r.setStatus(article.getStatus());
        r.setPublishedAt(article.getPublishedAt());
        r.setCreatedAt(article.getCreatedAt());
        r.setUpdatedAt(article.getUpdatedAt());
        if (author != null) {
            r.setAuthorNickname(author.getNickname());
            r.setAuthorAvatar(author.getAvatarUrl());
        }
        return r;
    }
}
