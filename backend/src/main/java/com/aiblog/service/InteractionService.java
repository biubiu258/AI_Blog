package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.entity.Article;
import com.aiblog.entity.ArticleFavorite;
import com.aiblog.entity.ArticleLike;
import com.aiblog.entity.Comment;
import com.aiblog.entity.User;
import com.aiblog.entity.UserFollow;
import com.aiblog.mapper.ArticleFavoriteMapper;
import com.aiblog.mapper.ArticleLikeMapper;
import com.aiblog.mapper.ArticleMapper;
import com.aiblog.mapper.CommentMapper;
import com.aiblog.mapper.UserFollowMapper;
import com.aiblog.mapper.UserMapper;
import com.aiblog.security.CurrentUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 互动：点赞、收藏、关注。切换语义（已存在则取消）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InteractionService {

    private final ArticleLikeMapper likeMapper;
    private final ArticleFavoriteMapper favoriteMapper;
    private final UserFollowMapper followMapper;
    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;
    private final CommentMapper commentMapper;
    private final NotificationService notificationService;

    public Map<String, Object> toggleLike(Long articleId) {
        Article article = mustGetPublicArticle(articleId);
        Long userId = CurrentUser.id();
        Long exists = likeMapper.selectCount(Wrappers.<ArticleLike>lambdaQuery()
                .eq(ArticleLike::getUserId, userId)
                .eq(ArticleLike::getArticleId, articleId));
        boolean liked;
        if (exists != null && exists > 0) {
            likeMapper.delete(Wrappers.<ArticleLike>lambdaQuery()
                    .eq(ArticleLike::getUserId, userId)
                    .eq(ArticleLike::getArticleId, articleId));
            liked = false;
        } else {
            ArticleLike like = new ArticleLike();
            like.setUserId(userId);
            like.setArticleId(articleId);
            try {
                likeMapper.insert(like);
            } catch (DuplicateKeyException ignored) {
                // 并发重复点赞按已点赞处理
            }
            liked = true;
            notificationService.notify(article.getAuthorId(), "like", userId, articleId, null,
                    "赞了你的文章《" + article.getTitle() + "》");
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("liked", liked);
        resp.put("likeCount", likeCount(articleId));
        return resp;
    }

    public Map<String, Object> toggleFavorite(Long articleId) {
        Article article = mustGetPublicArticle(articleId);
        Long userId = CurrentUser.id();
        Long exists = favoriteMapper.selectCount(Wrappers.<ArticleFavorite>lambdaQuery()
                .eq(ArticleFavorite::getUserId, userId)
                .eq(ArticleFavorite::getArticleId, articleId));
        boolean favorited;
        if (exists != null && exists > 0) {
            favoriteMapper.delete(Wrappers.<ArticleFavorite>lambdaQuery()
                    .eq(ArticleFavorite::getUserId, userId)
                    .eq(ArticleFavorite::getArticleId, articleId));
            favorited = false;
        } else {
            ArticleFavorite favorite = new ArticleFavorite();
            favorite.setUserId(userId);
            favorite.setArticleId(articleId);
            try {
                favoriteMapper.insert(favorite);
            } catch (DuplicateKeyException ignored) {
                // 并发重复收藏按已收藏处理
            }
            favorited = true;
            notificationService.notify(article.getAuthorId(), "favorite", userId, articleId, null,
                    "收藏了你的文章《" + article.getTitle() + "》");
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("favorited", favorited);
        resp.put("favoriteCount", favoriteCount(articleId));
        return resp;
    }

    public Map<String, Object> toggleFollow(Long targetUserId) {
        Long userId = CurrentUser.id();
        if (Objects.equals(userId, targetUserId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不能关注自己");
        }
        User target = userMapper.selectById(targetUserId);
        if (target == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        Long exists = followMapper.selectCount(Wrappers.<UserFollow>lambdaQuery()
                .eq(UserFollow::getFollowerId, userId)
                .eq(UserFollow::getFolloweeId, targetUserId));
        boolean following;
        if (exists != null && exists > 0) {
            followMapper.delete(Wrappers.<UserFollow>lambdaQuery()
                    .eq(UserFollow::getFollowerId, userId)
                    .eq(UserFollow::getFolloweeId, targetUserId));
            following = false;
        } else {
            UserFollow follow = new UserFollow();
            follow.setFollowerId(userId);
            follow.setFolloweeId(targetUserId);
            try {
                followMapper.insert(follow);
            } catch (DuplicateKeyException ignored) {
                // 并发重复关注按已关注处理
            }
            following = true;
            notificationService.notify(targetUserId, "follow", userId, null, null, "关注了你");
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("following", following);
        resp.put("followerCount", followerCount(targetUserId));
        return resp;
    }

    /** 文章详情所需的互动状态与计数；游客可用。 */
    public Map<String, Object> articleState(Article article) {
        Long articleId = article.getId();
        Long currentUserId = currentUserIdOrNull();
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("likeCount", likeCount(articleId));
        state.put("favoriteCount", favoriteCount(articleId));
        state.put("commentCount", commentMapper.selectCount(Wrappers.<Comment>lambdaQuery()
                .eq(Comment::getArticleId, articleId)
                .eq(Comment::getStatus, "approved")));
        state.put("followerCount", followerCount(article.getAuthorId()));
        boolean liked = false;
        boolean favorited = false;
        boolean following = false;
        if (currentUserId != null) {
            liked = likeMapper.selectCount(Wrappers.<ArticleLike>lambdaQuery()
                    .eq(ArticleLike::getUserId, currentUserId)
                    .eq(ArticleLike::getArticleId, articleId)) > 0;
            favorited = favoriteMapper.selectCount(Wrappers.<ArticleFavorite>lambdaQuery()
                    .eq(ArticleFavorite::getUserId, currentUserId)
                    .eq(ArticleFavorite::getArticleId, articleId)) > 0;
            if (!Objects.equals(currentUserId, article.getAuthorId())) {
                following = followMapper.selectCount(Wrappers.<UserFollow>lambdaQuery()
                        .eq(UserFollow::getFollowerId, currentUserId)
                        .eq(UserFollow::getFolloweeId, article.getAuthorId())) > 0;
            }
        }
        state.put("liked", liked);
        state.put("favorited", favorited);
        state.put("following", following);
        return state;
    }

    public long likeCount(Long articleId) {
        return likeMapper.selectCount(Wrappers.<ArticleLike>lambdaQuery()
                .eq(ArticleLike::getArticleId, articleId));
    }

    public long favoriteCount(Long articleId) {
        return favoriteMapper.selectCount(Wrappers.<ArticleFavorite>lambdaQuery()
                .eq(ArticleFavorite::getArticleId, articleId));
    }

    public long followerCount(Long userId) {
        return followMapper.selectCount(Wrappers.<UserFollow>lambdaQuery()
                .eq(UserFollow::getFolloweeId, userId));
    }

    public long followingCount(Long userId) {
        return followMapper.selectCount(Wrappers.<UserFollow>lambdaQuery()
                .eq(UserFollow::getFollowerId, userId));
    }

    public List<Long> followingIds(Long userId) {
        return followMapper.selectList(Wrappers.<UserFollow>lambdaQuery()
                        .eq(UserFollow::getFollowerId, userId))
                .stream().map(UserFollow::getFolloweeId).toList();
    }

    public List<Long> followerIds(Long userId) {
        return followMapper.selectList(Wrappers.<UserFollow>lambdaQuery()
                        .eq(UserFollow::getFolloweeId, userId))
                .stream().map(UserFollow::getFollowerId).toList();
    }

    public List<Long> favoriteArticleIds(Long userId) {
        return favoriteMapper.selectList(Wrappers.<ArticleFavorite>lambdaQuery()
                        .eq(ArticleFavorite::getUserId, userId)
                        .orderByDesc(ArticleFavorite::getCreatedAt))
                .stream().map(ArticleFavorite::getArticleId).toList();
    }

    private Article mustGetPublicArticle(Long articleId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null || !"published".equals(article.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        return article;
    }

    private static Long currentUserIdOrNull() {
        try {
            return CurrentUser.id();
        } catch (BusinessException e) {
            return null;
        }
    }
}
