package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.dto.auth.LoginRequest;
import com.aiblog.dto.auth.LoginResponse;
import com.aiblog.dto.auth.RegisterRequest;
import com.aiblog.dto.auth.UserInfoResponse;
import com.aiblog.entity.User;
import com.aiblog.mapper.UserMapper;
import com.aiblog.security.CaptchaStore;
import com.aiblog.security.JwtTokenProvider;
import com.aiblog.security.LoginRateLimiter;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final LoginRateLimiter rateLimiter;
    private final CaptchaStore captchaStore;

    public UserInfoResponse register(RegisterRequest request) {
        // 应用层与数据库唯一约束双重校验，避免并发下重复注册
        Long exists = userMapper.selectCount(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, request.getUsername()));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已被占用");
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() == null || request.getNickname().isBlank()
                ? request.getUsername() : request.getNickname());
        user.setEmail(request.getEmail());
        user.setRole("USER");
        user.setStatus(0);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已被占用");
        }
        log.info("新用户注册: id={}, username={}", user.getId(), user.getUsername());
        return UserInfoResponse.from(user);
    }

    public LoginResponse login(LoginRequest request, String clientIp) {
        // 风控第一道：锁定期直接拒绝
        rateLimiter.checkLocked(request.getUsername(), clientIp);
        // 风控第二道：图形验证码（一次性，错也对都作废）
        if (!captchaStore.verify(request.getCaptchaId(), request.getCaptchaCode())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "验证码错误或已过期");
        }
        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, request.getUsername()));
        // 统一提示，不泄露“用户是否存在”
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            rateLimiter.recordFail(request.getUsername(), clientIp);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录名或密码错误");
        }
        if (user.isBanActive()) {
            String until = user.getBannedUntil() == null
                    ? "" : "，封禁至 " + user.getBannedUntil().toLocalDate() + " " + user.getBannedUntil().toLocalTime();
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "账号已被封禁" + until + (user.getBanReason() == null ? "" : "：" + user.getBanReason()));
        }
        lazyUnlockIfNeeded(user);
        rateLimiter.reset(request.getUsername(), clientIp);
        String token = tokenProvider.generate(user.getId(), user.getUsername(), user.getRole(),
                user.getTokenVersion());
        return new LoginResponse(token, UserInfoResponse.from(user));
    }

    /** 封禁已到期但未回正的账号：惰性解封（与 JwtAuthenticationFilter 的行为一致） */
    private void lazyUnlockIfNeeded(User user) {
        if (user.getStatus() != null && user.getStatus() == 1) {
            user.setStatus(0);
            user.setBanReason(null);
            user.setBannedUntil(null);
            userMapper.updateById(user);
            log.info("账号封禁到期自动解封: id={}", user.getId());
        }
    }
}
