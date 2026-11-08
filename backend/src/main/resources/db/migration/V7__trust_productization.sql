-- 迭代 2：信任产品化（个人文风库 + 人类创作徽章）
SET NAMES utf8mb4;

CREATE TABLE `style_profile` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`      BIGINT      NOT NULL COMMENT '所属用户',
    `name`         VARCHAR(64) NOT NULL COMMENT '风格名称，如「我的散文体」',
    `features`     TEXT        NULL COMMENT 'AI 提炼的风格特征 JSON',
    `summary`      VARCHAR(500) NULL COMMENT '风格一句话摘要，生成提示用',
    `sample_count` INT         NOT NULL DEFAULT 0 COMMENT '训练样本文章数',
    `is_default`   TINYINT     NOT NULL DEFAULT 0 COMMENT '是否默认文风',
    `status`       VARCHAR(16) NOT NULL DEFAULT 'building' COMMENT 'building/ready/failed',
    `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_style_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '个人文风档案';

CREATE TABLE `human_badge_application` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT       NOT NULL COMMENT '申请人',
    `article_ids` TEXT         NULL COMMENT '申请时提交的 none 等级文章 id 列表 JSON',
    `status`      VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending/passed/rejected',
    `reviewer_id` BIGINT       NULL COMMENT '审核管理员',
    `note`        VARCHAR(500) NULL COMMENT '审核意见；拒绝时向用户展示',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `reviewed_at` DATETIME     NULL COMMENT '审核时间',
    PRIMARY KEY (`id`),
    KEY `idx_badge_status` (`status`, `created_at`),
    KEY `idx_badge_user` (`user_id`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '人类创作徽章申请';
