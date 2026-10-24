package com.aiblog.service;

import com.aiblog.entity.ArticleTag;
import com.aiblog.entity.Tag;
import com.aiblog.mapper.ArticleTagMapper;
import com.aiblog.mapper.TagMapper;
import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 标签：文章保存时按名称解析或新建，列表按标签筛选。
 */
@Service
@RequiredArgsConstructor
public class TagService {

    private static final int MAX_TAGS = 8;
    private static final int MAX_NAME_LENGTH = 32;

    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;

    public List<Tag> list() {
        return tagMapper.selectList(Wrappers.<Tag>lambdaQuery().orderByAsc(Tag::getName));
    }

    @Transactional
    public void setArticleTags(Long articleId, List<String> names) {
        List<String> normalized = normalize(names);
        articleTagMapper.delete(Wrappers.<ArticleTag>lambdaQuery()
                .eq(ArticleTag::getArticleId, articleId));
        for (String name : normalized) {
            Tag tag = findOrCreate(name);
            ArticleTag relation = new ArticleTag();
            relation.setArticleId(articleId);
            relation.setTagId(tag.getId());
            try {
                articleTagMapper.insert(relation);
            } catch (DuplicateKeyException ignored) {
                // 并发下重复插入忽略
            }
        }
    }

    public Map<Long, List<String>> tagsOfArticles(Collection<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return Map.of();
        }
        List<ArticleTag> relations = articleTagMapper.selectList(Wrappers.<ArticleTag>lambdaQuery()
                .in(ArticleTag::getArticleId, articleIds));
        if (relations.isEmpty()) {
            return Map.of();
        }
        Map<Long, Tag> tags = tagMapper.selectBatchIds(relations.stream()
                        .map(ArticleTag::getTagId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Tag::getId, t -> t));
        Map<Long, List<String>> result = new HashMap<>();
        for (ArticleTag relation : relations) {
            Tag tag = tags.get(relation.getTagId());
            if (tag != null) {
                result.computeIfAbsent(relation.getArticleId(), k -> new ArrayList<>())
                        .add(tag.getName());
            }
        }
        return result;
    }

    public List<String> tagsOfArticle(Long articleId) {
        return tagsOfArticles(List.of(articleId)).getOrDefault(articleId, List.of());
    }

    public List<Long> articleIdsByTag(Long tagId) {
        return articleTagMapper.selectList(Wrappers.<ArticleTag>lambdaQuery()
                        .eq(ArticleTag::getTagId, tagId))
                .stream().map(ArticleTag::getArticleId).toList();
    }

    private Tag findOrCreate(String name) {
        Tag exists = tagMapper.selectOne(Wrappers.<Tag>lambdaQuery().eq(Tag::getName, name));
        if (exists != null) {
            return exists;
        }
        Tag tag = new Tag();
        tag.setName(name);
        try {
            tagMapper.insert(tag);
            return tag;
        } catch (DuplicateKeyException e) {
            Tag concurrent = tagMapper.selectOne(Wrappers.<Tag>lambdaQuery().eq(Tag::getName, name));
            if (concurrent == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "标签创建失败");
            }
            return concurrent;
        }
    }

    private static List<String> normalize(List<String> names) {
        if (names == null) {
            return List.of();
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String raw : names) {
            if (raw == null) {
                continue;
            }
            String name = raw.trim();
            if (name.isEmpty()) {
                continue;
            }
            if (name.length() > MAX_NAME_LENGTH) {
                name = name.substring(0, MAX_NAME_LENGTH);
            }
            distinct.add(name);
            if (distinct.size() >= MAX_TAGS) {
                break;
            }
        }
        return new ArrayList<>(distinct);
    }
}
