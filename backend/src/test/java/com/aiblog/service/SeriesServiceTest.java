package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.entity.Article;
import com.aiblog.entity.Series;
import com.aiblog.entity.SeriesArticle;
import com.aiblog.entity.SeriesSubscription;
import com.aiblog.mapper.ArticleMapper;
import com.aiblog.mapper.SeriesArticleMapper;
import com.aiblog.mapper.SeriesMapper;
import com.aiblog.mapper.SeriesSubscriptionMapper;
import com.aiblog.mapper.UserMapper;
import com.aiblog.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 连载纯逻辑单测：标题校验、上下篇上下文、订阅与更新通知。
 */
class SeriesServiceTest {

    private final SeriesMapper seriesMapper = mock(SeriesMapper.class);
    private final SeriesArticleMapper seriesArticleMapper = mock(SeriesArticleMapper.class);
    private final SeriesSubscriptionMapper subscriptionMapper = mock(SeriesSubscriptionMapper.class);
    private final ArticleMapper articleMapper = mock(ArticleMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final SeriesService service = new SeriesService(seriesMapper, seriesArticleMapper,
            subscriptionMapper, articleMapper, userMapper, notificationService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Long id) {
        LoginUser user = new LoginUser(id, "user" + id, "pwd", "USER", 0);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private Article article(long id, String title, String status) {
        Article a = new Article();
        a.setId(id);
        a.setAuthorId(7L);
        a.setTitle(title);
        a.setStatus(status);
        return a;
    }

    private SeriesArticle relation(long seriesId, long articleId, int order) {
        SeriesArticle r = new SeriesArticle();
        r.setSeriesId(seriesId);
        r.setArticleId(articleId);
        r.setOrderNo(order);
        return r;
    }

    @Test
    void createRejectsEmptyOrTooLongTitle() {
        loginAs(7L);
        assertThrows(BusinessException.class, () -> service.create(" ", null));
        assertThrows(BusinessException.class,
                () -> service.create("标题".repeat(70), null));
    }

    @Test
    void createStartsAsOngoing() {
        loginAs(7L);
        Map<String, Object> result = service.create("小城故事", "系列说明");
        assertEquals("ongoing", result.get("status"));
        assertEquals(0L, result.get("articleCount"));
    }

    @Test
    void articleContextReturnsPrevNextByOrder() {
        Series series = new Series();
        series.setId(1L);
        series.setAuthorId(7L);
        series.setTitle("小城故事");
        series.setStatus("ongoing");
        when(seriesArticleMapper.selectList(any())).thenReturn(List.of(
                relation(1L, 10L, 1), relation(1L, 11L, 2), relation(1L, 12L, 3)));
        when(seriesMapper.selectById(1L)).thenReturn(series);
        when(articleMapper.selectBatchIds(any())).thenReturn(List.of(
                article(10L, "第一篇", "published"),
                article(11L, "第二篇", "published"),
                article(12L, "第三篇", "published")));

        Map<String, Object> context = service.articleContext(11L);

        assertEquals(1L, context.get("id"));
        assertEquals(2, context.get("index"));
        assertEquals(3, context.get("total"));
        @SuppressWarnings("unchecked")
        Map<String, Object> prev = (Map<String, Object>) context.get("prev");
        @SuppressWarnings("unchecked")
        Map<String, Object> next = (Map<String, Object>) context.get("next");
        assertEquals("第一篇", prev.get("title"));
        assertEquals("第三篇", next.get("title"));
    }

    @Test
    void articleNotInSeriesHasNoContext() {
        when(seriesArticleMapper.selectList(any())).thenReturn(List.of());
        assertNull(service.articleContext(99L));
    }

    @Test
    void subscribeInsertsOnceAndReportsCount() {
        loginAs(7L);
        Series series = new Series();
        series.setId(1L);
        series.setAuthorId(9L);
        when(seriesMapper.selectById(1L)).thenReturn(series);
        when(subscriptionMapper.selectCount(any())).thenReturn(0L, 1L);

        Map<String, Object> result = service.subscribe(1L);

        verify(subscriptionMapper).insert(any(SeriesSubscription.class));
        assertEquals(true, result.get("subscribed"));
        assertEquals(1L, result.get("subscriberCount"));
    }

    @Test
    void publishNotifiesSeriesSubscribers() {
        Series series = new Series();
        series.setId(1L);
        series.setTitle("小城故事");
        when(seriesArticleMapper.selectList(any())).thenReturn(List.of(relation(1L, 10L, 1)));
        when(seriesMapper.selectById(1L)).thenReturn(series);
        SeriesSubscription first = new SeriesSubscription();
        first.setSeriesId(1L);
        first.setUserId(21L);
        SeriesSubscription second = new SeriesSubscription();
        second.setSeriesId(1L);
        second.setUserId(22L);
        when(subscriptionMapper.selectList(any())).thenReturn(List.of(first, second));

        service.notifySeriesUpdate(article(10L, "第二篇", "published"));

        verify(notificationService).notify(eq(21L), eq("series"), eq(7L), eq(10L),
                any(), anyString());
        verify(notificationService).notify(eq(22L), eq("series"), eq(7L), eq(10L),
                any(), anyString());
    }

    @Test
    void draftDoesNotNotifySubscribers() {
        service.notifySeriesUpdate(article(10L, "草稿", "draft"));
        verify(notificationService, org.mockito.Mockito.never())
                .notify(any(), anyString(), any(), any(), any(), anyString());
        assertTrue(true);
    }
}
