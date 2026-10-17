package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.dto.article.ArticleDetailResponse;
import com.aiblog.dto.article.ArticleItem;
import com.aiblog.dto.article.ArticlePageResponse;
import com.aiblog.dto.article.ArticleSaveRequest;
import com.aiblog.entity.Article;
import com.aiblog.entity.Category;
import com.aiblog.entity.User;
import com.aiblog.mapper.ArticleMapper;
import com.aiblog.mapper.UserMapper;
import com.aiblog.security.CurrentUser;
import com.aiblog.security.LoginUser;
import com.aiblog.util.HtmlSanitizer;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleMapper articleMapper;
    private final CategoryService categoryService;
    private final UserMapper userMapper;
    private final VectorSyncService vectorSyncService;

    /**
     * 公开文章列表：仅已发布，支持分类与关键词（命中标题或正文）。
     */
    public ArticlePageResponse pagePublished(int page, int size, Long categoryId, String keyword) {
        return doPage(Wrappers.<Article>lambdaQuery()
                        .eq(Article::getStatus, "published")
                        .eq(categoryId != null, Article::getCategoryId, categoryId)
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(Article::getTitle, keyword)
                                .or().like(Article::getContentHtml, keyword))
                        .orderByDesc(Article::getPublishedAt),
                page, size);
    }

    /**
     * 我的文章：含草稿，仅本人。
     */
    public ArticlePageResponse pageMine(int page, int size, String status) {
        return doPage(Wrappers.<Article>lambdaQuery()
                        .eq(Article::getAuthorId, CurrentUser.id())
                        .eq(StringUtils.hasText(status), Article::getStatus, status)
                        .orderByDesc(Article::getUpdatedAt),
                page, size);
    }

    private ArticlePageResponse doPage(
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Article> wrapper,
            int page, int size) {
        Page<Article> result = articleMapper.selectPage(Page.of(page, Math.min(size, 50)), wrapper);
        List<Article> records = result.getRecords();
        Map<Long, Category> categories = loadCategories(records);
        Map<Long, User> authors = loadAuthors(records);
        List<ArticleItem> items = records.stream()
                .map(a -> ArticleItem.of(a,
                        a.getCategoryId() != null && categories.containsKey(a.getCategoryId())
                                ? categories.get(a.getCategoryId()).getName() : null,
                        authors.get(a.getAuthorId())))
                .toList();
        return new ArticlePageResponse(result.getTotal(), items);
    }

    /**
     * 详情：已发布对所有人可读；草稿仅作者本人与管理员可见（越权返回 1003）。
     */
    public ArticleDetailResponse detail(Long id) {
        Article article = mustGet(id);
        LoginUser current = currentUserOrNull();
        boolean isAuthor = current != null && Objects.equals(current.getId(), article.getAuthorId());
        boolean isAdmin = current != null && current.isAdmin();
        if ("draft".equals(article.getStatus()) && !isAuthor && !isAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "草稿仅作者可见");
        }
        Category category = article.getCategoryId() == null
                ? null : categoryService.mustGetOrNull(article.getCategoryId());
        User author = userMapper.selectById(article.getAuthorId());
        return ArticleDetailResponse.of(article,
                category == null ? null : category.getName(), author);
    }

    public Map<String, Object> create(ArticleSaveRequest request) {
        validateStatus(request.getStatus());
        if (request.getCategoryId() != null) {
            categoryService.mustGet(request.getCategoryId());
        }
        Article article = new Article();
        applyRequest(article, request);
        article.setAuthorId(CurrentUser.id());
        if ("published".equals(request.getStatus())) {
            article.setPublishedAt(LocalDateTime.now());
        }
        articleMapper.insert(article);
        log.info("文章创建: id={}, author={}, status={}", article.getId(), article.getAuthorId(), article.getStatus());
        if ("published".equals(article.getStatus())) {
            vectorSyncService.upsert(article);
        }
        return Map.of("id", article.getId(), "status", article.getStatus());
    }

    public Map<String, Object> update(Long id, ArticleSaveRequest request) {
        Article article = mustGet(id);
        requireEditable(article);
        validateStatus(request.getStatus());
        if (request.getCategoryId() != null) {
            categoryService.mustGet(request.getCategoryId());
        }
        boolean wasPublished = "published".equals(article.getStatus());
        applyRequest(article, request);
        if ("published".equals(request.getStatus()) && article.getPublishedAt() == null) {
            article.setPublishedAt(LocalDateTime.now());
        }
        articleMapper.updateById(article);
        log.info("文章更新: id={}, status={}", article.getId(), article.getStatus());
        // 发布态才需要向量；撤回为草稿或编辑草稿则移除向量
        if ("published".equals(article.getStatus())) {
            vectorSyncService.upsert(article);
        } else if (wasPublished) {
            vectorSyncService.delete(article.getId());
        }
        return Map.of("id", article.getId(), "status", article.getStatus());
    }

    public void delete(Long id) {
        Article article = mustGet(id);
        requireEditable(article);
        articleMapper.deleteById(id);
        log.info("文章删除: id={}", article.getId());
        vectorSyncService.delete(id);
    }

    private void applyRequest(Article article, ArticleSaveRequest request) {
        article.setTitle(request.getTitle());
        article.setCategoryId(request.getCategoryId());
        article.setSummary(request.getSummary());
        article.setStatus(request.getStatus());
        // 富文本入库前强制白名单清洗，防 XSS（jsoup Safelist）
        article.setContentHtml(HtmlSanitizer.clean(request.getContentHtml()));
    }

    private void validateStatus(String status) {
        if (!"draft".equals(status) && !"published".equals(status)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "status 仅支持 draft / published");
        }
    }

    private void requireEditable(Article article) {
        LoginUser current = CurrentUser.get();
        if (!Objects.equals(current.getId(), article.getAuthorId()) && !current.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅作者或管理员可操作");
        }
    }

    private Article mustGet(Long id) {
        Article article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        return article;
    }

    private LoginUser currentUserOrNull() {
        try {
            return CurrentUser.get();
        } catch (BusinessException e) {
            return null;
        }
    }

    private Map<Long, Category> loadCategories(List<Article> articles) {
        Set<Long> ids = articles.stream().map(Article::getCategoryId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return categoryService.mustGetAll(ids).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
    }

    private Map<Long, User> loadAuthors(List<Article> articles) {
        Set<Long> ids = articles.stream().map(Article::getAuthorId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }
}
