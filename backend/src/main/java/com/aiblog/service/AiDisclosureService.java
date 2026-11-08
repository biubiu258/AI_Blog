package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.entity.AiOutput;
import com.aiblog.entity.Article;
import com.aiblog.entity.ContentLabelLog;
import com.aiblog.mapper.AiOutputMapper;
import com.aiblog.mapper.ArticleMapper;
import com.aiblog.mapper.ContentLabelLogMapper;
import com.aiblog.security.CurrentUser;
import com.aiblog.security.LoginUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * AI 参与声明与内容标识：对齐《人工智能生成合成内容标识办法》。
 * 生成侧负责显式标识（正文首尾与导出文件），传播侧负责隐式标识（元数据）与审计留痕。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiDisclosureService {

    public static final String LEVEL_NONE = "none";
    public static final String LEVEL_ASSISTED = "assisted";
    public static final String LEVEL_GENERATED = "generated";

    private static final Set<String> LEVELS = Set.of(LEVEL_NONE, LEVEL_ASSISTED, LEVEL_GENERATED);
    private static final int TRACE_LIMIT = 30;
    private static final int DRAFT_LOOKBACK_DAYS = 7;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ArticleMapper articleMapper;
    private final AiOutputMapper aiOutputMapper;
    private final ContentLabelLogMapper contentLabelLogMapper;
    private final ObjectMapper objectMapper;

    /** 规范化等级；未传返回 null（表示未声明） */
    public String normalizeOrNull(String level) {
        if (!StringUtils.hasText(level)) {
            return null;
        }
        String value = level.trim().toLowerCase(Locale.ROOT);
        if (!LEVELS.contains(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "aiUsageLevel 仅支持 none / assisted / generated");
        }
        return value;
    }

    /** 发布 / 定时发布前必须完成声明 */
    public String requireDeclared(String level) {
        String value = normalizeOrNull(level);
        if (value == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "发布前需完成 AI 参与声明（aiUsageLevel）");
        }
        return value;
    }

    /** 按审计记录推荐默认等级：无 AI 调用 → none，有调用 → assisted */
    public Map<String, Object> suggest(Long articleId) {
        long total = aiOutputMapper.selectCount(Wrappers.<AiOutput>lambdaQuery()
                .eq(AiOutput::getArticleId, articleId));
        long adopted = aiOutputMapper.selectCount(Wrappers.<AiOutput>lambdaQuery()
                .eq(AiOutput::getArticleId, articleId)
                .eq(AiOutput::getAdopted, 1));
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("articleId", articleId);
        resp.put("suggestedLevel", total == 0 ? LEVEL_NONE : LEVEL_ASSISTED);
        resp.put("aiCallCount", total);
        resp.put("adoptedCount", adopted);
        return resp;
    }

    /**
     * 写入声明的持久化字段（不落日志，由调用方在文章落库后调用 {@link #logChange}）。
     */
    public void applyDeclaration(Article article, String level) {
        String target = normalizeOrNull(level);
        if (target == null) {
            return;
        }
        String from = article.getAiUsageLevel();
        article.setAiUsageLevel(target);
        if (article.getAiDeclaredAt() == null || !Objects.equals(from, target)) {
            article.setAiDeclaredAt(LocalDateTime.now());
        }
        article.setAiTraceIds(serializeTraceIds(collectTraceIds(article.getId(), article.getAuthorId())));
    }

    /** 记录标识变更，满足可追溯与审计留存要求 */
    public void logChange(Long articleId, String fromLevel, String toLevel, Long operatorId,
                          String reason) {
        if (Objects.equals(fromLevel, toLevel) && fromLevel != null) {
            return;
        }
        ContentLabelLog entry = new ContentLabelLog();
        entry.setArticleId(articleId);
        entry.setFromLevel(fromLevel);
        entry.setToLevel(toLevel);
        entry.setOperatorId(operatorId);
        entry.setReason(reason);
        entry.setCreatedAt(LocalDateTime.now());
        contentLabelLogMapper.insert(entry);
    }

    /** 作者修改自己文章的 AI 参与声明 */
    public Map<String, Object> updateByAuthor(Long articleId, String level) {
        Article article = mustGet(articleId);
        LoginUser current = CurrentUser.get();
        if (!Objects.equals(current.getId(), article.getAuthorId()) && !current.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅作者或管理员可修改标识");
        }
        String target = requireDeclared(level);
        String from = article.getAiUsageLevel();
        article.setAiUsageLevel(target);
        article.setAiDeclaredAt(LocalDateTime.now());
        article.setAiTraceIds(serializeTraceIds(collectTraceIds(articleId, article.getAuthorId())));
        articleMapper.updateById(article);
        logChange(articleId, from, target, current.getId(), "作者主动修改声明");
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", articleId);
        resp.put("aiUsageLevel", target);
        resp.put("aiDeclaredAt", article.getAiDeclaredAt());
        return resp;
    }

    /** 管理端修正标识等级（含误标、漏标纠正） */
    public Map<String, Object> adminUpdate(Long articleId, String level, String reason) {
        Article article = mustGet(articleId);
        String target = requireDeclared(level);
        String from = article.getAiUsageLevel();
        article.setAiUsageLevel(target);
        article.setAiDeclaredAt(LocalDateTime.now());
        articleMapper.updateById(article);
        logChange(articleId, from, target, CurrentUser.id(), reason);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", articleId);
        resp.put("fromLevel", from);
        resp.put("aiUsageLevel", target);
        return resp;
    }

    /** 管理端标识台账：按等级筛选并带作者信息 */
    public Map<String, Object> adminPage(int page, int size, String level, String keyword) {
        String target = StringUtils.hasText(level) ? normalizeOrNull(level) : null;
        Page<Article> result = articleMapper.selectPage(Page.of(page, Math.min(size, 50)),
                Wrappers.<Article>lambdaQuery()
                        .eq(target != null, Article::getAiUsageLevel, target)
                        .like(StringUtils.hasText(keyword), Article::getTitle, keyword)
                        .orderByDesc(Article::getUpdatedAt));
        List<Map<String, Object>> items = new ArrayList<>();
        for (Article a : result.getRecords()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", a.getId());
            item.put("title", a.getTitle());
            item.put("status", a.getStatus());
            item.put("authorId", a.getAuthorId());
            item.put("aiUsageLevel", a.getAiUsageLevel());
            item.put("aiDeclaredAt", a.getAiDeclaredAt());
            item.put("updatedAt", a.getUpdatedAt());
            item.put("contentId", contentId(a));
            items.add(item);
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("total", result.getTotal());
        resp.put("items", items);
        return resp;
    }

    /** 文章标识变更历史（作者与管理员可见） */
    public List<ContentLabelLog> logs(Long articleId) {
        return contentLabelLogMapper.selectList(Wrappers.<ContentLabelLog>lambdaQuery()
                .eq(ContentLabelLog::getArticleId, articleId)
                .orderByDesc(ContentLabelLog::getCreatedAt));
    }

    /**
     * 导出 Markdown：隐式标识写入 front matter，显式标识置于正文首尾。
     */
    public String buildExportMarkdown(Article article, String authorNickname, String categoryName,
                                      List<String> tags) {
        String level = article.getAiUsageLevel() == null ? LEVEL_NONE : article.getAiUsageLevel();
        String contentId = contentId(article);
        List<String> traceIds = parseTraceIds(article.getAiTraceIds());
        StringBuilder sb = new StringBuilder();

        // 隐式标识：文件元数据
        sb.append("<!-- AI 内容标识文件，标识信息请勿删除或篡改 -->\n");
        sb.append("---\n");
        sb.append("ai_content_id: ").append(contentId).append('\n');
        sb.append("ai_usage_level: ").append(level).append('\n');
        sb.append("ai_service: AI_blog\n");
        if (article.getAiDeclaredAt() != null) {
            sb.append("ai_declared_at: ").append(article.getAiDeclaredAt().format(TS)).append('\n');
        }
        if (!traceIds.isEmpty()) {
            sb.append("ai_trace_ids: ").append(String.join(",", traceIds)).append('\n');
        }
        sb.append("---\n\n");

        // 显式标识：正文起始
        if (!LEVEL_NONE.equals(level)) {
            sb.append("> 【AI 内容标识】").append(labelText(level))
                    .append("。内容编号：").append(contentId).append("\n\n");
        }

        sb.append("# ").append(article.getTitle()).append("\n\n");
        if (StringUtils.hasText(article.getSummary())) {
            sb.append("> ").append(article.getSummary()).append("\n\n");
        }
        if (StringUtils.hasText(authorNickname)) {
            sb.append("作者：").append(authorNickname);
            if (StringUtils.hasText(categoryName)) {
                sb.append(" · 分类：").append(categoryName);
            }
            if (tags != null && !tags.isEmpty()) {
                sb.append(" · 标签：").append(String.join("、", tags));
            }
            sb.append("\n\n");
        }
        sb.append(htmlToPlainText(article.getContentHtml())).append("\n");

        // 显式标识：正文末尾
        if (!LEVEL_NONE.equals(level)) {
            sb.append("\n> 【AI 内容标识】本文档").append(labelText(level))
                    .append("，内容编号：").append(contentId).append("。\n");
        }
        sb.append("\n<!-- AI 内容标识结束 -->\n");
        return sb.toString();
    }

    /** 展示用标识文案 */
    public static String labelText(String level) {
        return switch (level == null ? LEVEL_NONE : level) {
            case LEVEL_ASSISTED -> "含 AI 辅助创作内容，已由作者人工修改与确认";
            case LEVEL_GENERATED -> "内容主要由 AI 生成，已由作者审核后发布";
            default -> "未使用 AI 生成内容";
        };
    }

    /** 平台内容编号：对外稳定且不暴露内部自增规律之外的信息 */
    public static String contentId(Article article) {
        return "AIB-ARTICLE-" + article.getId();
    }

    private Article mustGet(Long articleId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        return article;
    }

    /**
     * 关联的 AI 调用追踪号：优先取该文章下已采纳的记录；
     * 文章新建于 AI 调用之后时，回退到作者近 7 天未挂文章的已采纳记录。
     */
    private List<String> collectTraceIds(Long articleId, Long userId) {
        List<AiOutput> records = new ArrayList<>();
        if (articleId != null) {
            records = aiOutputMapper.selectList(Wrappers.<AiOutput>lambdaQuery()
                    .eq(AiOutput::getArticleId, articleId)
                    .eq(AiOutput::getAdopted, 1)
                    .orderByAsc(AiOutput::getId)
                    .last("limit " + TRACE_LIMIT));
        }
        if (records.isEmpty() && userId != null) {
            records = aiOutputMapper.selectList(Wrappers.<AiOutput>lambdaQuery()
                    .eq(AiOutput::getUserId, userId)
                    .eq(AiOutput::getAdopted, 1)
                    .isNull(AiOutput::getArticleId)
                    .ge(AiOutput::getCreatedAt, LocalDateTime.now().minusDays(DRAFT_LOOKBACK_DAYS))
                    .orderByAsc(AiOutput::getId)
                    .last("limit " + TRACE_LIMIT));
        }
        return records.stream()
                .map(AiOutput::getTraceId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String serializeTraceIds(List<String> traceIds) {
        if (traceIds.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(traceIds);
        } catch (Exception e) {
            log.warn("序列化 traceIds 失败，降级为空数组", e);
            return "[]";
        }
    }

    private List<String> parseTraceIds(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 正文 HTML 转 Markdown 纯文本：保留段落换行，剥离样式与脚本 */
    private String htmlToPlainText(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        String prepared = html
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|h[1-6]|li|blockquote|tr|section|article)>", "\n\n");
        String text = Jsoup.parse(prepared).body().wholeText();
        return text.replaceAll("[ \t]+", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }
}
