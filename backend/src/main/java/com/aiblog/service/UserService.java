package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.dto.user.PreferenceRequest;
import com.aiblog.dto.user.UpdateUserRequest;
import com.aiblog.dto.auth.UserInfoResponse;
import com.aiblog.entity.User;
import com.aiblog.entity.UserPreference;
import com.aiblog.mapper.UserMapper;
import com.aiblog.mapper.UserPreferenceMapper;
import com.aiblog.security.CurrentUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final UserPreferenceMapper preferenceMapper;

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

    private User mustGet(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }
}
