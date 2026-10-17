package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.dto.comment.CommentItem;
import com.aiblog.entity.Article;
import com.aiblog.entity.Comment;
import com.aiblog.entity.User;
import com.aiblog.mapper.ArticleMapper;
import com.aiblog.mapper.CommentMapper;
import com.aiblog.mapper.UserMapper;
import com.aiblog.security.CurrentUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentMapper commentMapper;
    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;

    public List<CommentItem> listByArticle(Long articleId) {
        mustGetVisibleArticle(articleId);
        List<Comment> comments = commentMapper.selectList(Wrappers.<Comment>lambdaQuery()
                .eq(Comment::getArticleId, articleId)
                .orderByDesc(Comment::getCreatedAt));
        Map<Long, User> authors = comments.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(comments.stream().map(Comment::getAuthorId)
                        .collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return comments.stream()
                .map(c -> CommentItem.of(c, authors.get(c.getAuthorId())))
                .toList();
    }

    /**
     * 仅登录用户可评论，且只能评论已发布文章。
     */
    public Map<String, Object> create(Long articleId, String content) {
        Article article = mustGetVisibleArticle(articleId);
        if (!"published".equals(article.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "文章未发布，无法评论");
        }
        Comment comment = new Comment();
        comment.setArticleId(articleId);
        comment.setAuthorId(CurrentUser.id());
        comment.setContent(content.strip());
        commentMapper.insert(comment);
        return Map.of("id", comment.getId());
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

    /**
     * 草稿的评论仅作者/管理员可见，其余情况要求文章存在。
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
        return article;
    }
}
