package com.aiblog.dto.admin;

import com.aiblog.entity.Article;
import com.aiblog.entity.Comment;
import com.aiblog.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端全站评论列表项：联出作者昵称与所属文章标题。
 */
@Data
public class AdminCommentItem {

    private Long id;
    private Long articleId;
    private String articleTitle;
    private Long authorId;
    private String authorNickname;
    private String content;
    private LocalDateTime createdAt;

    public static AdminCommentItem of(Comment comment, User author, Article article) {
        AdminCommentItem item = new AdminCommentItem();
        item.setId(comment.getId());
        item.setArticleId(comment.getArticleId());
        item.setArticleTitle(article == null ? null : article.getTitle());
        item.setAuthorId(comment.getAuthorId());
        item.setAuthorNickname(author == null ? null : author.getNickname());
        item.setContent(comment.getContent());
        item.setCreatedAt(comment.getCreatedAt());
        return item;
    }
}
