package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.entity.Article;
import com.aiblog.entity.Series;
import com.aiblog.entity.SeriesArticle;
import com.aiblog.entity.SeriesSubscription;
import com.aiblog.entity.User;
import com.aiblog.mapper.ArticleMapper;
import com.aiblog.mapper.SeriesArticleMapper;
import com.aiblog.mapper.SeriesMapper;
import com.aiblog.mapper.SeriesSubscriptionMapper;
import com.aiblog.mapper.UserMapper;
import com.aiblog.security.CurrentUser;
import com.aiblog.security.LoginUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 专题/连载：作者按目录顺序组织文章，读者可订阅，更新时通过站内通知触达。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeriesService {

    public static final String STATUS_ONGOING = "ongoing";
    public static final String STATUS_FINISHED = "finished";

    private final SeriesMapper seriesMapper;
    private final SeriesArticleMapper seriesArticleMapper;
    private final SeriesSubscriptionMapper subscriptionMapper;
    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    @Transactional
    public Map<String, Object> create(String title, String description) {
        requireTitle(title);
        Series series = new Series();
        series.setAuthorId(CurrentUser.id());
        series.setTitle(title.trim());
        series.setDescription(trimToNull(description, 500));
        series.setStatus(STATUS_ONGOING);
        seriesMapper.insert(series);
        log.info("创建连载: id={}, author={}", series.getId(), series.getAuthorId());
        return toItem(series, 0);
    }

    public Map<String, Object> update(Long id, String title, String description, String status) {
        Series series = mustGet(id);
        requireAuthor(series);
        if (StringUtils.hasText(title)) {
            requireTitle(title);
            series.setTitle(title.trim());
        }
        if (description != null) {
            series.setDescription(trimToNull(description, 500));
        }
        if (StringUtils.hasText(status)) {
            if (!STATUS_ONGOING.equals(status) && !STATUS_FINISHED.equals(status)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "status 仅支持 ongoing / finished");
            }
            series.setStatus(status);
        }
        seriesMapper.updateById(series);
        return toItem(series, articleCount(id));
    }

    @Transactional
    public void delete(Long id) {
        Series series = mustGet(id);
        requireAuthor(series);
        seriesMapper.deleteById(id);
        seriesArticleMapper.delete(Wrappers.<SeriesArticle>lambdaQuery()
                .eq(SeriesArticle::getSeriesId, id));
        subscriptionMapper.delete(Wrappers.<SeriesSubscription>lambdaQuery()
                .eq(SeriesSubscription::getSeriesId, id));
        log.info("删除连载: id={}", id);
    }

    /** 我的连载列表 */
    public List<Map<String, Object>> mine() {
        Long userId = CurrentUser.id();
        List<Series> list = seriesMapper.selectList(Wrappers.<Series>lambdaQuery()
                .eq(Series::getAuthorId, userId)
                .orderByDesc(Series::getUpdatedAt));
        return list.stream().map(s -> toItem(s, articleCount(s.getId()))).toList();
    }

    /** 连载详情：目录按 order_no 排序；游客仅见已发布文章 */
    public Map<String, Object> detail(Long id) {
        Series series = mustGet(id);
        LoginUser current = currentUserOrNull();
        boolean privileged = current != null
                && (Objects.equals(current.getId(), series.getAuthorId()) || current.isContentAdmin());
        List<SeriesArticle> relations = relations(id);
        List<Article> articles = loadArticles(relations);
        Map<Long, Article> byId = articles.stream()
                .collect(Collectors.toMap(Article::getId, Function.identity()));
        List<Map<String, Object>> items = new ArrayList<>();
        int order = 0;
        for (SeriesArticle relation : relations) {
            Article article = byId.get(relation.getArticleId());
            if (article == null) {
                continue;
            }
            if (!privileged && !"published".equals(article.getStatus())) {
                continue;
            }
            order++;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("articleId", article.getId());
            item.put("title", article.getTitle());
            item.put("summary", article.getSummary());
            item.put("status", article.getStatus());
            item.put("publishedAt", article.getPublishedAt());
            item.put("viewCount", article.getViewCount() == null ? 0 : article.getViewCount());
            item.put("orderNo", relation.getOrderNo());
            item.put("index", order);
            items.add(item);
        }
        User author = userMapper.selectById(series.getAuthorId());
        Map<String, Object> resp = toItem(series, items.size());
        resp.put("authorNickname", author == null ? null : author.getNickname());
        resp.put("authorAvatar", author == null ? null : author.getAvatarUrl());
        resp.put("articles", items);
        resp.put("subscriberCount", subscriberCount(id));
        resp.put("subscribed", isSubscribed(id, current));
        resp.put("mine", current != null && Objects.equals(current.getId(), series.getAuthorId()));
        return resp;
    }

    /** 加入连载：文章必须属于当前作者；已发布文章加入时立即通知订阅者 */
    @Transactional
    public Map<String, Object> addArticle(Long seriesId, Long articleId, Integer orderNo) {
        Series series = mustGet(seriesId);
        requireAuthor(series);
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        if (!Objects.equals(article.getAuthorId(), series.getAuthorId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能加入自己的文章");
        }
        long exists = seriesArticleMapper.selectCount(Wrappers.<SeriesArticle>lambdaQuery()
                .eq(SeriesArticle::getSeriesId, seriesId)
                .eq(SeriesArticle::getArticleId, articleId));
        if (exists > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "文章已在该连载中");
        }
        SeriesArticle relation = new SeriesArticle();
        relation.setSeriesId(seriesId);
        relation.setArticleId(articleId);
        relation.setOrderNo(orderNo != null && orderNo > 0 ? orderNo : nextOrder(seriesId));
        seriesArticleMapper.insert(relation);
        if ("published".equals(article.getStatus())) {
            notifySubscribers(series, article);
        }
        return Map.of("seriesId", seriesId, "articleId", articleId,
                "orderNo", relation.getOrderNo());
    }

    @Transactional
    public void removeArticle(Long seriesId, Long articleId) {
        Series series = mustGet(seriesId);
        requireAuthor(series);
        seriesArticleMapper.delete(Wrappers.<SeriesArticle>lambdaQuery()
                .eq(SeriesArticle::getSeriesId, seriesId)
                .eq(SeriesArticle::getArticleId, articleId));
    }

    /** 调整目录顺序：按传入的文章 id 顺序重排 */
    @Transactional
    public void reorder(Long seriesId, List<Long> articleIds) {
        Series series = mustGet(seriesId);
        requireAuthor(series);
        if (articleIds == null || articleIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "articleIds 不能为空");
        }
        List<SeriesArticle> relations = relations(seriesId);
        Set<Long> actual = relations.stream().map(SeriesArticle::getArticleId)
                .collect(Collectors.toSet());
        if (!actual.equals(new HashSet<>(articleIds))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文章列表与连载目录不一致");
        }
        for (int i = 0; i < articleIds.size(); i++) {
            int order = i + 1;
            seriesArticleMapper.update(null, Wrappers.<SeriesArticle>lambdaUpdate()
                    .eq(SeriesArticle::getSeriesId, seriesId)
                    .eq(SeriesArticle::getArticleId, articleIds.get(i))
                    .set(SeriesArticle::getOrderNo, order));
        }
    }

    /** 订阅连载 */
    public Map<String, Object> subscribe(Long seriesId) {
        Series series = mustGet(seriesId);
        Long userId = CurrentUser.id();
        if (!isSubscribed(seriesId, CurrentUser.get())) {
            SeriesSubscription subscription = new SeriesSubscription();
            subscription.setSeriesId(seriesId);
            subscription.setUserId(userId);
            try {
                subscriptionMapper.insert(subscription);
            } catch (DuplicateKeyException ignored) {
                // 并发重复订阅
            }
        }
        return Map.of("subscribed", true, "subscriberCount", subscriberCount(series.getId()));
    }

    public Map<String, Object> unsubscribe(Long seriesId) {
        Long userId = CurrentUser.id();
        subscriptionMapper.delete(Wrappers.<SeriesSubscription>lambdaQuery()
                .eq(SeriesSubscription::getSeriesId, seriesId)
                .eq(SeriesSubscription::getUserId, userId));
        return Map.of("subscribed", false, "subscriberCount", subscriberCount(seriesId));
    }

    /** 我订阅的连载 */
    public List<Map<String, Object>> subscribed() {
        Long userId = CurrentUser.id();
        List<SeriesSubscription> subscriptions = subscriptionMapper.selectList(
                Wrappers.<SeriesSubscription>lambdaQuery()
                        .eq(SeriesSubscription::getUserId, userId)
                        .orderByDesc(SeriesSubscription::getCreatedAt));
        if (subscriptions.isEmpty()) {
            return List.of();
        }
        List<Series> list = seriesMapper.selectBatchIds(subscriptions.stream()
                .map(SeriesSubscription::getSeriesId).toList());
        Map<Long, Series> byId = list.stream()
                .collect(Collectors.toMap(Series::getId, Function.identity()));
        return subscriptions.stream().map(s -> byId.get(s.getSeriesId()))
                .filter(Objects::nonNull)
                .map(s -> toItem(s, articleCount(s.getId())))
                .toList();
    }

    /** 文章所在的连载上下文（含上下篇），用于详情页跳转；不在连载中返回 null */
    public Map<String, Object> articleContext(Long articleId) {
        List<SeriesArticle> relations = seriesArticleMapper.selectList(
                Wrappers.<SeriesArticle>lambdaQuery()
                        .eq(SeriesArticle::getArticleId, articleId)
                        .orderByAsc(SeriesArticle::getCreatedAt));
        if (relations.isEmpty()) {
            return null;
        }
        Series series = seriesMapper.selectById(relations.get(0).getSeriesId());
        if (series == null) {
            return null;
        }
        List<SeriesArticle> siblings = relations(series.getId());
        List<Article> articles = loadArticles(siblings);
        Map<Long, Article> byId = articles.stream()
                .collect(Collectors.toMap(Article::getId, Function.identity()));
        int index = -1;
        for (int i = 0; i < siblings.size(); i++) {
            if (Objects.equals(siblings.get(i).getArticleId(), articleId)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return null;
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", series.getId());
        resp.put("title", series.getTitle());
        resp.put("status", series.getStatus());
        resp.put("index", index + 1);
        resp.put("total", siblings.size());
        resp.put("prev", neighbor(byId, siblings, index - 1));
        resp.put("next", neighbor(byId, siblings, index + 1));
        return resp;
    }

    /** 文章发布后：若在连载中则通知订阅者 */
    public void notifySeriesUpdate(Article article) {
        if (article == null || !"published".equals(article.getStatus())) {
            return;
        }
        List<SeriesArticle> relations = seriesArticleMapper.selectList(
                Wrappers.<SeriesArticle>lambdaQuery()
                        .eq(SeriesArticle::getArticleId, article.getId()));
        for (SeriesArticle relation : relations) {
            Series series = seriesMapper.selectById(relation.getSeriesId());
            if (series != null) {
                notifySubscribers(series, article);
            }
        }
    }

    private void notifySubscribers(Series series, Article article) {
        List<SeriesSubscription> subscribers = subscriptionMapper.selectList(
                Wrappers.<SeriesSubscription>lambdaQuery()
                        .eq(SeriesSubscription::getSeriesId, series.getId()));
        String content = "《" + series.getTitle() + "》更新：《" + article.getTitle() + "》";
        for (SeriesSubscription subscriber : subscribers) {
            notificationService.notify(subscriber.getUserId(), "series", article.getAuthorId(),
                    article.getId(), null, content);
        }
    }

    private Map<String, Object> neighbor(Map<Long, Article> byId,
                                         List<SeriesArticle> siblings, int index) {
        if (index < 0 || index >= siblings.size()) {
            return null;
        }
        Long articleId = siblings.get(index).getArticleId();
        Article article = byId.get(articleId);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", articleId);
        item.put("title", article == null ? null : article.getTitle());
        return item;
    }

    private List<SeriesArticle> relations(Long seriesId) {
        return seriesArticleMapper.selectList(Wrappers.<SeriesArticle>lambdaQuery()
                .eq(SeriesArticle::getSeriesId, seriesId)
                .orderByAsc(SeriesArticle::getOrderNo)
                .orderByAsc(SeriesArticle::getCreatedAt));
    }

    private List<Article> loadArticles(List<SeriesArticle> relations) {
        Set<Long> ids = relations.stream().map(SeriesArticle::getArticleId)
                .collect(Collectors.toSet());
        return ids.isEmpty() ? List.of() : articleMapper.selectBatchIds(ids);
    }

    private int nextOrder(Long seriesId) {
        List<SeriesArticle> relations = relations(seriesId);
        return relations.stream().map(SeriesArticle::getOrderNo)
                .filter(Objects::nonNull).max(Integer::compareTo).orElse(0) + 1;
    }

    private long articleCount(Long seriesId) {
        return seriesArticleMapper.selectCount(Wrappers.<SeriesArticle>lambdaQuery()
                .eq(SeriesArticle::getSeriesId, seriesId));
    }

    private long subscriberCount(Long seriesId) {
        return subscriptionMapper.selectCount(Wrappers.<SeriesSubscription>lambdaQuery()
                .eq(SeriesSubscription::getSeriesId, seriesId));
    }

    private boolean isSubscribed(Long seriesId, LoginUser current) {
        if (current == null) {
            return false;
        }
        return subscriptionMapper.selectCount(Wrappers.<SeriesSubscription>lambdaQuery()
                .eq(SeriesSubscription::getSeriesId, seriesId)
                .eq(SeriesSubscription::getUserId, current.getId())) > 0;
    }

    private Series mustGet(Long id) {
        Series series = seriesMapper.selectById(id);
        if (series == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "连载不存在");
        }
        return series;
    }

    private void requireAuthor(Series series) {
        if (!Objects.equals(CurrentUser.id(), series.getAuthorId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅作者可管理该连载");
        }
    }

    private static void requireTitle(String title) {
        if (!StringUtils.hasText(title)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "连载标题不能为空");
        }
        if (title.trim().length() > 120) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "连载标题最长 120 个字符");
        }
    }

    private static String trimToNull(String text, int max) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String value = text.trim();
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static Map<String, Object> toItem(Series series, long articleCount) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", series.getId());
        item.put("authorId", series.getAuthorId());
        item.put("title", series.getTitle());
        item.put("description", series.getDescription());
        item.put("status", series.getStatus());
        item.put("articleCount", articleCount);
        item.put("createdAt", series.getCreatedAt());
        item.put("updatedAt", series.getUpdatedAt());
        return item;
    }

    private static LoginUser currentUserOrNull() {
        try {
            return CurrentUser.get();
        } catch (BusinessException e) {
            return null;
        }
    }
}
