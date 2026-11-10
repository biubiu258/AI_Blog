package com.aiblog.dto.article;

import com.aiblog.entity.Article;
import com.aiblog.entity.User;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class ArticleDetailResponse {

    private Long id;
    private String title;
    private String contentHtml;
    private String summary;
    private Long authorId;
    private String authorNickname;
    private String authorAvatar;
    private String authorBio;
    private Long categoryId;
    private String categoryName;
    private String status;
    private Integer viewCount;
    private List<String> tags;
    private LocalDateTime publishedAt;
    private LocalDateTime scheduledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** AI 参与程度：none / assisted / generated */
    private String aiUsageLevel;
    /** 作者主动声明时间 */
    private LocalDateTime aiDeclaredAt;
    /** 平台内容编号，用于显式标识展示 */
    private String aiContentId;
    /** 人类创作徽章（迭代 2 启用） */
    private Integer humanBadge;

    /** 互动计数与当前用户状态 */
    private Long likeCount;
    private Long favoriteCount;
    private Long commentCount;
    private Long followerCount;
    private Boolean liked;
    private Boolean favorited;
    private Boolean following;

    /** 所属连载上下文（目录序号与上下篇），未加入连载时为 null */
    private Map<String, Object> series;

    public static ArticleDetailResponse of(Article article, String categoryName, User author,
                                           List<String> tags) {
        ArticleDetailResponse r = new ArticleDetailResponse();
        r.setId(article.getId());
        r.setTitle(article.getTitle());
        r.setContentHtml(article.getContentHtml());
        r.setSummary(article.getSummary());
        r.setAuthorId(article.getAuthorId());
        r.setCategoryId(article.getCategoryId());
        r.setCategoryName(categoryName);
        r.setStatus(article.getStatus());
        r.setViewCount(article.getViewCount() == null ? 0 : article.getViewCount());
        r.setTags(tags == null ? List.of() : tags);
        r.setPublishedAt(article.getPublishedAt());
        r.setScheduledAt(article.getScheduledAt());
        r.setCreatedAt(article.getCreatedAt());
        r.setUpdatedAt(article.getUpdatedAt());
        r.setAiUsageLevel(article.getAiUsageLevel() == null ? "none" : article.getAiUsageLevel());
        r.setAiDeclaredAt(article.getAiDeclaredAt());
        r.setAiContentId("AIB-ARTICLE-" + article.getId());
        r.setHumanBadge(article.getHumanBadge() == null ? 0 : article.getHumanBadge());
        if (author != null) {
            r.setAuthorNickname(author.getNickname());
            r.setAuthorAvatar(author.getAvatarUrl());
            r.setAuthorBio(author.getBio());
        }
        return r;
    }
}
