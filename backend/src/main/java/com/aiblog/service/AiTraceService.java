package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.entity.AiOutput;
import com.aiblog.entity.Article;
import com.aiblog.entity.ArticleVersion;
import com.aiblog.mapper.AiOutputMapper;
import com.aiblog.mapper.ArticleMapper;
import com.aiblog.mapper.ArticleVersionMapper;
import com.aiblog.security.CurrentUser;
import com.aiblog.security.LoginUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 创作溯源聚合：按文章聚合 ai_output 审计记录，输出面向读者的信任摘要。
 * 只输出白名单字段（摘要与时间线），绝不暴露提示词原文、请求快照与全文输出。
 */
@Service
@RequiredArgsConstructor
public class AiTraceService {

    /** 时间线条目上限，超出仅保留最近记录 */
    private static final int TIMELINE_LIMIT = 50;
    private static final int PREVIEW_LIMIT = 120;

    private final ArticleMapper articleMapper;
    private final AiOutputMapper aiOutputMapper;
    private final ArticleVersionMapper articleVersionMapper;

    /**
     * 公开溯源摘要。已发布文章任何人可看；草稿/私享仅作者或管理员。
     */
    public Map<String, Object> trace(Long articleId) {
        Article article = mustGet(articleId);
        List<AiOutput> outputs = aiOutputMapper.selectList(Wrappers.<AiOutput>lambdaQuery()
                .eq(AiOutput::getArticleId, articleId)
                .orderByAsc(AiOutput::getId));

        long aiCallCount = outputs.size();
        long adoptedCount = outputs.stream().filter(o -> o.getAdopted() != null && o.getAdopted() == 1).count();
        long failedCount = outputs.stream().filter(o -> "failed".equals(o.getStatus())).count();
        long manualSaveCount = articleVersionMapper.selectCount(Wrappers.<ArticleVersion>lambdaQuery()
                .eq(ArticleVersion::getArticleId, articleId));

        int score = score(article.getAiUsageLevel(), aiCallCount, adoptedCount);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("articleId", articleId);
        resp.put("title", article.getTitle());
        resp.put("status", article.getStatus());
        resp.put("aiUsageLevel", article.getAiUsageLevel());
        resp.put("humanBadge", article.getHumanBadge() != null && article.getHumanBadge() == 1);
        resp.put("aiDeclaredAt", article.getAiDeclaredAt());
        resp.put("aiCallCount", aiCallCount);
        resp.put("adoptedCount", adoptedCount);
        resp.put("failedCount", failedCount);
        resp.put("manualSaveCount", manualSaveCount);
        resp.put("humanEditRatio", humanEditRatio(manualSaveCount, adoptedCount));
        resp.put("assistScore", score);
        resp.put("label", label(article.getAiUsageLevel(), score));
        resp.put("actionStats", actionStats(outputs));
        resp.put("timeline", timeline(outputs));
        return resp;
    }

    /**
     * AI 辅助度评分（0-100，仅供参考）：以声明等级为基准，按采纳率微调。
     * none 且无调用 → 0；声明 generated 至少 90，代表主要由 AI 生成。
     */
    int score(String level, long totalCalls, long adoptedCount) {
        int base;
        switch (level == null ? AiDisclosureService.LEVEL_NONE : level) {
            case AiDisclosureService.LEVEL_GENERATED -> base = 90;
            case AiDisclosureService.LEVEL_ASSISTED -> base = 50;
            default -> base = totalCalls > 0 ? 20 : 0;
        }
        if (totalCalls == 0) {
            return base;
        }
        double adoptRate = (double) adoptedCount / totalCalls;
        int bonus = (int) Math.round(10 * adoptRate);
        return Math.min(100, base + bonus);
    }

    /**
     * 三档分级提示（对齐《标识办法》的显式语气梯度）：
     * 属于生成合成 / 可能为 AI 辅助 / 疑似少量 AI 参与。
     */
    Map<String, Object> label(String level, int score) {
        String normalized = level == null ? AiDisclosureService.LEVEL_NONE : level;
        String tier;
        String text;
        switch (normalized) {
            case AiDisclosureService.LEVEL_GENERATED -> {
                tier = "generated";
                text = "本文属于 AI 生成合成内容";
            }
            case AiDisclosureService.LEVEL_ASSISTED -> {
                tier = "assisted";
                text = "本文可能为 AI 辅助创作";
            }
            default -> {
                tier = score >= 20 ? "assisted" : "human";
                text = score >= 20 ? "本文疑似使用了 AI 辅助" : "本文疑似无 AI 参与（作者声明为人类创作）";
            }
        }
        Map<String, Object> label = new LinkedHashMap<>();
        label.put("tier", tier);
        label.put("text", text);
        return label;
    }

    /** 按动作聚合调用与采纳数，动作名做脱敏中文化 */
    private List<Map<String, Object>> actionStats(List<AiOutput> outputs) {
        Map<String, long[]> grouped = new TreeMap<>();
        for (AiOutput o : outputs) {
            long[] stat = grouped.computeIfAbsent(actionKey(o), k -> new long[2]);
            stat[0]++;
            if (o.getAdopted() != null && o.getAdopted() == 1) {
                stat[1]++;
            }
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, long[]> e : grouped.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("action", e.getKey());
            item.put("name", actionName(e.getKey()));
            item.put("calls", e.getValue()[0]);
            item.put("adopted", e.getValue()[1]);
            list.add(item);
        }
        return list;
    }

    /** 时间线：白名单字段 + 截断预览，被标记内容隐藏预览 */
    private List<Map<String, Object>> timeline(List<AiOutput> outputs) {
        List<AiOutput> latest = outputs.stream()
                .sorted(Comparator.comparing(AiOutput::getId).reversed())
                .limit(TIMELINE_LIMIT)
                .toList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (AiOutput o : latest) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("time", o.getFinishedAt() != null ? o.getFinishedAt() : o.getCreatedAt());
            item.put("action", actionKey(o));
            item.put("name", actionName(actionKey(o)));
            item.put("task", o.getTask());
            item.put("source", o.getSource());
            item.put("status", o.getStatus());
            item.put("adopted", o.getAdopted() != null && o.getAdopted() == 1);
            item.put("outputLength", o.getOutputLength());
            item.put("durationMs", o.getDurationMs());
            boolean flagged = AiAuditService.REVIEW_FLAGGED.equals(o.getReviewStatus());
            item.put("flagged", flagged);
            item.put("preview", flagged ? null : preview(o.getOutputPreview()));
            list.add(item);
        }
        return list;
    }

    /** 人工修改占比：人工保存次数 /（人工保存 + 采纳的 AI 输出），无记录时返回 null */
    private Double humanEditRatio(long manualSaveCount, long adoptedCount) {
        long total = manualSaveCount + adoptedCount;
        if (total == 0) {
            return null;
        }
        return Math.round(1000.0 * manualSaveCount / total) / 10.0;
    }

    private String actionKey(AiOutput o) {
        String action = o.getAction();
        return action == null ? "other" : action;
    }

    private String actionName(String action) {
        return switch (action) {
            case "generate" -> "AI 生成";
            case "review" -> "AI 审阅";
            case "search" -> "AI 搜索增强";
            case "care" -> "写作关怀";
            case "style_profile" -> "文风提炼";
            default -> "其他";
        };
    }

    private String preview(String outputPreview) {
        if (outputPreview == null) {
            return null;
        }
        String text = outputPreview.replaceAll("\\s+", " ").trim();
        return text.length() <= PREVIEW_LIMIT ? text : text.substring(0, PREVIEW_LIMIT) + "…";
    }

    private Article mustGet(Long id) {
        Article article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        if (!"published".equals(article.getStatus())) {
            boolean allowed;
            try {
                LoginUser current = CurrentUser.get();
                allowed = Objects.equals(current.getId(), article.getAuthorId()) || current.isAdmin();
            } catch (RuntimeException e) {
                allowed = false;
            }
            if (!allowed) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "文章不存在");
            }
        }
        return article;
    }
}
