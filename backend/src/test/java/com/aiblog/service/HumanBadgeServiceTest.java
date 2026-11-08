package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.entity.Article;
import com.aiblog.entity.HumanBadgeApplication;
import com.aiblog.mapper.ArticleMapper;
import com.aiblog.mapper.HumanBadgeApplicationMapper;
import com.aiblog.mapper.UserMapper;
import com.aiblog.security.CurrentUser;
import com.aiblog.security.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HumanBadgeService 纯逻辑单测：申请门槛、重复申请拦截、审核流转与徽章点亮。
 */
class HumanBadgeServiceTest {

    private final HumanBadgeApplicationMapper applicationMapper = mock(HumanBadgeApplicationMapper.class);
    private final ArticleMapper articleMapper = mock(ArticleMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HumanBadgeService service =
            new HumanBadgeService(applicationMapper, articleMapper, userMapper, objectMapper);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Long id) {
        LoginUser user = new LoginUser(id, "user" + id, "pwd", "USER", 0);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private Article article(long id, String level, Integer badge) {
        Article a = new Article();
        a.setId(id);
        a.setAuthorId(7L);
        a.setStatus("published");
        a.setAiUsageLevel(level);
        a.setHumanBadge(badge);
        a.setPublishedAt(LocalDateTime.now());
        return a;
    }

    @Test
    void applyRejectsWhenNotEnoughNoneArticles() {
        loginAs(7L);
        when(applicationMapper.selectCount(any())).thenReturn(0L);
        when(articleMapper.selectList(any())).thenReturn(List.of(
                article(1, "none", 0), article(2, "assisted", 0)));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.apply());
        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
    }

    @Test
    void applyRejectsDuplicatePending() {
        loginAs(7L);
        when(applicationMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.apply());
        assertTrue(ex.getMessage().contains("待审核"));
    }

    @Test
    void applyStoresEligibleArticleIds() {
        loginAs(7L);
        when(applicationMapper.selectCount(any())).thenReturn(0L);
        when(articleMapper.selectList(any())).thenReturn(List.of(
                article(1, "none", 0), article(2, "none", 0), article(3, "none", 0)));

        Map<String, Object> result = service.apply();

        assertEquals("pending", result.get("status"));
        assertEquals(List.of(1L, 2L, 3L), result.get("articleIds"));
        ArgumentCaptor<HumanBadgeApplication> captor =
                ArgumentCaptor.forClass(HumanBadgeApplication.class);
        verify(applicationMapper).insert(captor.capture());
        assertEquals("[1,2,3]", captor.getValue().getArticleIds());
    }

    @Test
    void reviewRejectsInvalidStatusAndRequiresNote() {
        assertThrows(BusinessException.class, () -> service.review(1L, "pending", null));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.review(1L, "rejected", "  "));
        assertTrue(ex.getMessage().contains("理由"));
    }

    @Test
    void reviewRejectsAlreadyReviewed() {
        HumanBadgeApplication done = new HumanBadgeApplication();
        done.setId(1L);
        done.setStatus("passed");
        when(applicationMapper.selectById(1L)).thenReturn(done);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.review(1L, "rejected", "不合格"));
        assertTrue(ex.getMessage().contains("已审核"));
    }

    @Test
    void reviewPassedLightsBadgesOnlyForEligibleArticles() {
        loginAs(99L);
        HumanBadgeApplication application = new HumanBadgeApplication();
        application.setId(1L);
        application.setUserId(7L);
        application.setStatus("pending");
        when(applicationMapper.selectById(1L)).thenReturn(application);
        // 抽审时重新收集符合条件文章：含一篇已点亮（不应重复计数）
        when(articleMapper.selectList(any())).thenReturn(List.of(
                article(1, "none", 0), article(2, "none", 1), article(3, "none", 0)));
        when(articleMapper.selectById(any())).thenAnswer(inv -> {
            long id = inv.getArgument(0);
            // 文章 2 已点亮徽章，不应重复计数
            return article(id, "none", id == 2 ? 1 : 0);
        });

        Map<String, Object> result = service.review(1L, "passed", "抽审通过");

        assertEquals("passed", result.get("status"));
        assertEquals(2, result.get("badgedArticles"));
        verify(applicationMapper).updateById(any(HumanBadgeApplication.class));
    }

    @Test
    void reviewRejectedDoesNotTouchArticles() {
        loginAs(99L);
        HumanBadgeApplication application = new HumanBadgeApplication();
        application.setId(1L);
        application.setUserId(7L);
        application.setStatus("pending");
        when(applicationMapper.selectById(1L)).thenReturn(application);

        Map<String, Object> result = service.review(1L, "rejected", "AI 生成内容占比过高");

        assertEquals("rejected", result.get("status"));
        assertEquals(0, result.get("badgedArticles"));
        verify(articleMapper, never()).updateById(any(Article.class));
    }

    @Test
    void hasBadgeChecksPassedApplicationsOnly() {
        when(applicationMapper.selectCount(any())).thenReturn(0L);
        assertFalse(service.hasBadge(7L));
        when(applicationMapper.selectCount(any())).thenReturn(1L);
        assertTrue(service.hasBadge(7L));
    }
}
