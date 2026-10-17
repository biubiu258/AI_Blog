-- =============================================================
-- 建表脚本 —— 用于已存在的远程数据库（aiblog）
-- 与 schema.sql 的唯一区别：不含 CREATE DATABASE / USE，
-- 因为远程账号通常无建库权限，且库已由管理员创建。
-- 全部为 CREATE TABLE IF NOT EXISTS，可重复执行。
-- =============================================================
SET NAMES utf8mb4;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`      VARCHAR(64)  NOT NULL COMMENT '登录名',
    `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希',
    `email`         VARCHAR(128) NULL COMMENT '邮箱，可选',
    `nickname`      VARCHAR(64)  NULL COMMENT '展示昵称',
    `avatar_url`    VARCHAR(255) NULL COMMENT '头像地址',
    `bio`           VARCHAR(500) NULL COMMENT '简介',
    `role`          VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT 'USER / ADMIN / CONTENT_ADMIN',
    `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '0 正常 / 1 封禁',
    `ban_reason`    VARCHAR(255) NULL COMMENT '封禁原因',
    `banned_until`  DATETIME     NULL COMMENT '封禁截止时间，NULL=永久',
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
    `status`       VARCHAR(16)  NOT NULL DEFAULT 'draft' COMMENT 'draft / published / private（private 为风控专属，仅作者可见）',
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

-- 管理员操作审计日志
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

-- 人文关怀检测审计（不含文章原文）
CREATE TABLE IF NOT EXISTS `care_log` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT      NOT NULL COMMENT '触发检测的作者',
    `article_id` BIGINT      NULL COMMENT '触发文章',
    `level`      VARCHAR(8)  NOT NULL COMMENT '检测级别：low/mid/high',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_care_log_user` (`user_id`)
) ENGINE = InnoDB COMMENT '人文关怀检测审计（不含原文）';
