package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * P1 存量表列补齐：阅读量、定时发布、评论回复与审核态、令牌版本、邮箱验证。
 * 使用 information_schema 判断列是否存在，兼容已上线的旧库。
 */
public class V4__P1_align_schema extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        addColumn(connection, "article", "view_count", "INT NOT NULL DEFAULT 0 COMMENT '阅读量'");
        addColumn(connection, "article", "scheduled_at", "DATETIME NULL COMMENT '定时发布时间'");
        addColumn(connection, "comment", "parent_id", "BIGINT NULL COMMENT '父评论，NULL 为一级评论'");
        addColumn(connection, "comment", "reply_to_user_id", "BIGINT NULL COMMENT '回复的目标用户'");
        addColumn(connection, "comment", "status", "VARCHAR(16) NOT NULL DEFAULT 'approved' COMMENT 'approved/hidden'");
        addColumn(connection, "user", "token_version", "INT NOT NULL DEFAULT 0 COMMENT '令牌版本，递增即全端下线'");
        addColumn(connection, "user", "email_verified", "TINYINT NOT NULL DEFAULT 0 COMMENT '邮箱是否已验证'");
    }

    private void addColumn(Connection connection, String table, String column, String definition)
            throws Exception {
        if (hasColumn(connection, table, column)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + definition);
        }
    }

    private boolean hasColumn(Connection connection, String table, String column) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.COLUMNS "
                             + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + table
                             + "' AND COLUMN_NAME = '" + column + "'")) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }
}
