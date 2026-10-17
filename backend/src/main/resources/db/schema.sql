-- =============================================================
-- AI 辅助发文博客系统 数据库初始化脚本（MySQL 8.x）
-- 字段定义见 docs/04-数据库设计.md
-- =============================================================
CREATE DATABASE IF NOT EXISTS ai_blog DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ai_blog;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`      VARCHAR(64)  NOT NULL COMMENT '登录名',
    `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希',
    `email`         VARCHAR(128) NULL COMMENT '邮箱，可选',
    `nickname`      VARCHAR(64)  NULL COMMENT '展示昵称',
    `avatar_url`    VARCHAR(255) NULL COMMENT '头像地址',
    `bio`           VARCHAR(500) NULL COMMENT '简介',
    `role`          VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT 'USER / ADMIN',
    `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '0 正常 / 1 封禁',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`),
    UNIQUE KEY `uk_user_email` (`email`)
) ENGINE = InnoDB COMMENT '用户';

-- 分类表
CREATE TABLE IF NOT EXISTS `category` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(64) NOT NULL COMMENT '分类名',
    `description` VARCHAR(255) NULL COMMENT '描述',
    `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_category_name` (`name`)
) ENGINE = InnoDB COMMENT '分类';

-- 文章表
CREATE TABLE IF NOT EXISTS `article` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `author_id`    BIGINT       NOT NULL COMMENT '作者，外键 -> user',
    `category_id`  BIGINT       NULL COMMENT '分类，外键 -> category',
    `title`        VARCHAR(200) NOT NULL COMMENT '标题',
    `content_html` LONGTEXT     NULL COMMENT '经白名单清洗后的正文 HTML',
    `summary`      VARCHAR(500) NULL COMMENT '摘要，可选',
    `status`       VARCHAR(16)  NOT NULL DEFAULT 'draft' COMMENT 'draft / published',
    `published_at` DATETIME     NULL COMMENT '发布时间',
    `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_article_author` (`author_id`),
    KEY `idx_article_category` (`category_id`),
    KEY `idx_article_status_pub` (`status`, `published_at`)
) ENGINE = InnoDB COMMENT '文章';

-- 评论表
CREATE TABLE IF NOT EXISTS `comment` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `article_id` BIGINT      NOT NULL COMMENT '所属文章，外键',
    `author_id`  BIGINT      NOT NULL COMMENT '作者，外键 -> user',
    `content`    TEXT        NOT NULL COMMENT '评论内容（纯文本）',
    `deleted`    TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_comment_article` (`article_id`, `created_at`)
) ENGINE = InnoDB COMMENT '评论';

-- 用户写作偏好（P1）
CREATE TABLE IF NOT EXISTS `user_preference` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`       BIGINT      NOT NULL COMMENT '用户，唯一外键 -> user',
    `tone`          VARCHAR(200) NULL COMMENT '口吻/风格',
    `target_reader` VARCHAR(200) NULL COMMENT '目标读者',
    `structure`     TEXT         NULL COMMENT '常用结构描述，可 JSON',
    `extra`         TEXT         NULL COMMENT '其他偏好，可 JSON',
    `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pref_user` (`user_id`)
) ENGINE = InnoDB COMMENT '用户写作偏好';

-- 上传文件记录
CREATE TABLE IF NOT EXISTS `upload_file` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `owner_id`   BIGINT       NOT NULL COMMENT '上传者，外键 -> user',
    `file_path`  VARCHAR(255) NOT NULL COMMENT '磁盘相对路径',
    `url`        VARCHAR(255) NOT NULL COMMENT '对外访问地址',
    `size`       BIGINT       NOT NULL COMMENT '字节数',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_upload_owner` (`owner_id`)
) ENGINE = InnoDB COMMENT '上传文件';
