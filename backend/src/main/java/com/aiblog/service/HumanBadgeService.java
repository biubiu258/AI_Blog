package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.entity.Article;
import com.aiblog.entity.HumanBadgeApplication;
import com.aiblog.entity.User;
import com.aiblog.mapper.ArticleMapper;
import com.aiblog.mapper.HumanBadgeApplicationMapper;
import com.aiblog.mapper.UserMapper;
import com.aiblog.security.CurrentUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 人类创作徽章：作者以连续 none 声明的已发布文章申请，管理员抽审后授予。
 * 授予后同步点亮该作者全部 none 声明的已发布文章徽章；拒绝时向用户展示理由。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HumanBadgeService {

    /** 申请门槛：至少 3 篇已发布且声明为 none 的文章 */
    public static final int MIN_REQUIRED_ARTICLES = 3;

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_PASSED = "passed";
    public static final String STATUS_REJECTED = "rejected";

    private final HumanBadgeApplicationMapper applicationMapper;
    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    /** 提交申请：自动收集本人符合条件文章；已有待审核申请时不允许重复提交 */
    @Transactional
    public Map<String, Object> apply() {
        Long userId = CurrentUser.id();
        Long pending = applicationMapper.selectCount(Wrappers.<HumanBadgeApplication>lambdaQuery()
                .eq(HumanBadgeApplication::getUserId, userId)
                .eq(HumanBadgeApplication::getStatus, STATUS_PENDING));
        if (pending > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "已有待审核的申请，请等待审核结果");
        }
        List<Long> eligibleIds = eligibleArticleIds(userId);
        if (eligibleIds.size() < MIN_REQUIRED_ARTICLES) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "需至少 " + MIN_REQUIRED_ARTICLES + " 篇声明为人类创作的已发布文章");
        }
        HumanBadgeApplication application = new HumanBadgeApplication();
        application.setUserId(userId);
        application.setArticleIds(writeJson(eligibleIds));
        application.setStatus(STATUS_PENDING);
        application.setCreatedAt(LocalDateTime.now());
        applicationMapper.insert(application);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", application.getId());
        resp.put("status", STATUS_PENDING);
        resp.put("articleIds", eligibleIds);
        return resp;
    }

    /** 我的申请记录（含拒绝理由） */
    public List<Map<String, Object>> mine() {
        List<HumanBadgeApplication> list = applicationMapper.selectList(
                Wrappers.<HumanBadgeApplication>lambdaQuery()
                        .eq(HumanBadgeApplication::getUserId, CurrentUser.id())
                        .orderByDesc(HumanBadgeApplication::getId));
        List<Map<String, Object>> items = new ArrayList<>();
        for (HumanBadgeApplication a : list) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", a.getId());
            item.put("status", a.getStatus());
            item.put("note", a.getNote());
            item.put("articleCount", parseIds(a.getArticleIds()).size());
            item.put("createdAt", a.getCreatedAt());
            item.put("reviewedAt", a.getReviewedAt());
            items.add(item);
        }
        return items;
    }

    /** 管理端分页：按状态筛选，带申请人昵称 */
    public Map<String, Object> adminPage(int page, int size, String status) {
        Page<HumanBadgeApplication> result = applicationMapper.selectPage(
                Page.of(page, Math.min(size, 50)),
                Wrappers.<HumanBadgeApplication>lambdaQuery()
                        .eq(StringUtils.hasText(status), HumanBadgeApplication::getStatus, status)
                        .orderByDesc(HumanBadgeApplication::getId));
        List<Map<String, Object>> items = new ArrayList<>();
        for (HumanBadgeApplication a : result.getRecords()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", a.getId());
            item.put("userId", a.getUserId());
            User user = userMapper.selectById(a.getUserId());
            item.put("userNickname", user == null ? null : user.getNickname());
            item.put("articleIds", parseIds(a.getArticleIds()));
            item.put("status", a.getStatus());
            item.put("note", a.getNote());
            item.put("createdAt", a.getCreatedAt());
            item.put("reviewedAt", a.getReviewedAt());
            items.add(item);
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("total", result.getTotal());
        resp.put("items", items);
        return resp;
    }

    /** 审核通过与拒绝；通过时点亮申请人全部符合条件的文章徽章 */
    @Transactional
    public Map<String, Object> review(Long id, String status, String note) {
        if (!STATUS_PASSED.equals(status) && !STATUS_REJECTED.equals(status)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "审核结果仅支持 passed / rejected");
        }
        if (STATUS_REJECTED.equals(status) && !StringUtils.hasText(note)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "拒绝时必须填写理由，将向申请人展示");
        }
        HumanBadgeApplication application = applicationMapper.selectById(id);
        if (application == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "申请不存在");
        }
        if (!STATUS_PENDING.equals(application.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该申请已审核完成");
        }
        application.setStatus(status);
        application.setNote(note);
        application.setReviewerId(CurrentUser.id());
        application.setReviewedAt(LocalDateTime.now());
        applicationMapper.updateById(application);

        int badgedArticles = 0;
        if (STATUS_PASSED.equals(status)) {
            List<Long> articleIds = eligibleArticleIds(application.getUserId());
            for (Long articleId : articleIds) {
                Article article = articleMapper.selectById(articleId);
                if (article != null
                        && (article.getHumanBadge() == null || article.getHumanBadge() == 0)) {
                    Article patch = new Article();
                    patch.setId(articleId);
                    patch.setHumanBadge(1);
                    articleMapper.updateById(patch);
                    badgedArticles++;
                }
            }
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", id);
        resp.put("status", status);
        resp.put("badgedArticles", badgedArticles);
        return resp;
    }

    /** 作者是否持有徽章：存在任一通过的申请即视为持有（个人主页展示用） */
    public boolean hasBadge(Long userId) {
        return applicationMapper.selectCount(Wrappers.<HumanBadgeApplication>lambdaQuery()
                .eq(HumanBadgeApplication::getUserId, userId)
                .eq(HumanBadgeApplication::getStatus, STATUS_PASSED)) > 0;
    }

    /** 符合条件的文章：本人已发布且 AI 声明为 none */
    private List<Long> eligibleArticleIds(Long userId) {
        return articleMapper.selectList(Wrappers.<Article>lambdaQuery()
                        .eq(Article::getAuthorId, userId)
                        .eq(Article::getStatus, "published")
                        .eq(Article::getAiUsageLevel, AiDisclosureService.LEVEL_NONE)
                        .orderByDesc(Article::getPublishedAt))
                .stream().map(Article::getId).toList();
    }

    private List<Long> parseIds(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            log.warn("徽章申请文章列表解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    private String writeJson(List<Long> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (Exception e) {
            return "[]";
        }
    }
}
