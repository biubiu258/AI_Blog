package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.entity.WritingStreak;
import com.aiblog.entity.WritingStreakDay;
import com.aiblog.mapper.WritingStreakDayMapper;
import com.aiblog.mapper.WritingStreakMapper;
import com.aiblog.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 日更打卡纯逻辑单测：首日、连更、断更、里程碑与补签。
 */
class WritingStreakServiceTest {

    private final WritingStreakMapper streakMapper = mock(WritingStreakMapper.class);
    private final WritingStreakDayMapper dayMapper = mock(WritingStreakDayMapper.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final WritingStreakService service = new WritingStreakService(
            streakMapper, dayMapper, notificationService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Long id) {
        LoginUser user = new LoginUser(id, "user" + id, "pwd", "USER", 0);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private WritingStreak streak(int current, int longest, LocalDate last, int cards) {
        WritingStreak s = new WritingStreak();
        s.setUserId(7L);
        s.setCurrentStreak(current);
        s.setLongestStreak(longest);
        s.setLastActiveDate(last);
        s.setMakeupCards(cards);
        return s;
    }

    @Test
    void firstActivityStartsAtOne() {
        service.recordActivity(7L, LocalDate.of(2026, 11, 4), "save");
        verify(dayMapper).insert(any(WritingStreakDay.class));
        verify(streakMapper).insert(any(WritingStreak.class));
        verify(streakMapper, never())
                .updateById(org.mockito.ArgumentMatchers.<WritingStreak>any());
        verify(notificationService, never()).notify(any(), anyString(), any(), any(), any(), anyString());
    }

    @Test
    void consecutiveDayIncrementsAndHitsMilestone() {
        LocalDate today = LocalDate.of(2026, 11, 4);
        when(streakMapper.selectById(7L)).thenReturn(streak(6, 6, today.minusDays(1), 0));

        service.recordActivity(7L, today, "publish");

        verify(streakMapper).updateById(org.mockito.ArgumentMatchers.<WritingStreak>argThat(s ->
                s.getCurrentStreak() == 7 && s.getLongestStreak() == 7
                        && s.getMakeupCards() == 1 && s.getLastActiveDate().equals(today)));
        verify(notificationService).notify(eq(7L), eq("streak"), isNull(), isNull(), isNull(),
                anyString());
    }

    @Test
    void sameDayIsIdempotent() {
        LocalDate today = LocalDate.of(2026, 11, 4);
        when(streakMapper.selectById(7L)).thenReturn(streak(3, 3, today, 0));

        service.recordActivity(7L, today, "save");

        verify(dayMapper).insert(any(WritingStreakDay.class));
        verify(streakMapper, never())
                .updateById(org.mockito.ArgumentMatchers.<WritingStreak>any());
    }

    @Test
    void gapResetsCurrentButKeepsLongest() {
        LocalDate today = LocalDate.of(2026, 11, 4);
        when(streakMapper.selectById(7L)).thenReturn(streak(5, 9, today.minusDays(4), 0));

        service.recordActivity(7L, today, "save");

        verify(streakMapper).updateById(org.mockito.ArgumentMatchers.<WritingStreak>argThat(s ->
                s.getCurrentStreak() == 1 && s.getLongestStreak() == 9));
        verify(notificationService, never()).notify(any(), anyString(), any(), any(), any(), anyString());
    }

    @Test
    void makeupCardContinuesStreakFromDayBeforeYesterday() {
        loginAs(7L);
        LocalDate today = LocalDate.of(2026, 11, 4);
        when(streakMapper.selectById(7L))
                .thenReturn(streak(4, 4, today.minusDays(2), 1));

        Map<String, Object> result = service.useMakeupCard();

        assertEquals(5, result.get("currentStreak"));
        assertEquals(0, result.get("makeupCards"));
        verify(streakMapper).updateById(org.mockito.ArgumentMatchers.<WritingStreak>argThat(s ->
                s.getLastActiveDate().equals(today.minusDays(1)) && s.getMakeupCards() == 0));
    }

    @Test
    void makeupRejectedWithoutCards() {
        loginAs(7L);
        when(streakMapper.selectById(7L)).thenReturn(streak(2, 2, LocalDate.now().minusDays(3), 0));
        assertThrows(BusinessException.class, service::useMakeupCard);
    }

    @Test
    void summaryBuildsThirtyDayCalendarAndAchievements() {
        LocalDate today = LocalDate.now();
        when(streakMapper.selectById(7L)).thenReturn(streak(8, 12, today, 1));
        WritingStreakDay day = new WritingStreakDay();
        day.setUserId(7L);
        day.setActiveDate(today);
        day.setSource("publish");
        when(dayMapper.selectList(any())).thenReturn(List.of(day));

        Map<String, Object> result = service.summary(7L);

        assertTrue((Boolean) result.get("checkedInToday"));
        assertEquals(30, ((List<?>) result.get("calendar")).size());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> achievements = (List<Map<String, Object>>) result.get("achievements");
        assertTrue((Boolean) achievements.get(0).get("achieved"));
        assertFalse((Boolean) achievements.get(1).get("achieved"));
    }

    @Test
    void brokenStreakReminderResetsAndNotifies() {
        WritingStreak missed = streak(3, 7, LocalDate.now().minusDays(3), 0);
        when(streakMapper.selectList(any())).thenReturn(List.of(missed));

        service.remindBrokenStreaks();

        verify(streakMapper).updateById(org.mockito.ArgumentMatchers.<WritingStreak>argThat(
                s -> s.getCurrentStreak() == 0 && s.getLongestStreak() == 7));
        verify(notificationService).notify(eq(7L), eq("streak"), isNull(), isNull(), isNull(),
                anyString());
    }

    @Test
    void milestoneCrossedPicksHighestBoundary() {
        assertEquals(0, WritingStreakService.milestoneCrossed(6, 6));
        assertEquals(7, WritingStreakService.milestoneCrossed(6, 7));
        assertEquals(30, WritingStreakService.milestoneCrossed(29, 30));
        assertEquals(365, WritingStreakService.milestoneCrossed(300, 400));
    }
}
