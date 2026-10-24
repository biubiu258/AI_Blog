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
    private Long parentId;
    private Long replyToUserId;
    private String replyToNickname;
    private LocalDateTime createdAt;

    public static CommentItem of(Comment comment, User author, User replyTo) {
        CommentItem item = new CommentItem();
        item.setId(comment.getId());
        item.setArticleId(comment.getArticleId());
        item.setAuthorId(comment.getAuthorId());
        item.setContent(comment.getContent());
        item.setParentId(comment.getParentId());
        item.setReplyToUserId(comment.getReplyToUserId());
        item.setCreatedAt(comment.getCreatedAt());
        if (author != null) {
            item.setAuthorNickname(author.getNickname());
            item.setAuthorAvatar(author.getAvatarUrl());
        }
        if (replyTo != null) {
            item.setReplyToNickname(replyTo.getNickname());
        }
        return item;
    }
}
