package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.entity.AiUsage;
import com.aiblog.mapper.AiUsageMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 治理：每日配额、用量记账、建议采纳率统计。
 */
@Service
@RequiredArgsConstructor
public class AiUsageService {

    private final AiUsageMapper usageMapper;

    @Value("${app.ai.daily-quota:200}")
    private int dailyQuota;

    public void checkQuota(Long userId) {
        long used = usageMapper.selectCount(Wrappers.<AiUsage>lambdaQuery()
                .eq(AiUsage::getUserId, userId)
                .ge(AiUsage::getCreatedAt, LocalDate.now().atStartOfDay()));
        if (used >= dailyQuota) {
            throw new BusinessException(ErrorCode.TOO_MANY_ATTEMPTS,
                    "今日 AI 助手额度已用完（" + dailyQuota + " 次），请明天再试");
        }
    }

    public void record(Long userId, String action) {
        AiUsage usage = new AiUsage();
        usage.setUserId(userId);
        usage.setAction(action);
        usage.setTokensIn(0);
        usage.setTokensOut(0);
        usage.setAdopted(0);
        usageMapper.insert(usage);
    }

    /** 标记当前用户最近一次该动作的建议被采纳 */
    public void markAdopted(Long userId, String action) {
        AiUsage latest = usageMapper.selectOne(Wrappers.<AiUsage>lambdaQuery()
                .eq(AiUsage::getUserId, userId)
                .eq(AiUsage::getAction, action)
                .orderByDesc(AiUsage::getId)
                .last("LIMIT 1"));
        if (latest != null && (latest.getAdopted() == null || latest.getAdopted() == 0)) {
            latest.setAdopted(1);
            usageMapper.updateById(latest);
        }
    }

    public Map<String, Object> stats(int days) {
        int span = Math.max(1, Math.min(days, 90));
        LocalDateTime since = LocalDate.now().minusDays(span - 1L).atStartOfDay();
        List<AiUsage> rows = usageMapper.selectList(Wrappers.<AiUsage>lambdaQuery()
                .ge(AiUsage::getCreatedAt, since));

        Map<String, Long> byAction = new HashMap<>();
        Map<String, long[]> daily = new LinkedHashMap<>();
        for (int i = span - 1; i >= 0; i--) {
            daily.put(LocalDate.now().minusDays(i).toString(), new long[]{0, 0});
        }
        long adopted = 0;
        for (AiUsage row : rows) {
            byAction.merge(row.getAction(), 1L, Long::sum);
            if (row.getAdopted() != null && row.getAdopted() > 0) {
                adopted++;
            }
            String date = row.getCreatedAt().toLocalDate().toString();
            long[] bucket = daily.get(date);
            if (bucket != null) {
                bucket[0]++;
                if (row.getAdopted() != null && row.getAdopted() > 0) {
                    bucket[1]++;
                }
            }
        }
        List<Map<String, Object>> dailyList = new ArrayList<>();
        daily.forEach((date, bucket) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date);
            item.put("count", bucket[0]);
            item.put("adopted", bucket[1]);
            dailyList.add(item);
        });
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("days", span);
        resp.put("total", rows.size());
        resp.put("adopted", adopted);
        resp.put("adoptionRate", rows.isEmpty() ? 0d
                : Math.round(adopted * 1000.0 / rows.size()) / 1000.0);
        resp.put("byAction", byAction);
        resp.put("daily", dailyList);
        return resp;
    }
}
