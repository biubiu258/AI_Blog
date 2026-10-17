package com.aiblog.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** HS256 密钥，长度须 >= 32 字节 */
    private String secret;

    /** 令牌有效期（小时） */
    private int expireHours = 72;
}
