package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.dto.admin.AdminCommentItem;
import com.aiblog.dto.comment.CommentItem;
import com.aiblog.entity.Article;
import com.aiblog.entity.Comment;
import com.aiblog.entity.User;
import com.aiblog.mapper.ArticleMapper;
import com.aiblog.mapper.CommentMapper;
import com.aiblog.mapper.UserMapper;
import com.aiblog.security.CurrentUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentMapper commentMapper;
    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    public List<CommentItem> listByArticle(Long articleId) {
        mustGetVisibleArticle(articleId);
        List<Comment> comments = commentMapper.selectList(Wrappers.<Comment>lambdaQuery()
                .eq(Comment::getArticleId, articleId)
                .eq(Comment::getStatus, "approved")
                .orderByAsc(Comment::getCreatedAt));
        Map<Long, User> authors = comments.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(comments.stream().map(Comment::getAuthorId)
                        .collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return comments.stream()
                .map(c -> CommentItem.of(c, authors.get(c.getAuthorId()),
                        c.getReplyToUserId() == null ? null : authors.get(c.getReplyToUserId())))
                .toList();
    }

    /**
     * 仅登录用户可评论，且只能评论已发布文章；parentId 用于两级回复。
     */
    public Map<String, Object> create(Long articleId, String content, Long parentId) {
        Article article = mustGetVisibleArticle(articleId);
        if (!"published".equals(article.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "文章未发布，无法评论");
        }
        Long authorId = CurrentUser.id();
        Long rootId = null;
        Long replyToUserId = null;
        if (parentId != null) {
            Comment parent = commentMapper.selectById(parentId);
            if (parent == null || !Objects.equals(parent.getArticleId(), articleId)
                    || !"approved".equals(parent.getStatus())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "被回复的评论不存在");
            }
            // 两级结构：回复的回复仍挂在一级评论下
            rootId = parent.getParentId() == null ? parent.getId() : parent.getParentId();
            replyToUserId = parent.getAuthorId();
        }
        Comment comment = new Comment();
        comment.setArticleId(articleId);
        comment.setAuthorId(authorId);
        comment.setContent(content.strip());
        comment.setParentId(rootId);
        comment.setReplyToUserId(replyToUserId);
        comment.setStatus("approved");
        commentMapper.insert(comment);

        if (replyToUserId != null) {
            notificationService.notify(replyToUserId, "reply", authorId, articleId, comment.getId(),
                    "回复了你的评论：" + abbreviate(comment.getContent()));
        }
        if (!Objects.equals(article.getAuthorId(), authorId)
                && !Objects.equals(article.getAuthorId(), replyToUserId)) {
            notificationService.notifyComment(article, authorId, comment.getId(), comment.getContent());
        }
        return Map.of("id", comment.getId());
    }

    private static String abbreviate(String text) {
        return text.length() <= 80 ? text : text.substring(0, 80) + "…";
    }

    /**
     * 评论作者本人或管理员可删除。
     */
    public void delete(Long id) {
        Comment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "评论不存在");
        }
        Long currentId = CurrentUser.id();
        if (!Objects.equals(comment.getAuthorId(), currentId) && !CurrentUser.get().isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅评论作者或管理员可删除");
        }
        commentMapper.deleteById(id);
    }

    /** 管理端全站评论分页，支持关键词（命中评论内容） */
    public Map<String, Object> adminPage(int page, int size, String keyword) {
        Page<Comment> result = commentMapper.selectPage(Page.of(page, Math.min(size, 50)),
                Wrappers.<Comment>lambdaQuery()
                        .like(StringUtils.hasText(keyword), Comment::getContent, keyword)
                        .orderByDesc(Comment::getCreatedAt));
        List<Comment> records = result.getRecords();
        Map<Long, User> authors = records.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(records.stream().map(Comment::getAuthorId)
                        .collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Article> articles = records.isEmpty() ? Map.of()
                : articleMapper.selectBatchIds(records.stream().map(Comment::getArticleId)
                        .collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Article::getId, Function.identity()));
        List<AdminCommentItem> items = records.stream()
                .map(c -> AdminCommentItem.of(c,
                        authors.get(c.getAuthorId()),
                        articles.get(c.getArticleId())))
                .toList();
        return Map.of("total", result.getTotal(), "items", items);
    }

    /** 管理端批量删除评论（逻辑删除），返回实际删除条数 */
    public int adminBatchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "ids 不能为空");
        }
        List<Comment> comments = commentMapper.selectBatchIds(ids);
        if (comments.isEmpty()) {
            return 0;
        }
        int n = commentMapper.deleteBatchIds(comments.stream().map(Comment::getId).toList());
        log.info("管理端批量删除评论: count={}", n);
        return n;
    }

    /**
     * 草稿的评论仅作者/管理员可见；私密文章（风控）仅作者可见，他人与游客一律 404。
     */
    private Article mustGetVisibleArticle(Long articleId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        if ("draft".equals(article.getStatus())) {
            Long currentId;
            try {
                currentId = CurrentUser.id();
            } catch (BusinessException e) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "草稿仅作者可见");
            }
            if (!Objects.equals(article.getAuthorId(), currentId) && !CurrentUser.get().isAdmin()) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "草稿仅作者可见");
            }
        }
        if ("private".equals(article.getStatus())) {
            Long currentId;
            try {
                currentId = CurrentUser.id();
            } catch (BusinessException e) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "文章不存在");
            }
            if (!Objects.equals(article.getAuthorId(), currentId)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "文章不存在");
            }
        }
        return article;
    }
}
