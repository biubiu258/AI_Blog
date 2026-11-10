package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.entity.AiOutput;
import com.aiblog.entity.Article;
import com.aiblog.mapper.AiOutputMapper;
import com.aiblog.mapper.ArticleMapper;
import com.aiblog.mapper.ArticleVersionMapper;
import com.aiblog.security.CurrentUser;
import com.aiblog.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AiTraceService 纯逻辑单测：辅助度评分、三档分级、白名单时间线与权限。
 */
class AiTraceServiceTest {

    private final ArticleMapper articleMapper = mock(ArticleMapper.class);
    private final AiOutputMapper aiOutputMapper = mock(AiOutputMapper.class);
    private final ArticleVersionMapper articleVersionMapper = mock(ArticleVersionMapper.class);
    private final AiTraceService service =
            new AiTraceService(articleMapper, aiOutputMapper, articleVersionMapper);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Long id, String role) {
        LoginUser user = new LoginUser(id, "user" + id, "pwd", role, 0);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private Article publishedArticle() {
        Article article = new Article();
        article.setId(11L);
        article.setAuthorId(7L);
        article.setTitle("散文一篇");
        article.setStatus("published");
        article.setAiUsageLevel("assisted");
        article.setAiDeclaredAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        article.setHumanBadge(0);
        return article;
    }

    private AiOutput output(long id, String action, String task, String status,
                            int adopted, String reviewStatus) {
        AiOutput o = new AiOutput();
        o.setId(id);
        o.setArticleId(11L);
        o.setAction(action);
        o.setTask(task);
        o.setStatus(status);
        o.setAdopted(adopted);
        o.setReviewStatus(reviewStatus);
        o.setOutputPreview("  AI 生成的\n段落预览  ");
        o.setOutputLength(120);
        o.setDurationMs(800);
        o.setRequestSnapshot("{\"secret\":\"提示词原文\"}");
        o.setOutput("完整输出内容不应出现在时间线");
        o.setCreatedAt(LocalDateTime.of(2026, 11, 1, 9, 0));
        o.setFinishedAt(LocalDateTime.of(2026, 11, 1, 9, 1));
        return o;
    }

    @Test
    void scoreFollowsDeclaredLevelAndAdoptionRate() {
        assertEquals(0, service.score("none", 0, 0));
        assertEquals(90, service.score("generated", 0, 0));
        assertEquals(50, service.score("assisted", 4, 0));
        assertEquals(60, service.score("assisted", 4, 4));
        assertEquals(20, service.score(null, 3, 0));
        assertEquals(100, service.score("generated", 2, 2));
    }

    @Test
    void labelMapsThreeTiers() {
        assertEquals("generated", service.label("generated", 95).get("tier"));
        assertEquals("assisted", service.label("assisted", 55).get("tier"));
        assertEquals("human", service.label("none", 0).get("tier"));
        assertEquals("assisted", service.label("none", 25).get("tier"));

        Map<String, Object> label = service.label("generated", 95);
        assertTrue(((String) label.get("text")).contains("生成合成"));
    }

    @Test
    void traceAggregatesWhitelistedTimeline() {
        when(articleMapper.selectById(11L)).thenReturn(publishedArticle());
        when(aiOutputMapper.selectList(any())).thenReturn(List.of(
                output(1, "generate", "draft", "success", 1, "passed"),
                output(2, "review", "review", "success", 0, "flagged"),
                output(3, "generate", "polish", "failed", 0, "pending")));
        when(articleVersionMapper.selectCount(any())).thenReturn(2L);

        Map<String, Object> result = service.trace(11L);

        assertEquals(3L, result.get("aiCallCount"));
        assertEquals(1L, result.get("adoptedCount"));
        assertEquals(1L, result.get("failedCount"));
        assertEquals(2L, result.get("manualSaveCount"));
        assertEquals("assisted", result.get("aiUsageLevel"));
        // 人工修改占比 = 2 / (2 + 1)
        assertEquals(66.7, (Double) result.get("humanEditRatio"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> timeline = (List<Map<String, Object>>) result.get("timeline");
        assertEquals(3, timeline.size());
        Map<String, Object> first = timeline.get(0);
        assertEquals("AI 生成", first.get("name"));
        assertFalse((Boolean) first.get("adopted"));
        // 白名单：绝不暴露提示词与全文
        assertFalse(first.containsKey("requestSnapshot"));
        assertFalse(first.containsKey("requestDigest"));
        assertFalse(first.containsKey("output"));
        assertTrue(((String) first.get("preview")).startsWith("AI 生成的"));

        // 被标记的记录隐藏预览
        Map<String, Object> flagged = timeline.get(1);
        assertTrue((Boolean) flagged.get("flagged"));
        assertNull(flagged.get("preview"));

        // 倒数第一条为已采纳记录
        assertTrue((Boolean) timeline.get(2).get("adopted"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> actionStats = (List<Map<String, Object>>) result.get("actionStats");
        assertEquals(2, actionStats.size());
    }

    @Test
    void timelineCapsAtFiftyEntriesNewestFirst() {
        when(articleMapper.selectById(11L)).thenReturn(publishedArticle());
        when(aiOutputMapper.selectList(any())).thenReturn(java.util.stream.LongStream
                .rangeClosed(1, 60)
                .mapToObj(i -> {
                    AiOutput o = output(i, "generate", "draft", "success", 0, "passed");
                    o.setOutputLength((int) i);
                    return o;
                })
                .toList());
        when(articleVersionMapper.selectCount(any())).thenReturn(0L);

        Map<String, Object> result = service.trace(11L);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> timeline = (List<Map<String, Object>>) result.get("timeline");
        assertEquals(50, timeline.size());
        assertEquals(60, timeline.get(0).get("outputLength"));
        assertEquals(11, timeline.get(49).get("outputLength"));
    }

    @Test
    void traceRejectsDraftForAnonymous() {
        Article draft = publishedArticle();
        draft.setStatus("draft");
        when(articleMapper.selectById(11L)).thenReturn(draft);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.trace(11L));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void traceAllowsDraftForAuthorAndAdmin() {
        Article draft = publishedArticle();
        draft.setStatus("draft");
        when(articleMapper.selectById(11L)).thenReturn(draft);
        when(aiOutputMapper.selectList(any())).thenReturn(List.of());
        when(articleVersionMapper.selectCount(any())).thenReturn(0L);

        loginAs(7L, "USER");
        assertEquals("draft", service.trace(11L).get("status"));

        SecurityContextHolder.clearContext();
        loginAs(99L, "ADMIN");
        assertFalse((Boolean) service.trace(11L).get("humanBadge"));
    }
}
