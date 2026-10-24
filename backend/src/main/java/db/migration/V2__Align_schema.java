package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 兼容既有旧库：补齐封禁字段与审计表。
 * 新库由 V1 基线直接建全，本迁移为空操作。
 */
public class V2__Align_schema extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        addColumnIfMissing(connection, "user", "ban_reason",
                "VARCHAR(255) NULL COMMENT '封禁原因'");
        addColumnIfMissing(connection, "user", "banned_until",
                "DATETIME NULL COMMENT '封禁截止时间，NULL=永久'");

        try (Statement statement = connection.createStatement()) {
            statement.execute("""
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
                    ) ENGINE = InnoDB COMMENT '管理员操作审计日志'
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS `care_log` (
                        `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
                        `user_id`    BIGINT      NOT NULL COMMENT '触发检测的作者',
                        `article_id` BIGINT      NULL COMMENT '触发文章',
                        `level`      VARCHAR(8)  NOT NULL COMMENT '检测级别：low/mid/high',
                        `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (`id`),
                        KEY `idx_care_log_user` (`user_id`)
                    ) ENGINE = InnoDB COMMENT '人文关怀检测审计（不含原文）'
                    """);
        }
    }

    private void addColumnIfMissing(Connection connection, String table, String column, String definition)
            throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement statement = connection.prepareStatement(checkSql)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next() && resultSet.getInt(1) > 0) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + definition);
        }
    }
}
