package com.aiblog.service;

import com.aiblog.dto.notification.NotificationItem;
import com.aiblog.entity.Article;
import com.aiblog.entity.Notification;
import com.aiblog.entity.User;
import com.aiblog.mapper.ArticleMapper;
import com.aiblog.mapper.NotificationMapper;
import com.aiblog.mapper.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 站内通知：点赞/收藏/评论/回复/关注触发，前端轮询未读数。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;
    private final ArticleMapper articleMapper;

    public void notify(Long userId, String type, Long actorId, Long articleId,
                       Long commentId, String content) {
        if (userId == null || Objects.equals(userId, actorId)) {
            return;
        }
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setActorId(actorId);
        n.setArticleId(articleId);
        n.setCommentId(commentId);
        n.setContent(content == null ? null : content.substring(0, Math.min(content.length(), 500)));
        n.setIsRead(0);
        notificationMapper.insert(n);
    }

    public Map<String, Object> page(Long userId, int page, int size, boolean unreadOnly,
                                    String type) {
        Page<Notification> result = notificationMapper.selectPage(Page.of(page, Math.min(size, 50)),
                Wrappers.<Notification>lambdaQuery()
                        .eq(Notification::getUserId, userId)
                        .eq(unreadOnly, Notification::getIsRead, 0)
                        .eq(Objects.nonNull(type) && !type.isBlank(), Notification::getType, type)
                        .orderByDesc(Notification::getCreatedAt));
        List<Notification> records = result.getRecords();
        Map<Long, User> actors = records.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(records.stream().map(Notification::getActorId)
                        .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, String> titles = new HashMap<>();
        Set<Long> articleIds = records.stream().map(Notification::getArticleId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (!articleIds.isEmpty()) {
            articleMapper.selectBatchIds(articleIds)
                    .forEach(a -> titles.put(a.getId(), a.getTitle()));
        }
        List<NotificationItem> items = records.stream()
                .map(n -> NotificationItem.of(n, actors.get(n.getActorId()),
                        titles.get(n.getArticleId())))
                .toList();
        Map<String, Object> resp = new HashMap<>();
        resp.put("total", result.getTotal());
        resp.put("unread", unreadCount(userId));
        resp.put("items", items);
        return resp;
    }

    public long unreadCount(Long userId) {
        return notificationMapper.selectCount(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
    }

    public void markRead(Long userId, List<Long> ids) {
        notificationMapper.update(null, Wrappers.<Notification>lambdaUpdate()
                .eq(Notification::getUserId, userId)
                .in(ids != null && !ids.isEmpty(), Notification::getId, ids)
                .set(Notification::getIsRead, 1));
    }

    public void notifyComment(Article article, Long commenterId, Long commentId, String content) {
        if (article == null || commenterId == null) {
            return;
        }
        notify(article.getAuthorId(), "comment", commenterId, article.getId(), commentId,
                "评论了《" + article.getTitle() + "》：" + abbreviate(content));
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        String plain = text.replaceAll("\\s+", " ").trim();
        return plain.length() <= 80 ? plain : plain.substring(0, 80) + "…";
    }
}
