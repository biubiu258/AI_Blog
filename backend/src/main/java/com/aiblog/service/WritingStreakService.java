package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.entity.WritingStreak;
import com.aiblog.entity.WritingStreakDay;
import com.aiblog.mapper.WritingStreakDayMapper;
import com.aiblog.mapper.WritingStreakMapper;
import com.aiblog.security.CurrentUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 日更打卡：当日有保存或发布即打卡成功。
 * 连续天数按自然日累加，断更一天归零；连续满 7/30/100/365 天授予补签卡并发里程碑通知。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WritingStreakService {

    /** 连续天数里程碑 */
    static final int[] MILESTONES = {7, 30, 100, 365};

    /** 日历回看天数 */
    private static final int CALENDAR_DAYS = 30;

    private final WritingStreakMapper streakMapper;
    private final WritingStreakDayMapper dayMapper;
    private final NotificationService notificationService;

    /** 保存/发布埋点：同一天重复调用只记录一次 */
    public void recordActivity(Long userId, String source) {
        recordActivity(userId, LocalDate.now(), source);
    }

    void recordActivity(Long userId, LocalDate date, String source) {
        if (userId == null) {
            return;
        }
        recordDay(userId, date, source);
        WritingStreak streak = streakMapper.selectById(userId);
        if (streak == null) {
            WritingStreak fresh = new WritingStreak();
            fresh.setUserId(userId);
            fresh.setCurrentStreak(1);
            fresh.setLongestStreak(1);
            fresh.setLastActiveDate(date);
            fresh.setMakeupCards(0);
            streakMapper.insert(fresh);
            return;
        }
        LocalDate last = streak.getLastActiveDate();
        if (last != null && last.equals(date)) {
            return;
        }
        int previous = streak.getCurrentStreak() == null ? 0 : streak.getCurrentStreak();
        int current;
        if (last != null && last.plusDays(1).equals(date)) {
            current = previous + 1;
        } else {
            current = 1;
        }
        streak.setCurrentStreak(current);
        streak.setLongestStreak(Math.max(
                streak.getLongestStreak() == null ? 0 : streak.getLongestStreak(), current));
        streak.setLastActiveDate(date);
        int crossed = milestoneCrossed(previous, current);
        if (crossed > 0) {
            streak.setMakeupCards((streak.getMakeupCards() == null ? 0 : streak.getMakeupCards()) + 1);
        }
        streakMapper.updateById(streak);
        if (crossed > 0) {
            notifyMilestone(userId, crossed);
        }
    }

    /** 补签昨天：消耗一张补签卡，连续天数延续；断更超过一天则从 1 重新累计 */
    public Map<String, Object> useMakeupCard() {
        Long userId = CurrentUser.id();
        LocalDate today = LocalDate.now();
        WritingStreak streak = streakMapper.selectById(userId);
        if (streak == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "暂无打卡记录，无法补签");
        }
        if (streak.getMakeupCards() == null || streak.getMakeupCards() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "补签卡不足");
        }
        LocalDate yesterday = today.minusDays(1);
        LocalDate last = streak.getLastActiveDate();
        if (last != null && !last.isBefore(yesterday)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "最近一天已打卡，无需补签");
        }
        int previous = streak.getCurrentStreak() == null ? 0 : streak.getCurrentStreak();
        int current = last != null && last.equals(today.minusDays(2)) ? previous + 1 : 1;
        streak.setCurrentStreak(current);
        streak.setLongestStreak(Math.max(
                streak.getLongestStreak() == null ? 0 : streak.getLongestStreak(), current));
        streak.setLastActiveDate(yesterday);
        streak.setMakeupCards(streak.getMakeupCards() - 1);
        streakMapper.updateById(streak);
        recordDay(userId, yesterday, "makeup");
        log.info("补签成功: user={}, currentStreak={}", userId, current);
        return summary(userId);
    }

    /** 打卡日历、连续天数、成就与补签卡 */
    public Map<String, Object> summary(Long userId) {
        WritingStreak streak = streakMapper.selectById(userId);
        int current = streak == null || streak.getCurrentStreak() == null ? 0 : streak.getCurrentStreak();
        int longest = streak == null || streak.getLongestStreak() == null ? 0 : streak.getLongestStreak();
        LocalDate last = streak == null ? null : streak.getLastActiveDate();
        int cards = streak == null || streak.getMakeupCards() == null ? 0 : streak.getMakeupCards();

        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(CALENDAR_DAYS - 1L);
        List<WritingStreakDay> days = dayMapper.selectList(Wrappers.<WritingStreakDay>lambdaQuery()
                .eq(WritingStreakDay::getUserId, userId)
                .ge(WritingStreakDay::getActiveDate, from)
                .orderByAsc(WritingStreakDay::getActiveDate));
        Map<LocalDate, String> sources = new LinkedHashMap<>();
        days.forEach(d -> sources.put(d.getActiveDate(), d.getSource()));

        List<Map<String, Object>> calendar = new ArrayList<>();
        for (int i = 0; i < CALENDAR_DAYS; i++) {
            LocalDate day = from.plusDays(i);
            Map<String, Object> cell = new LinkedHashMap<>();
            cell.put("date", day.toString());
            cell.put("active", sources.containsKey(day));
            cell.put("source", sources.get(day));
            calendar.add(cell);
        }

        List<Map<String, Object>> achievements = new ArrayList<>();
        for (int milestone : MILESTONES) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("days", milestone);
            item.put("title", achievementTitle(milestone));
            item.put("achieved", longest >= milestone);
            achievements.add(item);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("currentStreak", current);
        resp.put("longestStreak", longest);
        resp.put("lastActiveDate", last == null ? null : last.toString());
        resp.put("makeupCards", cards);
        resp.put("checkedInToday", Objects.equals(last, today));
        resp.put("calendar", calendar);
        resp.put("achievements", achievements);
        return resp;
    }

    /**
     * 断更提醒：每天 20:00 扫描昨天未打卡且仍有连续记录的用户，
     * 发送一次站内提醒并将连续天数归零（最长记录保留）。
     */
    @Scheduled(cron = "0 0 20 * * ?")
    public void remindBrokenStreaks() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        List<WritingStreak> missed = streakMapper.selectList(Wrappers.<WritingStreak>lambdaQuery()
                .gt(WritingStreak::getCurrentStreak, 0)
                .lt(WritingStreak::getLastActiveDate, yesterday));
        for (WritingStreak streak : missed) {
            streak.setCurrentStreak(0);
            streakMapper.updateById(streak);
            notificationService.notify(streak.getUserId(), "streak", null, null, null,
                    "昨天的写作打卡断了，连续天数已归零。今天写一篇，重新开始吧。");
        }
        if (!missed.isEmpty()) {
            log.info("断更提醒: count={}", missed.size());
        }
    }

    /** 跨越的最高里程碑；未跨越返回 0 */
    static int milestoneCrossed(int previous, int current) {
        int crossed = 0;
        for (int milestone : MILESTONES) {
            if (previous < milestone && current >= milestone) {
                crossed = milestone;
            }
        }
        return crossed;
    }

    private void notifyMilestone(Long userId, int days) {
        notificationService.notify(userId, "streak", null, null, null,
                "连续写作 " + days + " 天，达成「" + achievementTitle(days)
                        + "」成就，获得 1 张补签卡。");
    }

    private void recordDay(Long userId, LocalDate date, String source) {
        WritingStreakDay day = new WritingStreakDay();
        day.setUserId(userId);
        day.setActiveDate(date);
        day.setSource(source == null ? "save" : source);
        try {
            dayMapper.insert(day);
        } catch (DuplicateKeyException ignored) {
            // 当日已记录，打卡明细按天去重
        }
    }

    private static String achievementTitle(int days) {
        return switch (days) {
            case 7 -> "一周不断更";
            case 30 -> "月度笔耕";
            case 100 -> "百日写作";
            case 365 -> "一年之约";
            default -> days + " 天连更";
        };
    }
}
