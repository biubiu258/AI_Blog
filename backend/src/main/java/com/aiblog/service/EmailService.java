package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.entity.EmailToken;
import com.aiblog.entity.User;
import com.aiblog.mapper.EmailTokenMapper;
import com.aiblog.mapper.UserMapper;
import com.aiblog.security.CurrentUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 邮箱验证与找回密码：
 * - 验证码 10 分钟有效，同一邮箱 10 分钟内最多 5 次；
 * - 未配置 SMTP 且 dev-mode=true 时返回 code 供本地联调；生产必须关闭 dev-mode。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final int TTL_MINUTES = 10;
    private static final int MAX_PER_WINDOW = 5;
    private static final long WINDOW_MILLIS = 10 * 60 * 1000L;

    private final EmailTokenMapper tokenMapper;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;

    @Value("${app.mail.dev-mode:true}")
    private boolean devMode;

    private final Map<String, Deque<Long>> sendWindow = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /** 给当前登录用户已填写的邮箱发送验证码 */
    public Map<String, Object> sendVerifyCode() {
        User user = mustGet(CurrentUser.id());
        if (!StringUtils.hasText(user.getEmail())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请先在个人资料中填写邮箱");
        }
        return send(user.getId(), user.getEmail(), "verify");
    }

    /** 找回密码：按邮箱定位用户，避免泄露邮箱是否存在 */
    public Map<String, Object> sendResetCode(String email) {
        if (!StringUtils.hasText(email)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "邮箱不能为空");
        }
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getEmail, email.trim()));
        Map<String, Object> resp = new LinkedHashMap<>();
        if (user == null) {
            resp.put("sent", true);
            return resp;
        }
        return send(user.getId(), user.getEmail(), "reset");
    }

    public void verifyEmail(String code) {
        User user = mustGet(CurrentUser.id());
        EmailToken token = mustValidToken(user.getEmail(), "verify", code);
        token.setUsed(1);
        tokenMapper.updateById(token);
        user.setEmailVerified(1);
        // MP 默认忽略 null 字段，显式 set 生效
        userMapper.updateById(user);
        log.info("邮箱验证通过 userId={}", user.getId());
    }

    public void resetPassword(String email, String code, String newPassword) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(newPassword)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "邮箱与新密码不能为空");
        }
        if (newPassword.length() < 6 || newPassword.length() > 64) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "密码长度需为 6-64 位");
        }
        EmailToken token = mustValidToken(email.trim(), "reset", code);
        User user = mustGet(token.getUserId());
        token.setUsed(1);
        tokenMapper.updateById(token);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
        userMapper.updateById(user);
        log.info("密码重置成功 userId={}", user.getId());
    }

    private Map<String, Object> send(Long userId, String email, String type) {
        checkRateLimit(email);
        String code = String.format("%06d", random.nextInt(1_000_000));
        EmailToken token = new EmailToken();
        token.setUserId(userId);
        token.setEmail(email);
        token.setToken(code);
        token.setType(type);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(TTL_MINUTES));
        token.setUsed(0);
        tokenMapper.insert(token);

        String subject = "verify".equals(type) ? "AI 发文博客 - 邮箱验证码" : "AI 发文博客 - 重置密码";
        String text = "你的验证码是 " + code + "，" + TTL_MINUTES + " 分钟内有效。如非本人操作请忽略。";
        boolean sent = mailService.send(email, subject, text);
        if (!sent) {
            log.info("邮件服务未配置，验证码（仅开发环境可见）email={} type={} code={}", email, type, code);
            if (!devMode) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "邮件服务未配置，请联系管理员");
            }
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("sent", sent);
        resp.put("expiresIn", TTL_MINUTES * 60);
        if (devMode) {
            resp.put("devCode", code);
        }
        return resp;
    }

    private EmailToken mustValidToken(String email, String type, String code) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "验证码不能为空");
        }
        EmailToken token = tokenMapper.selectOne(Wrappers.<EmailToken>lambdaQuery()
                .eq(EmailToken::getEmail, email)
                .eq(EmailToken::getType, type)
                .eq(EmailToken::getUsed, 0)
                .eq(EmailToken::getToken, code.trim())
                .orderByDesc(EmailToken::getId)
                .last("LIMIT 1"));
        if (token == null || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "验证码错误或已过期");
        }
        return token;
    }

    private void checkRateLimit(String email) {
        long now = System.currentTimeMillis();
        Deque<Long> window = sendWindow.computeIfAbsent(email, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() > WINDOW_MILLIS) {
                window.pollFirst();
            }
            if (window.size() >= MAX_PER_WINDOW) {
                throw new BusinessException(ErrorCode.TOO_MANY_ATTEMPTS, "发送过于频繁，请稍后再试");
            }
            window.addLast(now);
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
