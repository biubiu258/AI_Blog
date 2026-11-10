package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.entity.AiOutput;
import com.aiblog.entity.Article;
import com.aiblog.entity.ArticleReadStat;
import com.aiblog.entity.CurationSlot;
import com.aiblog.entity.Curator;
import com.aiblog.entity.User;
import com.aiblog.mapper.AiOutputMapper;
import com.aiblog.mapper.ArticleMapper;
import com.aiblog.mapper.ArticleReadStatMapper;
import com.aiblog.mapper.CurationSlotMapper;
import com.aiblog.mapper.CuratorMapper;
import com.aiblog.mapper.UserMapper;
import com.aiblog.security.CurrentUser;
import com.aiblog.security.LoginUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 伯乐/策展：AI 只按「辅助度低 + 完读率高 + 无违规」排序候选，
 * 由当期伯乐人工确认推荐位并填写对读者可见的推荐理由。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CuratorService {

    /** 每期最多推荐篇数 */
    static final int MAX_SLOTS_PER_WEEK = 5;
    /** 候选池回看天数 */
    private static final int CANDIDATE_DAYS = 60;
    private static final int CANDIDATE_LIMIT = 30;

    private final CuratorMapper curatorMapper;
    private final CurationSlotMapper slotMapper;
    private final ArticleMapper articleMapper;
    private final AiOutputMapper aiOutputMapper;
    private final ArticleReadStatMapper readStatMapper;
    private final UserMapper userMapper;
    private final AiTraceService aiTraceService;

    /** 当前登录者的伯乐身份：供前端决定是否展示策展入口 */
    public Map<String, Object> me() {
        Long userId = CurrentUser.id();
        Curator curator = curatorMapper.selectOne(Wrappers.<Curator>lambdaQuery()
                .eq(Curator::getUserId, userId));
        Map<String, Object> resp = new HashMap<>();
        resp.put("isCurator", curator != null && activeInTerm(curator));
        resp.put("curatorId", curator == null ? null : curator.getId());
        resp.put("termStart", curator == null ? null : curator.getTermStart());
        resp.put("termEnd", curator == null ? null : curator.getTermEnd());
        return resp;
    }

    /** 管理端指定伯乐（含任期）；已存在则更新任期并恢复在任 */
    public Map<String, Object> addCurator(Long userId, LocalDate termStart, LocalDate termEnd) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "userId 不能为空");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (termStart != null && termEnd != null && termStart.isAfter(termEnd)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "任期开始不能晚于结束");
        }
        Curator curator = curatorMapper.selectOne(Wrappers.<Curator>lambdaQuery()
                .eq(Curator::getUserId, userId));
        if (curator == null) {
            curator = new Curator();
            curator.setUserId(userId);
            curator.setStatus("active");
            curator.setTermStart(termStart);
            curator.setTermEnd(termEnd);
            curatorMapper.insert(curator);
        } else {
            curator.setStatus("active");
            if (termStart != null) {
                curator.setTermStart(termStart);
            }
            if (termEnd != null) {
                curator.setTermEnd(termEnd);
            }
            curatorMapper.updateById(curator);
        }
        return curatorItem(curator, user);
    }

    public List<Map<String, Object>> adminList() {
        List<Curator> curators = curatorMapper.selectList(Wrappers.<Curator>lambdaQuery()
                .orderByDesc(Curator::getCreatedAt));
        if (curators.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = userMapper.selectBatchIds(curators.stream()
                        .map(Curator::getUserId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return curators.stream().map(c -> curatorItem(c, users.get(c.getUserId()))).toList();
    }

    /** 移除伯乐：置为 inactive，保留历史推荐位 */
    public void removeCurator(Long id) {
        Curator curator = curatorMapper.selectById(id);
        if (curator == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "伯乐不存在");
        }
        curator.setStatus("inactive");
        curatorMapper.updateById(curator);
    }

    /** 本周伯乐推荐位，reason 对读者可见 */
    public List<Map<String, Object>> slots(String week) {
        String target = StringUtils.hasText(week) ? week : currentWeek();
        List<CurationSlot> slots = slotMapper.selectList(Wrappers.<CurationSlot>lambdaQuery()
                .eq(CurationSlot::getWeek, target)
                .orderByDesc(CurationSlot::getCreatedAt));
        if (slots.isEmpty()) {
            return List.of();
        }
        Map<Long, Article> articles = articleMapper.selectBatchIds(slots.stream()
                        .map(CurationSlot::getArticleId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Article::getId, Function.identity()));
        Map<Long, User> authors = loadUsers(articles.values().stream()
                .map(Article::getAuthorId).collect(Collectors.toSet()));
        Map<Long, Curator> curators = loadCurators(slots.stream()
                .map(CurationSlot::getCuratorId).collect(Collectors.toSet()));
        Map<Long, User> curatorUsers = loadUsers(curators.values().stream()
                .map(Curator::getUserId).collect(Collectors.toSet()));
        List<Map<String, Object>> items = new ArrayList<>();
        for (CurationSlot slot : slots) {
            Article article = articles.get(slot.getArticleId());
            if (article == null || !"published".equals(article.getStatus())) {
                continue;
            }
            User author = authors.get(article.getAuthorId());
            Curator curator = curators.get(slot.getCuratorId());
            User curatorUser = curator == null ? null : curatorUsers.get(curator.getUserId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("slotId", slot.getId());
            item.put("articleId", article.getId());
            item.put("title", article.getTitle());
            item.put("summary", article.getSummary());
            item.put("authorId", article.getAuthorId());
            item.put("authorNickname", author == null ? null : author.getNickname());
            item.put("authorAvatar", author == null ? null : author.getAvatarUrl());
            item.put("publishedAt", article.getPublishedAt());
            item.put("week", slot.getWeek());
            item.put("reason", slot.getReason());
            item.put("curatorNickname", curatorUser == null ? null : curatorUser.getNickname());
            items.add(item);
        }
        return items;
    }

    /** 候选池：AI 辅助度低、完读率高、无违规优先；已在本周推荐的排除 */
    public List<Map<String, Object>> candidates() {
        LoginUser current = CurrentUser.get();
        Curator curator = curatorMapper.selectOne(Wrappers.<Curator>lambdaQuery()
                .eq(Curator::getUserId, current.getId()));
        boolean isCurator = curator != null && activeInTerm(curator);
        if (!isCurator && !current.isContentAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅伯乐或运营可查看候选池");
        }
        LocalDateTime from = LocalDateTime.now().minusDays(CANDIDATE_DAYS);
        List<Article> articles = articleMapper.selectList(Wrappers.<Article>lambdaQuery()
                .eq(Article::getStatus, "published")
                .ge(Article::getPublishedAt, from)
                .ne(Article::getAuthorId, current.getId())
                .orderByDesc(Article::getPublishedAt));
        if (articles.isEmpty()) {
            return List.of();
        }
        Set<Long> articleIds = articles.stream().map(Article::getId).collect(Collectors.toSet());
        Set<Long> slotted = slotMapper.selectList(Wrappers.<CurationSlot>lambdaQuery()
                        .eq(CurationSlot::getWeek, currentWeek())
                        .in(CurationSlot::getArticleId, articleIds)).stream()
                .map(CurationSlot::getArticleId).collect(Collectors.toSet());
        Map<Long, List<AiOutput>> outputs = aiOutputMapper.selectList(
                        Wrappers.<AiOutput>lambdaQuery().in(AiOutput::getArticleId, articleIds)).stream()
                .collect(Collectors.groupingBy(AiOutput::getArticleId));
        Map<Long, ArticleReadStat> readStats = readStatMapper.selectBatchIds(articleIds).stream()
                .collect(Collectors.toMap(ArticleReadStat::getArticleId, Function.identity()));
        Map<Long, User> authors = loadUsers(articles.stream()
                .map(Article::getAuthorId).collect(Collectors.toSet()));

        List<Map<String, Object>> candidates = new ArrayList<>();
        for (Article article : articles) {
            if (slotted.contains(article.getId())) {
                continue;
            }
            List<AiOutput> records = outputs.getOrDefault(article.getId(), List.of());
            if (flagged(records)) {
                continue;
            }
            long adopted = records.stream()
                    .filter(o -> o.getAdopted() != null && o.getAdopted() == 1).count();
            int assistScore = aiTraceService.score(article.getAiUsageLevel(), records.size(), adopted);
            double completionRate = completionRate(article, readStats.get(article.getId()));
            User author = authors.get(article.getAuthorId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("articleId", article.getId());
            item.put("title", article.getTitle());
            item.put("summary", article.getSummary());
            item.put("authorId", article.getAuthorId());
            item.put("authorNickname", author == null ? null : author.getNickname());
            item.put("publishedAt", article.getPublishedAt());
            item.put("viewCount", article.getViewCount() == null ? 0 : article.getViewCount());
            item.put("completeCount", readStats.containsKey(article.getId())
                    ? readStats.get(article.getId()).getCompleteCount() : 0);
            item.put("completionRate", completionRate);
            item.put("aiUsageLevel", article.getAiUsageLevel());
            item.put("assistScore", assistScore);
            item.put("humanBadge", article.getHumanBadge() != null && article.getHumanBadge() == 1);
            candidates.add(item);
        }
        // AI 只做筛选：辅助度越低越靠前，其次完读率高、阅读量高
        candidates.sort(Comparator
                .comparingInt((Map<String, Object> m) -> (Integer) m.get("assistScore"))
                .thenComparingDouble(m -> -(Double) m.get("completionRate"))
                .thenComparingLong(m -> -((Number) m.get("viewCount")).longValue()));
        return candidates.size() <= CANDIDATE_LIMIT
                ? candidates : new ArrayList<>(candidates.subList(0, CANDIDATE_LIMIT));
    }

    /** 伯乐确认推荐：必须填写推荐理由；每周上限 5 篇 */
    @Transactional
    public Map<String, Object> recommend(Long articleId, String reason) {
        Curator curator = requireActiveCurator();
        Article article = articleMapper.selectById(articleId);
        if (article == null || !"published".equals(article.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文章不存在或未发布");
        }
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "推荐理由不能为空");
        }
        String text = reason.trim();
        if (text.length() > 500) {
            text = text.substring(0, 500);
        }
        String week = currentWeek();
        long existing = slotMapper.selectCount(Wrappers.<CurationSlot>lambdaQuery()
                .eq(CurationSlot::getCuratorId, curator.getId())
                .eq(CurationSlot::getArticleId, articleId)
                .eq(CurationSlot::getWeek, week));
        if (existing > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该文章已在本次推荐位中");
        }
        long total = slotMapper.selectCount(Wrappers.<CurationSlot>lambdaQuery()
                .eq(CurationSlot::getCuratorId, curator.getId())
                .eq(CurationSlot::getWeek, week));
        if (total >= MAX_SLOTS_PER_WEEK) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "每周最多推荐 " + MAX_SLOTS_PER_WEEK + " 篇");
        }
        CurationSlot slot = new CurationSlot();
        slot.setCuratorId(curator.getId());
        slot.setArticleId(articleId);
        slot.setWeek(week);
        slot.setReason(text);
        slotMapper.insert(slot);
        log.info("伯乐推荐: curator={}, article={}, week={}", curator.getId(), articleId, week);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("slotId", slot.getId());
        resp.put("articleId", articleId);
        resp.put("week", week);
        resp.put("reason", text);
        return resp;
    }

    public void removeSlot(Long id) {
        CurationSlot slot = slotMapper.selectById(id);
        if (slot == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "推荐位不存在");
        }
        LoginUser current = CurrentUser.get();
        Curator curator = curatorMapper.selectById(slot.getCuratorId());
        boolean owner = curator != null && Objects.equals(curator.getUserId(), current.getId());
        if (!owner && !current.isContentAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权移除该推荐位");
        }
        slotMapper.deleteById(id);
    }

    private Curator requireActiveCurator() {
        Curator curator = curatorMapper.selectOne(Wrappers.<Curator>lambdaQuery()
                .eq(Curator::getUserId, CurrentUser.id()));
        if (curator == null || !activeInTerm(curator)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前不在伯乐任期内");
        }
        return curator;
    }

    private Map<Long, User> loadUsers(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Set<Long> clean = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (clean.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(clean).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private Map<Long, Curator> loadCurators(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return curatorMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Curator::getId, Function.identity()));
    }

    static boolean flagged(List<AiOutput> records) {
        return records.stream().anyMatch(o -> AiAuditService.REVIEW_FLAGGED.equals(o.getReviewStatus()));
    }

    static double completionRate(Article article, ArticleReadStat stat) {
        int views = article.getViewCount() == null ? 0 : article.getViewCount();
        if (views <= 0 || stat == null || stat.getCompleteCount() == null) {
            return 0.0;
        }
        double rate = Math.min(100.0, 100.0 * stat.getCompleteCount() / views);
        return Math.round(rate * 10) / 10.0;
    }

    /** ISO 周次，如 2026-W45 */
    public static String currentWeek() {
        LocalDate today = LocalDate.now();
        WeekFields iso = WeekFields.ISO;
        int year = today.get(iso.weekBasedYear());
        int week = today.get(iso.weekOfWeekBasedYear());
        return String.format("%d-W%02d", year, week);
    }

    static boolean activeInTerm(Curator curator) {
        LocalDate today = LocalDate.now();
        if (!"active".equals(curator.getStatus())) {
            return false;
        }
        if (curator.getTermStart() != null && today.isBefore(curator.getTermStart())) {
            return false;
        }
        return curator.getTermEnd() == null || !today.isAfter(curator.getTermEnd());
    }

    private static Map<String, Object> curatorItem(Curator curator, User user) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", curator.getId());
        item.put("userId", curator.getUserId());
        item.put("nickname", user == null ? null : user.getNickname());
        item.put("avatar", user == null ? null : user.getAvatarUrl());
        item.put("status", curator.getStatus());
        item.put("active", activeInTerm(curator));
        item.put("termStart", curator.getTermStart());
        item.put("termEnd", curator.getTermEnd());
        item.put("createdAt", curator.getCreatedAt());
        return item;
    }
}
