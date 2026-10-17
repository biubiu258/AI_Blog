package com.aiblog.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 登录用户主体：携带数据库中的 id 与角色，供业务层取当前用户。
 */
@Getter
public class LoginUser implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final String role;
    private final Integer status;

    public LoginUser(Long id, String username, String password, String role, Integer status) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
        this.status = status;
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    /** 内容管理员或超级管理员：可进入内容管理类接口 */
    public boolean isContentAdmin() {
        return "ADMIN".equals(role) || "CONTENT_ADMIN".equals(role);
    }

    public boolean isBanned() {
        return status != null && status == 1;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !isBanned();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return !isBanned();
    }
}
