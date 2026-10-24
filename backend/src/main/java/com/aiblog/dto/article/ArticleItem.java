package com.aiblog.dto.article;

import com.aiblog.entity.Article;
import com.aiblog.entity.User;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

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
    private Integer viewCount;
    private List<String> tags;
    private LocalDateTime publishedAt;
    private LocalDateTime scheduledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ArticleItem of(Article article, String categoryName, User author,
                                 List<String> tags) {
        ArticleItem item = new ArticleItem();
        item.setId(article.getId());
        item.setTitle(article.getTitle());
        item.setSummary(article.getSummary());
        item.setAuthorId(article.getAuthorId());
        item.setCategoryId(article.getCategoryId());
        item.setCategoryName(categoryName);
        item.setStatus(article.getStatus());
        item.setViewCount(article.getViewCount() == null ? 0 : article.getViewCount());
        item.setTags(tags == null ? List.of() : tags);
        item.setPublishedAt(article.getPublishedAt());
        item.setScheduledAt(article.getScheduledAt());
        item.setCreatedAt(article.getCreatedAt());
        item.setUpdatedAt(article.getUpdatedAt());
        if (author != null) {
            item.setAuthorNickname(author.getNickname());
            item.setAuthorAvatar(author.getAvatarUrl());
        }
        return item;
    }
}
