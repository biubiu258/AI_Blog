package com.aiblog.security;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录限流：IP 与账号双维度失败计数，连续失败 5 次锁定 10 分钟。
 * 单机内存实现（毕设单实例部署够用）；多实例部署需改用 Redis 等共享存储。
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_FAILS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(10);

    private record Attempt(int fails, LocalDateTime lockedUntil) {}

    private final ConcurrentHashMap<String, Attempt> store = new ConcurrentHashMap<>();

    /** 登录前检查：处于锁定期则直接拒绝 */
    public void checkLocked(String username, String ip) {
        LocalDateTime now = LocalDateTime.now();
        for (String key : keys(username, ip)) {
            Attempt a = store.get(key);
            if (a == null) {
                continue;
            }
            if (a.lockedUntil() != null) {
                if (a.lockedUntil().isAfter(now)) {
                    long minutes = Math.max(1, Duration.between(now, a.lockedUntil()).toMinutes());
                    throw new BusinessException(ErrorCode.TOO_MANY_ATTEMPTS,
                            "尝试次数过多，请 " + minutes + " 分钟后再试");
                }
                // 锁定已过期：清零重新计数
                store.remove(key);
            }
        }
    }

    /** 登录失败：两个维度同时累加，达到阈值即锁定 */
    public void recordFail(String username, String ip) {
        LocalDateTime now = LocalDateTime.now();
        for (String key : keys(username, ip)) {
            store.compute(key, (k, a) -> {
                int fails = (a == null ? 0 : a.fails()) + 1;
                LocalDateTime lockedUntil = a != null ? a.lockedUntil() : null;
                if (lockedUntil == null && fails >= MAX_FAILS) {
                    lockedUntil = now.plus(LOCK_DURATION);
                }
                return new Attempt(fails, lockedUntil);
            });
        }
    }

    /** 登录成功：清除两个维度的计数 */
    public void reset(String username, String ip) {
        keys(username, ip).forEach(store::remove);
    }

    private List<String> keys(String username, String ip) {
        return List.of("user:" + username, "ip:" + ip);
    }
}
