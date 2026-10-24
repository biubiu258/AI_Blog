package com.aiblog.dto.auth;

import com.aiblog.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserInfoResponse {

    private Long id;
    private String username;
    private String nickname;
    private String avatarUrl;
    private String bio;
    private String email;
    private Integer emailVerified;
    private String role;
    private LocalDateTime createdAt;

    public static UserInfoResponse from(User user) {
        UserInfoResponse r = new UserInfoResponse();
        r.setId(user.getId());
        r.setUsername(user.getUsername());
        r.setNickname(user.getNickname());
        r.setAvatarUrl(user.getAvatarUrl());
        r.setBio(user.getBio());
        r.setEmail(user.getEmail());
        r.setEmailVerified(user.getEmailVerified() == null ? 0 : user.getEmailVerified());
        r.setRole(user.getRole());
        r.setCreatedAt(user.getCreatedAt());
        return r;
    }
}
