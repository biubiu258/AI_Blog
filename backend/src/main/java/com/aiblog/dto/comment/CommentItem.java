package com.aiblog.dto.comment;

import com.aiblog.entity.Comment;
import com.aiblog.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommentItem {

    private Long id;
    private Long articleId;
    private Long authorId;
    private String authorNickname;
    private String authorAvatar;
    private String content;
    private LocalDateTime createdAt;

    public static CommentItem of(Comment comment, User author) {
        CommentItem item = new CommentItem();
        item.setId(comment.getId());
        item.setArticleId(comment.getArticleId());
        item.setAuthorId(comment.getAuthorId());
        item.setContent(comment.getContent());
        item.setCreatedAt(comment.getCreatedAt());
        if (author != null) {
            item.setAuthorNickname(author.getNickname());
            item.setAuthorAvatar(author.getAvatarUrl());
        }
        return item;
    }
}
