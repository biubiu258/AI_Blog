package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.dto.user.PreferenceRequest;
import com.aiblog.dto.user.UpdateUserRequest;
import com.aiblog.dto.user.UserBrief;
import com.aiblog.dto.auth.UserInfoResponse;
import com.aiblog.entity.Article;
import com.aiblog.entity.User;
import com.aiblog.entity.UserPreference;
import com.aiblog.mapper.ArticleMapper;
import com.aiblog.mapper.UserMapper;
import com.aiblog.mapper.UserPreferenceMapper;
import com.aiblog.security.CurrentUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final UserPreferenceMapper preferenceMapper;
    private final ArticleMapper articleMapper;
    private final InteractionService interactionService;

    public UserInfoResponse me() {
        User user = mustGet(CurrentUser.id());
        return UserInfoResponse.from(user);
    }

    public UserInfoResponse updateMe(UpdateUserRequest request) {
        User user = mustGet(CurrentUser.id());
        if (request.getNickname() != null) user.setNickname(request.getNickname());
        if (request.getBio() != null) user.setBio(request.getBio());
        if (request.getAvatarUrl() != null) user.setAvatarUrl(request.getAvatarUrl());
        if (request.getEmail() != null) user.setEmail(request.getEmail());
        userMapper.updateById(user);
        return UserInfoResponse.from(user);
    }

    public PreferenceRequest getMyPreference() {
        UserPreference pref = preferenceMapper.selectOne(Wrappers.<UserPreference>lambdaQuery()
                .eq(UserPreference::getUserId, CurrentUser.id()));
        return PreferenceRequest.from(pref);
    }

    public PreferenceRequest updateMyPreference(PreferenceRequest request) {
        UserPreference pref = preferenceMapper.selectOne(Wrappers.<UserPreference>lambdaQuery()
                .eq(UserPreference::getUserId, CurrentUser.id()));
        if (pref == null) {
            pref = new UserPreference();
            pref.setUserId(CurrentUser.id());
        }
        pref.setTone(request.getTone());
        pref.setTargetReader(request.getTargetReader());
        pref.setStructure(request.getStructure());
        pref.setExtra(request.getExtra());
        if (pref.getId() == null) {
            preferenceMapper.insert(pref);
        } else {
            preferenceMapper.updateById(pref);
        }
        return PreferenceRequest.from(pref);
    }

    /** 公开个人主页：不含邮箱，附关注/粉丝/发文数与当前用户关注状态 */
    public Map<String, Object> profile(Long id) {
        User user = mustGet(id);
        Long currentUserId = currentUserIdOrNull();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", user.getId());
        resp.put("nickname", user.getNickname());
        resp.put("avatarUrl", user.getAvatarUrl());
        resp.put("bio", user.getBio());
        resp.put("createdAt", user.getCreatedAt());
        resp.put("followerCount", interactionService.followerCount(id));
        resp.put("followingCount", interactionService.followingCount(id));
        resp.put("articleCount", articleMapper.selectCount(Wrappers.<Article>lambdaQuery()
                .eq(Article::getAuthorId, id)
                .eq(Article::getStatus, "published")));
        resp.put("following", currentUserId != null && !Objects.equals(currentUserId, id)
                && interactionService.followingIds(currentUserId).contains(id));
        return resp;
    }

    public List<UserBrief> followers(Long id) {
        mustGet(id);
        return toBriefs(interactionService.followerIds(id));
    }

    public List<UserBrief> following(Long id) {
        mustGet(id);
        return toBriefs(interactionService.followingIds(id));
    }

    private List<UserBrief> toBriefs(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return userMapper.selectBatchIds(ids).stream().map(UserBrief::from).toList();
    }

    private static Long currentUserIdOrNull() {
        try {
            return CurrentUser.id();
        } catch (BusinessException e) {
            return null;
        }
    }

    private User mustGet(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }
}
