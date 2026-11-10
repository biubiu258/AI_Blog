package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.config.AiProperties;
import com.aiblog.ai.PythonErrorTranslator;
import com.aiblog.entity.Article;
import com.aiblog.entity.StyleProfile;
import com.aiblog.mapper.ArticleMapper;
import com.aiblog.mapper.StyleProfileMapper;
import com.aiblog.security.CurrentUser;
import com.aiblog.security.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * StyleProfileService 纯逻辑单测：样本校验、归属校验与默认档解析（不触网）。
 */
class StyleProfileServiceTest {

    private final StyleProfileMapper styleProfileMapper = mock(StyleProfileMapper.class);
    private final ArticleMapper articleMapper = mock(ArticleMapper.class);
    private final WebClient webClient = WebClient.builder().build();
    private final PythonErrorTranslator errorTranslator = mock(PythonErrorTranslator.class);
    private final StyleProfileService service = new StyleProfileService(
            styleProfileMapper, articleMapper, webClient, new AiProperties(),
            errorTranslator, new ObjectMapper());

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Long id) {
        LoginUser user = new LoginUser(id, "user" + id, "pwd", "USER", 0);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private Article ownArticle(long id) {
        Article a = new Article();
        a.setId(id);
        a.setAuthorId(7L);
        a.setTitle("旧文" + id);
        a.setContentHtml("<p>" + "内容足够长的一段文字。".repeat(20) + "</p>");
        return a;
    }

    @Test
    void createRejectsBadNameAndSampleCount() {
        loginAs(7L);
        assertThrows(BusinessException.class, () -> service.create(" ", List.of(1L, 2L, 3L)));
        assertThrows(BusinessException.class,
                () -> service.create("我的文风", List.of(1L, 2L)));
        assertThrows(BusinessException.class,
                () -> service.create("我的文风", List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L)));
    }

    @Test
    void createRejectsForeignOrShortArticles() {
        loginAs(7L);
        when(articleMapper.selectById(1L)).thenReturn(ownArticle(1L));
        Article foreign = ownArticle(2L);
        foreign.setAuthorId(99L);
        when(articleMapper.selectById(2L)).thenReturn(foreign);
        when(articleMapper.selectById(3L)).thenAnswer(inv -> ownArticle(3L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create("我的文风", List.of(1L, 2L, 3L)));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());

        Article shortOne = ownArticle(4L);
        shortOne.setContentHtml("<p>太短</p>");
        when(articleMapper.selectById(4L)).thenReturn(shortOne);
        when(articleMapper.selectById(5L)).thenAnswer(inv -> ownArticle(5L));
        when(articleMapper.selectById(6L)).thenAnswer(inv -> ownArticle(6L));
        assertThrows(BusinessException.class,
                () -> service.create("我的文风", List.of(1L, 4L, 5L)));
    }

    @Test
    void deleteRejectsForeignProfile() {
        loginAs(7L);
        StyleProfile profile = new StyleProfile();
        profile.setId(1L);
        profile.setUserId(99L);
        when(styleProfileMapper.selectById(1L)).thenReturn(profile);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(1L));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void resolveUsesRequestedProfileWithOwnershipCheck() {
        StyleProfile ready = new StyleProfile();
        ready.setId(5L);
        ready.setUserId(7L);
        ready.setStatus(StyleProfileService.STATUS_READY);
        when(styleProfileMapper.selectById(5L)).thenReturn(ready);

        assertEquals(5L, service.resolveForGeneration(7L, 5L).getId());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.resolveForGeneration(8L, 5L));
        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());

        ready.setStatus(StyleProfileService.STATUS_FAILED);
        assertThrows(BusinessException.class, () -> service.resolveForGeneration(7L, 5L));
    }

    @Test
    void resolveFallsBackToDefaultProfile() {
        when(styleProfileMapper.selectOne(any())).thenReturn(null);
        assertNull(service.resolveForGeneration(7L, null));

        StyleProfile def = new StyleProfile();
        def.setId(9L);
        def.setUserId(7L);
        def.setStatus(StyleProfileService.STATUS_READY);
        when(styleProfileMapper.selectOne(any())).thenReturn(def);
        assertEquals(9L, service.resolveForGeneration(7L, null).getId());
    }

    @Test
    void setDefaultRequiresReadyStatusAndResetsOthers() {
        loginAs(7L);
        StyleProfile building = new StyleProfile();
        building.setId(1L);
        building.setUserId(7L);
        building.setStatus(StyleProfileService.STATUS_BUILDING);
        when(styleProfileMapper.selectById(1L)).thenReturn(building);
        assertThrows(BusinessException.class, () -> service.setDefault(1L));

        building.setStatus(StyleProfileService.STATUS_READY);
        when(styleProfileMapper.selectList(any())).thenReturn(List.of());
        service.setDefault(1L);
        verify(styleProfileMapper).updateById(building);
    }
}
