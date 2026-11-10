package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.entity.AiOutput;
import com.aiblog.entity.Article;
import com.aiblog.entity.ArticleReadStat;
import com.aiblog.entity.Curator;
import com.aiblog.mapper.AiOutputMapper;
import com.aiblog.mapper.ArticleMapper;
import com.aiblog.mapper.ArticleReadStatMapper;
import com.aiblog.mapper.CurationSlotMapper;
import com.aiblog.mapper.CuratorMapper;
import com.aiblog.mapper.UserMapper;
import com.aiblog.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 伯乐策展纯逻辑单测：周期、任期、完读率、违规过滤与推荐校验。
 */
class CuratorServiceTest {

    private final CuratorMapper curatorMapper = mock(CuratorMapper.class);
    private final CurationSlotMapper slotMapper = mock(CurationSlotMapper.class);
    private final ArticleMapper articleMapper = mock(ArticleMapper.class);
    private final AiOutputMapper aiOutputMapper = mock(AiOutputMapper.class);
    private final ArticleReadStatMapper readStatMapper = mock(ArticleReadStatMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final AiTraceService aiTraceService = mock(AiTraceService.class);
    private final CuratorService service = new CuratorService(curatorMapper, slotMapper,
            articleMapper, aiOutputMapper, readStatMapper, userMapper, aiTraceService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Long id, String role) {
        LoginUser user = new LoginUser(id, "user" + id, "pwd", role, 0);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private Curator curator(String status, LocalDate start, LocalDate end) {
        Curator c = new Curator();
        c.setId(1L);
        c.setUserId(7L);
        c.setStatus(status);
        c.setTermStart(start);
        c.setTermEnd(end);
        return c;
    }

    @Test
    void currentWeekUsesIsoFormat() {
        assertTrue(CuratorService.currentWeek().matches("\\d{4}-W\\d{2}"));
    }

    @Test
    void termWindowDecidesActive() {
        LocalDate today = LocalDate.now();
        assertTrue(CuratorService.activeInTerm(curator("active", today.minusDays(3), today.plusDays(3))));
        assertTrue(CuratorService.activeInTerm(curator("active", null, null)));
        assertFalse(CuratorService.activeInTerm(curator("inactive", null, null)));
        assertFalse(CuratorService.activeInTerm(curator("active", today.plusDays(1), null)));
        assertFalse(CuratorService.activeInTerm(curator("active", null, today.minusDays(1))));
    }

    @Test
    void completionRateIsCappedAndZeroWithoutViews() {
        Article article = new Article();
        article.setViewCount(20);
        ArticleReadStat stat = new ArticleReadStat();
        stat.setCompleteCount(10);
        assertEquals(50.0, CuratorService.completionRate(article, stat));

        stat.setCompleteCount(40);
        assertEquals(100.0, CuratorService.completionRate(article, stat));

        article.setViewCount(0);
        assertEquals(0.0, CuratorService.completionRate(article, stat));
        assertEquals(0.0, CuratorService.completionRate(article, null));
    }

    @Test
    void flaggedAnyRecordFiltersCandidate() {
        AiOutput passed = new AiOutput();
        passed.setReviewStatus(AiAuditService.REVIEW_PASSED);
        assertFalse(CuratorService.flagged(List.of(passed)));

        AiOutput flagged = new AiOutput();
        flagged.setReviewStatus(AiAuditService.REVIEW_FLAGGED);
        assertTrue(CuratorService.flagged(List.of(passed, flagged)));
    }

    @Test
    void recommendRequiresReasonAndActiveCurator() {
        loginAs(7L, "USER");
        when(curatorMapper.selectOne(any())).thenReturn(curator("active", null, null));
        Article article = new Article();
        article.setId(2L);
        article.setStatus("published");
        when(articleMapper.selectById(2L)).thenReturn(article);

        assertThrows(BusinessException.class, () -> service.recommend(2L, " "));
    }

    @Test
    void recommendRejectsUsersOutsideTerm() {
        loginAs(7L, "USER");
        when(curatorMapper.selectOne(any())).thenReturn(curator("inactive", null, null));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.recommend(2L, "值得一读"));
        assertEquals(1003, ex.getErrorCode().getCode());
    }
}
