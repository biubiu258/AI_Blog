package com.aiblog.service;

import com.aiblog.entity.Article;
import com.aiblog.entity.CareLog;
import com.aiblog.entity.StyleProfile;
import com.aiblog.mapper.ArticleMapper;
import com.aiblog.mapper.CareLogMapper;
import com.aiblog.security.CurrentUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 写作陪伴提示（迭代 2 关怀增强）：把关怀检测结果、写作节奏与个人文风库
 * 组合成一段温和的陪伴文案。只观察、不评价、不建议，避免制造写作压力。
 */
@Service
@RequiredArgsConstructor
public class WritingCompanionService {

    private static final int CARE_LOOKBACK_DAYS = 30;

    private final ArticleMapper articleMapper;
    private final CareLogMapper careLogMapper;
    private final StyleProfileService styleProfileService;

    public Map<String, Object> companion() {
        Long userId = CurrentUser.id();

        LocalDateTime lastPublishAt = articleMapper.selectList(
                        Wrappers.<Article>lambdaQuery()
                                .eq(Article::getAuthorId, userId)
                                .eq(Article::getStatus, "published")
                                .orderByDesc(Article::getPublishedAt)
                                .last("LIMIT 1"))
                .stream().findFirst()
                .map(Article::getPublishedAt)
                .orElse(null);
        long publishedCount = articleMapper.selectCount(
                Wrappers.<Article>lambdaQuery()
                        .eq(Article::getAuthorId, userId)
                        .eq(Article::getStatus, "published"));

        CareLog latestCare = careLogMapper.selectList(Wrappers.<CareLog>lambdaQuery()
                        .eq(CareLog::getUserId, userId)
                        .orderByDesc(CareLog::getId)
                        .last("LIMIT 1"))
                .stream().findFirst().orElse(null);
        String recentCareLevel = null;
        if (latestCare != null && latestCare.getCreatedAt() != null
                && latestCare.getCreatedAt().isAfter(
                LocalDateTime.now().minusDays(CARE_LOOKBACK_DAYS))) {
            recentCareLevel = latestCare.getLevel();
        }

        StyleProfile profile = styleProfileService.resolveForGeneration(userId, null);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("message", buildMessage(publishedCount, lastPublishAt, recentCareLevel, profile != null));
        resp.put("tone", toneOf(publishedCount, lastPublishAt, recentCareLevel));
        resp.put("publishedCount", publishedCount);
        resp.put("daysSinceLastPublish", daysSince(lastPublishAt));
        resp.put("recentCareLevel", recentCareLevel);
        resp.put("hasStyleProfile", profile != null);
        return resp;
    }

    private String buildMessage(long publishedCount, LocalDateTime lastPublishAt,
                                String careLevel, boolean hasStyleProfile) {
        StringBuilder sb = new StringBuilder();
        if (publishedCount == 0) {
            sb.append("还没有发布过文章，慢慢来，第一篇只需要诚实地写下此刻的想法。");
        } else {
            Long days = daysSince(lastPublishAt);
            if (days != null && days >= 14) {
                sb.append("已经 ").append(days).append(" 天没有发布新文章了，")
                        .append("无论这段时间在忙什么，回来的路一直都在。");
            } else if (days != null && days >= 7) {
                sb.append("上周之后还没有新的文字，不着急，灵感通常在生活里，不在输入框里。");
            } else {
                sb.append("最近的写作节奏很稳，记得偶尔停下来读读自己写下的句子。");
            }
        }
        if ("mid".equals(careLevel) || "high".equals(careLevel)) {
            sb.append("最近有些文字情绪比较重，写作可以是出口，但不是唯一的出口，照顾好自己更重要。");
        } else if ("low".equals(careLevel)) {
            sb.append("察觉到最近的文字有些低落，把它们写下来本身就是一种安放。");
        }
        if (hasStyleProfile) {
            sb.append("你的文风档案已经就绪，生成时可以带上自己的味道。");
        }
        return sb.toString();
    }

    private String toneOf(long publishedCount, LocalDateTime lastPublishAt, String careLevel) {
        if ("high".equals(careLevel) || "mid".equals(careLevel)) {
            return "gentle";
        }
        if (publishedCount == 0) {
            return "encourage";
        }
        Long days = daysSince(lastPublishAt);
        if (days != null && days >= 7) {
            return "warm";
        }
        return "steady";
    }

    private Long daysSince(LocalDateTime time) {
        return time == null ? null : Duration.between(time, LocalDateTime.now()).toDays();
    }
}
