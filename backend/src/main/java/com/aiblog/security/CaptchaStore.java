package com.aiblog.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录验证码存储：内存 Map，2 分钟过期、一次性消费。
 * 生成新验证码时顺手清理过期项；多实例部署需改用共享存储。
 */
@Component
public class CaptchaStore {

    private static final Duration TTL = Duration.ofMinutes(2);

    private record Entry(String code, LocalDateTime expireAt) {}

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    /** 保存验证码明文，返回一次性 captchaId */
    public String create(String code) {
        store.entrySet().removeIf(e -> e.getValue().expireAt().isBefore(LocalDateTime.now()));
        String id = UUID.randomUUID().toString().replace("-", "");
        store.put(id, new Entry(code.toLowerCase(), LocalDateTime.now().plus(TTL)));
        return id;
    }

    /** 校验并消费（无论对错均作废），防止重放 */
    public boolean verify(String captchaId, String input) {
        if (captchaId == null || input == null || input.isBlank()) {
            return false;
        }
        Entry e = store.remove(captchaId);
        return e != null
                && e.expireAt().isAfter(LocalDateTime.now())
                && e.code().equals(input.trim().toLowerCase());
    }
}
