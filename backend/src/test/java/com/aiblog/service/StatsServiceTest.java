package com.aiblog.service;

import com.aiblog.entity.User;
import com.aiblog.mapper.StatsMapper;
import com.aiblog.mapper.UserMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * StatsService 纯逻辑单测：热度加权、榜期换算、7 日趋势补零。
 */
class StatsServiceTest {

    private final StatsMapper statsMapper = mock(StatsMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final InteractionService interactionService = mock(InteractionService.class);
    private final StatsService service =
            new StatsService(statsMapper, userMapper, interactionService);

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    @Test
    void hotComputesWeightedScore() {
        when(statsMapper.hotArticles(any(), any(), anyInt())).thenReturn(List.of(
                row("id", 1L, "title", "甲", "rangeViews", 100L,
                        "likeCount", 2L, "favoriteCount", 1L, "viewCount", 500L)));
        List<Map<String, Object>> list = service.hotArticles("week", 10);
        assertEquals(1, list.size());
        // 100 × 1 + 2 × 5 + 1 × 8
        assertEquals(118L, list.get(0).get("score"));
    }

    @Test
    void hotRangeMapsToTimeWindow() {
        when(statsMapper.hotArticles(any(), any(), anyInt())).thenReturn(List.of());
        org.mockito.ArgumentCaptor<java.time.LocalDateTime> from =
                org.mockito.ArgumentCaptor.forClass(java.time.LocalDateTime.class);
        org.mockito.ArgumentCaptor<LocalDate> fromDate =
                org.mockito.ArgumentCaptor.forClass(LocalDate.class);

        service.hotArticles("week", 10);
        service.hotArticles("month", 10);
        service.hotArticles("all", 10);

        org.mockito.Mockito.verify(statsMapper, org.mockito.Mockito.times(3))
                .hotArticles(from.capture(), fromDate.capture(), eq(10));
        org.junit.jupiter.api.Assertions.assertNotNull(from.getAllValues().get(0));
        org.junit.jupiter.api.Assertions.assertNotNull(from.getAllValues().get(1));
        // 总榜不带时间窗口
        assertNull(from.getAllValues().get(2));
        assertNull(fromDate.getAllValues().get(2));
    }

    @Test
    void authorStatsFillsSevenDayTrendAndHidesDraftCountsForVisitors() {
        User user = new User();
        user.setId(7L);
        when(userMapper.selectById(7L)).thenReturn(user);
        when(statsMapper.countByStatus(7L)).thenReturn(List.of(
                row("status", "published", "cnt", 3L),
                row("status", "draft", "cnt", 2L)));
        when(statsMapper.dailyViews(eq(7L), any(LocalDate.class))).thenReturn(List.of(
                row("viewDate", LocalDate.now(), "cnt", 4L)));
        when(interactionService.followerCount(7L)).thenReturn(9L);
        when(statsMapper.totalViews(7L)).thenReturn(120L);
        when(statsMapper.topArticles(7L, 5)).thenReturn(List.of());

        Map<String, Object> stats = service.authorStats(7L);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> trend = (List<Map<String, Object>>) stats.get("trend");
        assertEquals(7, trend.size());
        assertEquals(4L, trend.get(6).get("count"));
        assertEquals(4L, stats.get("views7d"));
        assertEquals(3L, stats.get("publishedCount"));
        assertEquals(9L, stats.get("followerCount"));
        // 非本人不暴露草稿维度
        assertFalse((Boolean) stats.get("self"));
        assertNull(stats.get("draftCount"));
    }

    @Test
    void authorStatsRejectsUnknownUser() {
        when(userMapper.selectById(99L)).thenReturn(null);
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> service.authorStats(99L));
    }
}
