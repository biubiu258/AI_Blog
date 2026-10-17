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
import java.util.LinkedHashMap;
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
    private final CareService careService;

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
        // 私密发布（风控检测 high）：仅作者本人可见，他人与游客一律 404，不泄露存在性
        if ("private".equals(article.getStatus()) && !isAuthor) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文章不存在");
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
        // 检测只挂在发布动作上（契约 5.8），存草稿不检测
        boolean publishing = "published".equals(request.getStatus());
        CareService.CareResult care = publishing
                ? careService.check(CurrentUser.id(), request.getTitle(), request.getContentHtml())
                : CareService.CareResult.none();
        Article article = new Article();
        applyRequest(article, request);
        // 危机倾向：照常发布但仅作者可见（publishedAt 照设、不进公共语义检索），发布流程零打断
        if (publishing && care.high()) {
            article.setStatus("private");
        }
        article.setAuthorId(CurrentUser.id());
        if (publishing) {
            article.setPublishedAt(LocalDateTime.now());
        }
        articleMapper.insert(article);
        log.info("文章创建: id={}, author={}, status={}", article.getId(), article.getAuthorId(), article.getStatus());
        if ("published".equals(article.getStatus())) {
            vectorSyncService.upsert(article);
        }
        careService.record(article.getAuthorId(), article.getId(), care.level());
        return buildSaveResponse(article, care);
    }

    public Map<String, Object> update(Long id, ArticleSaveRequest request) {
        Article article = mustGet(id);
        requireEditable(article);
        validateStatus(request.getStatus());
        if (request.getCategoryId() != null) {
            categoryService.mustGet(request.getCategoryId());
        }
        boolean wasPublished = "published".equals(article.getStatus());
        boolean publishing = "published".equals(request.getStatus());
        // 再次发布 = 重新检测：内容修改后 level 降级则自动转回公开
        CareService.CareResult care = publishing
                ? careService.check(CurrentUser.id(), request.getTitle(), request.getContentHtml())
                : CareService.CareResult.none();
        applyRequest(article, request);
        if (publishing && care.high()) {
            article.setStatus("private");
        }
        if (publishing && article.getPublishedAt() == null) {
            article.setPublishedAt(LocalDateTime.now());
        }
        articleMapper.updateById(article);
        log.info("文章更新: id={}, status={}", article.getId(), article.getStatus());
        // 发布态才需要向量；转私密或撤回为草稿则移除向量
        if ("published".equals(article.getStatus())) {
            vectorSyncService.upsert(article);
        } else if (wasPublished) {
            vectorSyncService.delete(article.getId());
        }
        careService.record(article.getAuthorId(), article.getId(), care.level());
        return buildSaveResponse(article, care);
    }

    /** 保存响应：care 仅在 level≠none 时携带（high 时 status 已转 private） */
    private Map<String, Object> buildSaveResponse(Article article, CareService.CareResult care) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", article.getId());
        resp.put("status", article.getStatus());
        if (care.present()) {
            resp.put("care", Map.of(
                    "level", care.level(),
                    "summary", care.summary() == null ? "" : care.summary()));
        }
        return resp;
    }

    public void delete(Long id) {
        Article article = mustGet(id);
        requireEditable(article);
        articleMapper.deleteById(id);
        log.info("文章删除: id={}", article.getId());
        vectorSyncService.delete(id);
    }

    /** 管理端全站文章分页（含草稿），支持状态与关键词筛选 */
    public ArticlePageResponse adminPage(int page, int size, String status, String keyword) {
        return doPage(Wrappers.<Article>lambdaQuery()
                        .eq(StringUtils.hasText(status), Article::getStatus, status)
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(Article::getTitle, keyword)
                                .or().like(Article::getContentHtml, keyword))
                        .orderByDesc(Article::getUpdatedAt),
                page, size);
    }

    /** 管理端批量下架（published -> draft），返回实际处理条数；草稿跳过 */
    public int adminBatchUnpublish(List<Long> ids) {
        requireIds(ids);
        List<Article> articles = articleMapper.selectBatchIds(ids);
        List<Article> published = articles.stream()
                .filter(a -> "published".equals(a.getStatus()))
                .toList();
        for (Article a : published) {
            a.setStatus("draft");
            articleMapper.updateById(a);
            vectorSyncService.delete(a.getId());
        }
        log.info("管理端批量下架: count={}", published.size());
        return published.size();
    }

    /** 管理端批量删除（逻辑删除），返回实际删除条数 */
    public int adminBatchDelete(List<Long> ids) {
        requireIds(ids);
        List<Article> articles = articleMapper.selectBatchIds(ids);
        if (articles.isEmpty()) {
            return 0;
        }
        int n = articleMapper.deleteBatchIds(articles.stream().map(Article::getId).toList());
        articles.forEach(a -> vectorSyncService.delete(a.getId()));
        log.info("管理端批量删除文章: count={}", n);
        return n;
    }

    private void requireIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "ids 不能为空");
        }
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
