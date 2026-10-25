-- =============================================================
-- P1 内容组织与互动：标签、点赞、收藏、关注、通知、
-- 版本历史、邮箱令牌、举报、AI 用量、向量同步 outbox、阅读去重
-- =============================================================
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `tag` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `name`       VARCHAR(32) NOT NULL COMMENT '标签名',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tag_name` (`name`)
) ENGINE = InnoDB COMMENT '标签';

CREATE TABLE IF NOT EXISTS `article_tag` (
    `article_id` BIGINT NOT NULL,
    `tag_id`     BIGINT NOT NULL,
    PRIMARY KEY (`article_id`, `tag_id`),
    KEY `idx_article_tag_tag` (`tag_id`)
) ENGINE = InnoDB COMMENT '文章-标签';

CREATE TABLE IF NOT EXISTS `article_like` (
    `user_id`    BIGINT   NOT NULL,
    `article_id` BIGINT   NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_id`, `article_id`),
    KEY `idx_like_article` (`article_id`)
) ENGINE = InnoDB COMMENT '点赞';

CREATE TABLE IF NOT EXISTS `article_favorite` (
    `user_id`    BIGINT   NOT NULL,
    `article_id` BIGINT   NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_id`, `article_id`),
    KEY `idx_favorite_article` (`article_id`)
) ENGINE = InnoDB COMMENT '收藏';

CREATE TABLE IF NOT EXISTS `user_follow` (
    `follower_id` BIGINT   NOT NULL COMMENT '关注者',
    `followee_id` BIGINT   NOT NULL COMMENT '被关注者',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`follower_id`, `followee_id`),
    KEY `idx_follow_followee` (`followee_id`)
) ENGINE = InnoDB COMMENT '关注关系';

CREATE TABLE IF NOT EXISTS `notification` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT       NOT NULL COMMENT '接收者',
    `type`       VARCHAR(16)  NOT NULL COMMENT 'comment/reply/like/favorite/follow/system',
    `actor_id`   BIGINT       NULL COMMENT '触发者',
    `article_id` BIGINT       NULL COMMENT '关联文章',
    `comment_id` BIGINT       NULL COMMENT '关联评论',
    `content`    VARCHAR(500) NULL COMMENT '摘要文案',
    `is_read`    TINYINT      NOT NULL DEFAULT 0,
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_notification_user` (`user_id`, `is_read`, `created_at`)
) ENGINE = InnoDB COMMENT '站内通知';

CREATE TABLE IF NOT EXISTS `article_version` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `article_id`   BIGINT       NOT NULL,
    `title`        VARCHAR(200) NOT NULL,
    `content_html` LONGTEXT     NULL,
    `summary`      VARCHAR(500) NULL,
    `saved_by`     BIGINT       NOT NULL COMMENT '保存人',
    `saved_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_version_article` (`article_id`, `saved_at`)
) ENGINE = InnoDB COMMENT '文章版本快照';

CREATE TABLE IF NOT EXISTS `email_token` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT      NULL COMMENT '重置密码时可为空，按邮箱匹配',
    `email`      VARCHAR(128) NOT NULL,
    `token`      VARCHAR(64) NOT NULL COMMENT '6 位验证码或一次性令牌',
    `type`       VARCHAR(16) NOT NULL COMMENT 'verify/reset',
    `expires_at` DATETIME    NOT NULL,
    `used`       TINYINT     NOT NULL DEFAULT 0,
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_email_token` (`token`),
    KEY `idx_email_token_email` (`email`, `type`, `created_at`)
) ENGINE = InnoDB COMMENT '邮箱验证/重置令牌';

CREATE TABLE IF NOT EXISTS `report` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `reporter_id` BIGINT       NOT NULL,
    `target_type` VARCHAR(16)  NOT NULL COMMENT 'article/comment',
    `target_id`   BIGINT       NOT NULL,
    `reason`      VARCHAR(500) NOT NULL,
    `status`      VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending/handled/dismissed',
    `handled_by`  BIGINT       NULL,
    `handled_at`  DATETIME     NULL,
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_report_status` (`status`, `created_at`)
) ENGINE = InnoDB COMMENT '举报';

CREATE TABLE IF NOT EXISTS `ai_usage` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT      NOT NULL,
    `action`     VARCHAR(16) NOT NULL COMMENT 'generate/review/suggest',
    `tokens_in`  INT         NOT NULL DEFAULT 0,
    `tokens_out` INT         NOT NULL DEFAULT 0,
    `adopted`    TINYINT     NOT NULL DEFAULT 0 COMMENT '建议是否被采纳',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ai_usage_user` (`user_id`, `created_at`)
) ENGINE = InnoDB COMMENT 'AI 用量与采纳';

CREATE TABLE IF NOT EXISTS `vector_outbox` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT,
    `article_id`    BIGINT      NOT NULL,
    `action`        VARCHAR(16) NOT NULL COMMENT 'upsert/delete',
    `status`        VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT 'pending/done/failed',
    `retries`       INT         NOT NULL DEFAULT 0,
    `next_retry_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_outbox_status` (`status`, `next_retry_at`)
) ENGINE = InnoDB COMMENT '向量同步 outbox';

CREATE TABLE IF NOT EXISTS `article_view` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `article_id` BIGINT      NOT NULL,
    `viewer_key` VARCHAR(64) NOT NULL COMMENT '登录用户 id 或 IP 的摘要',
    `view_date`  DATE        NOT NULL,
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_article_view` (`article_id`, `viewer_key`, `view_date`)
) ENGINE = InnoDB COMMENT '阅读去重记录';
