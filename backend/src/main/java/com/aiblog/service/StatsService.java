package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.entity.User;
import com.aiblog.mapper.StatsMapper;
import com.aiblog.mapper.UserMapper;
import com.aiblog.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 作者数据面板与全站热门排行。
 * 热度权重：阅读 ×1、点赞 ×5、收藏 ×8。
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private static final int VIEW_WEIGHT = 1;
    private static final int LIKE_WEIGHT = 5;
    private static final int FAVORITE_WEIGHT = 8;
    private static final int TREND_DAYS = 7;

    private final StatsMapper statsMapper;
    private final UserMapper userMapper;
    private final InteractionService interactionService;

    /**
     * 作者数据：本人可见草稿/定时/私密数量，他人仅见公开维度的数据。
     */
    public Map<String, Object> authorStats(Long authorId) {
        User user = userMapper.selectById(authorId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        boolean self = Objects.equals(currentUserIdOrNull(), authorId);

        Map<String, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : statsMapper.countByStatus(authorId)) {
            counts.put(String.valueOf(row.get("status")), toLong(row.get("cnt")));
        }

        LocalDate trendFrom = LocalDate.now().minusDays(TREND_DAYS - 1L);
        Map<LocalDate, Long> daily = new LinkedHashMap<>();
        for (Map<String, Object> row : statsMapper.dailyViews(authorId, trendFrom)) {
            Object date = row.get("viewDate");
            if (date != null) {
                daily.put(LocalDate.parse(String.valueOf(date)), toLong(row.get("cnt")));
            }
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        long views7d = 0;
        for (int i = 0; i < TREND_DAYS; i++) {
            LocalDate day = trendFrom.plusDays(i);
            long count = daily.getOrDefault(day, 0L);
            views7d += count;
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", day.toString());
            point.put("count", count);
            trend.add(point);
        }

        List<Map<String, Object>> top = statsMapper.topArticles(authorId, 5).stream()
                .map(row -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", toLong(row.get("id")));
                    item.put("title", row.get("title"));
                    item.put("viewCount", toLong(row.get("viewCount")));
                    item.put("likeCount", toLong(row.get("likeCount")));
                    item.put("favoriteCount", toLong(row.get("favoriteCount")));
                    return item;
                })
                .toList();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("userId", authorId);
        resp.put("self", self);
        resp.put("publishedCount", counts.getOrDefault("published", 0L));
        resp.put("totalViews", statsMapper.totalViews(authorId));
        resp.put("likeCount", statsMapper.likesReceived(authorId));
        resp.put("favoriteCount", statsMapper.favoritesReceived(authorId));
        resp.put("commentCount", statsMapper.commentsReceived(authorId));
        resp.put("followerCount", interactionService.followerCount(authorId));
        resp.put("views7d", views7d);
        resp.put("trend", trend);
        resp.put("topArticles", top);
        if (self) {
            resp.put("draftCount", counts.getOrDefault("draft", 0L));
            resp.put("scheduledCount", counts.getOrDefault("scheduled", 0L));
            resp.put("privateCount", counts.getOrDefault("private", 0L));
        }
        return resp;
    }

    /**
     * 热门排行：range 支持 week / month / all，默认 week。
     */
    public List<Map<String, Object>> hotArticles(String range, int limit) {
        String scope = StringUtils.hasText(range) ? range : "week";
        int size = Math.min(Math.max(limit, 1), 50);
        LocalDateTime from = switch (scope) {
            case "month" -> LocalDateTime.now().minusDays(30);
            case "all" -> null;
            default -> LocalDateTime.now().minusDays(7);
        };
        LocalDate fromDate = from == null ? null : from.toLocalDate();
        return statsMapper.hotArticles(from, fromDate, size).stream()
                .map(row -> {
                    long views = toLong(row.get("rangeViews"));
                    long likes = toLong(row.get("likeCount"));
                    long favorites = toLong(row.get("favoriteCount"));
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", toLong(row.get("id")));
                    item.put("title", row.get("title"));
                    item.put("summary", row.get("summary"));
                    item.put("authorId", toLong(row.get("authorId")));
                    item.put("authorNickname", row.get("authorNickname"));
                    item.put("authorAvatar", row.get("authorAvatar"));
                    item.put("categoryName", row.get("categoryName"));
                    item.put("publishedAt", row.get("publishedAt"));
                    item.put("viewCount", toLong(row.get("viewCount")));
                    item.put("rangeViews", views);
                    item.put("likeCount", likes);
                    item.put("favoriteCount", favorites);
                    item.put("score", views * VIEW_WEIGHT + likes * LIKE_WEIGHT
                            + favorites * FAVORITE_WEIGHT);
                    return item;
                })
                .toList();
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? 0L : Long.parseLong(String.valueOf(value));
    }

    private static Long currentUserIdOrNull() {
        try {
            return CurrentUser.id();
        } catch (BusinessException e) {
            return null;
        }
    }
}
