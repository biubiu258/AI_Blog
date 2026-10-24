package com.aiblog.security;

import com.aiblog.entity.User;
import com.aiblog.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 从 Authorization: Bearer <jwt> 解析用户，加载最新资料（含封禁状态）后写入安全上下文。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserMapper userMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = tokenProvider.parse(token);
                Long uid = claims.get("uid", Long.class);
                Integer tv = claims.get("tv", Integer.class);
                if (uid != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    User user = userMapper.selectById(uid);
                    // tokenVersion 不一致说明密码已重置/被强制下线，令牌立即失效
                    boolean tokenValid = user != null && (user.getTokenVersion() == null
                            ? 0 : user.getTokenVersion()) == (tv == null ? 0 : tv);
                    if (tokenValid && !user.isBanActive()) {
                        if (user.getStatus() != null && user.getStatus() == 1) {
                            // 封禁期限已过：惰性解封，回正状态
                            user.setStatus(0);
                            user.setBanReason(null);
                            user.setBannedUntil(null);
                            userMapper.updateById(user);
                        }
                        UserDetails principal = new LoginUser(user.getId(), user.getUsername(),
                                user.getPasswordHash(), user.getRole(), user.getStatus());
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities());
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
            } catch (Exception ignored) {
                // 无效令牌一律按未认证处理，由入口点统一返回 1002
            }
        }
        chain.doFilter(request, response);
    }
}
