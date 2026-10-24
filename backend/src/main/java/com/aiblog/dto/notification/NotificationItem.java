package com.aiblog.dto.notification;

import com.aiblog.entity.Notification;
import com.aiblog.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificationItem {

    private Long id;
    private String type;
    private Long actorId;
    private String actorNickname;
    private String actorAvatar;
    private Long articleId;
    private String articleTitle;
    private Long commentId;
    private String content;
    private Boolean read;
    private LocalDateTime createdAt;

    public static NotificationItem of(Notification n, User actor, String articleTitle) {
        NotificationItem item = new NotificationItem();
        item.setId(n.getId());
        item.setType(n.getType());
        item.setActorId(n.getActorId());
        item.setArticleId(n.getArticleId());
        item.setArticleTitle(articleTitle);
        item.setCommentId(n.getCommentId());
        item.setContent(n.getContent());
        item.setRead(n.getIsRead() != null && n.getIsRead() > 0);
        item.setCreatedAt(n.getCreatedAt());
        if (actor != null) {
            item.setActorNickname(actor.getNickname());
            item.setActorAvatar(actor.getAvatarUrl());
        }
        return item;
    }
}
