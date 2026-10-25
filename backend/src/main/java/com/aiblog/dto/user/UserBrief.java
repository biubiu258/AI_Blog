package com.aiblog.dto.user;

import com.aiblog.entity.User;
import lombok.Data;

/**
 * 公开用户摘要：不含邮箱等隐私字段。
 */
@Data
public class UserBrief {

    private Long id;
    private String nickname;
    private String avatarUrl;
    private String bio;

    public static UserBrief from(User user) {
        UserBrief brief = new UserBrief();
        brief.setId(user.getId());
        brief.setNickname(user.getNickname());
        brief.setAvatarUrl(user.getAvatarUrl());
        brief.setBio(user.getBio());
        return brief;
    }
}
