package com.aiblog.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 生产环境启动守卫：拒绝默认密钥与默认密码，避免带病上线。
 */
@Component
@Profile("prod")
public class ProdConfigGuard implements InitializingBean {

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    @Value("${ai.internal-token:}")
    private String aiInternalToken;

    @Value("${app.init.admin-password:}")
    private String adminPassword;

    @Override
    public void afterPropertiesSet() {
        List<String> problems = new ArrayList<>();
        if (isBlank(jwtSecret) || jwtSecret.contains("change-me") || jwtSecret.contains("dev-secret")) {
            problems.add("JWT_SECRET 未配置或仍为开发默认值");
        }
        if (isBlank(datasourcePassword) || "root".equals(datasourcePassword)) {
            problems.add("MYSQL_PASSWORD 未配置或仍为默认 root");
        }
        if (isBlank(aiInternalToken) || "dev-internal-token".equals(aiInternalToken)) {
            problems.add("AI_INTERNAL_TOKEN 未配置或仍为开发默认值");
        }
        if (isBlank(adminPassword) || "admin123".equals(adminPassword)) {
            problems.add("ADMIN_INIT_PASSWORD 未配置或仍为默认 admin123");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("生产环境配置不安全，拒绝启动：" + String.join("；", problems));
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
