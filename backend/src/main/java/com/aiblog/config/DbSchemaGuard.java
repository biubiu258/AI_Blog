package com.aiblog.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时数据库结构守护：为已存在的旧库幂等补齐新增列/表，
 * 换环境零手工迁移。CREATE TABLE IF NOT EXISTS 天然幂等；
 * 加列前先查 information_schema，避免重复执行报错。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DbSchemaGuard implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        addColumnIfMissing("user", "ban_reason",
                "VARCHAR(255) NULL COMMENT '封禁原因'");
        addColumnIfMissing("user", "banned_until",
                "DATETIME NULL COMMENT '封禁截止时间，NULL=永久'");
        ensureAdminLogTable();
        ensureCareLogTable();
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, table, column);
        if (count == null || count == 0) {
            jdbcTemplate.execute(
                    "ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + definition);
            log.info("DbSchemaGuard: {}.{} 列已补齐", table, column);
        }
    }

    private void ensureAdminLogTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `admin_log` (
                    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
                    `admin_id`       BIGINT       NOT NULL COMMENT '操作管理员',
                    `admin_username` VARCHAR(64)  NOT NULL COMMENT '操作管理员用户名',
                    `action`         VARCHAR(50)  NOT NULL COMMENT '动作：BAN/UNBAN/SET_ROLE/ARTICLE_UNPUBLISH/ARTICLE_DELETE/COMMENT_DELETE',
                    `target`         VARCHAR(255) NOT NULL COMMENT '目标摘要，如 user:5 / article:1,2,3',
                    `detail`         VARCHAR(1000) NULL COMMENT '详情 JSON',
                    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (`id`),
                    KEY `idx_admin_log_admin` (`admin_id`, `created_at`)
                ) ENGINE = InnoDB COMMENT '管理员操作审计日志';
                """);
    }

    /** 人文关怀检测审计：只记 等级+用户+文章+时间，不含文章原文 */
    private void ensureCareLogTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `care_log` (
                    `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
                    `user_id`    BIGINT      NOT NULL COMMENT '触发检测的作者',
                    `article_id` BIGINT      NULL COMMENT '触发文章',
                    `level`      VARCHAR(8)  NOT NULL COMMENT '检测级别：low/mid/high',
                    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (`id`),
                    KEY `idx_care_log_user` (`user_id`)
                ) ENGINE = InnoDB COMMENT '人文关怀检测审计（不含原文）';
                """);
    }
}
