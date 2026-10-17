package com.aiblog.config;

import com.aiblog.entity.Category;
import com.aiblog.entity.User;
import com.aiblog.mapper.CategoryMapper;
import com.aiblog.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

/**
 * 启动初始化：确保存在管理员账号与默认分类，便于首次演示。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final UserMapper userMapper;
    private final CategoryMapper categoryMapper;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public ApplicationRunner initAdminAndCategories() {
        return args -> {
            if (userMapper.selectCount(null) == 0) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPasswordHash(passwordEncoder.encode("admin123"));
                admin.setNickname("管理员");
                admin.setRole("ADMIN");
                admin.setStatus(0);
                userMapper.insert(admin);
                log.info("已创建默认管理员: admin / admin123（请尽快修改密码）");
            }
            if (categoryMapper.selectCount(null) == 0) {
                List.of("技术教程", "AI 工具", "随笔", "生活").forEach(name -> {
                    Category category = new Category();
                    category.setName(name);
                    categoryMapper.insert(category);
                });
                log.info("已初始化默认分类");
            }
        };
    }
}
