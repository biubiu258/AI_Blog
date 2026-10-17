package com.aiblog.security;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从安全上下文取当前登录用户。
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static LoginUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }

    public static Long id() {
        return get().getId();
    }
}
