package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.entity.Article;
import com.aiblog.entity.ArticleVersion;
import com.aiblog.mapper.ArticleMapper;
import com.aiblog.mapper.ArticleVersionMapper;
import com.aiblog.security.CurrentUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 文章版本历史：更新前保存快照，最多保留 30 个版本。
 */
@Service
@RequiredArgsConstructor
public class ArticleVersionService {

    private static final int MAX_VERSIONS = 30;

    private final ArticleVersionMapper versionMapper;
    private final ArticleMapper articleMapper;

    public void snapshot(Article article, Long editorId) {
        if (article == null || article.getId() == null) {
            return;
        }
        ArticleVersion version = new ArticleVersion();
        version.setArticleId(article.getId());
        version.setTitle(article.getTitle());
        version.setContentHtml(article.getContentHtml());
        version.setSummary(article.getSummary());
        version.setSavedBy(editorId);
        versionMapper.insert(version);
        prune(article.getId());
    }

    public List<ArticleVersion> list(Long articleId) {
        requireOwnerOrAdmin(articleId);
        return versionMapper.selectList(Wrappers.<ArticleVersion>lambdaQuery()
                        .eq(ArticleVersion::getArticleId, articleId)
                        .orderByDesc(ArticleVersion::getSavedAt))
                .stream()
                // 列表不返回正文，避免响应过大
                .peek(v -> v.setContentHtml(null))
                .toList();
    }

    public ArticleVersion mustGetVersion(Long articleId, Long versionId) {
        requireOwnerOrAdmin(articleId);
        ArticleVersion version = versionMapper.selectById(versionId);
        if (version == null || !Objects.equals(version.getArticleId(), articleId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "版本不存在");
        }
        return version;
    }

    private void prune(Long articleId) {
        List<ArticleVersion> all = versionMapper.selectList(Wrappers.<ArticleVersion>lambdaQuery()
                .eq(ArticleVersion::getArticleId, articleId)
                .orderByDesc(ArticleVersion::getSavedAt));
        if (all.size() <= MAX_VERSIONS) {
            return;
        }
        for (ArticleVersion v : all.subList(MAX_VERSIONS, all.size())) {
            versionMapper.deleteById(v.getId());
        }
    }

    private void requireOwnerOrAdmin(Long articleId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        var current = CurrentUser.get();
        if (!Objects.equals(current.getId(), article.getAuthorId()) && !current.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅作者或管理员可查看版本");
        }
    }
}
