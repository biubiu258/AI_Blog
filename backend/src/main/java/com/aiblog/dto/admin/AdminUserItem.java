package com.aiblog.dto.admin;

import com.aiblog.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端用户列表项：不暴露密码哈希。
 */
@Data
public class AdminUserItem {

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String role;
    private Integer status;
    private LocalDateTime createdAt;

    public static AdminUserItem from(User user) {
        AdminUserItem item = new AdminUserItem();
        item.setId(user.getId());
        item.setUsername(user.getUsername());
        item.setNickname(user.getNickname());
        item.setEmail(user.getEmail());
        item.setRole(user.getRole());
        item.setStatus(user.getStatus());
        item.setCreatedAt(user.getCreatedAt());
        return item;
    }
}
