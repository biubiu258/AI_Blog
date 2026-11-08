-- 迭代 3：社区运营与分发（伯乐策展 / 日更打卡 / 专题连载 / 慢读 / 征文活动）
SET NAMES utf8mb4;

CREATE TABLE `curator` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT      NOT NULL COMMENT '伯乐用户',
    `status`     VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT 'active/inactive',
    `term_start` DATE        NULL COMMENT '任期开始',
    `term_end`   DATE        NULL COMMENT '任期结束',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_curator_user` (`user_id`),
    KEY `idx_curator_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '伯乐/策展人';

CREATE TABLE `curation_slot` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `curator_id`  BIGINT       NOT NULL COMMENT '推荐人',
    `article_id`  BIGINT       NOT NULL COMMENT '推荐文章',
    `week`        VARCHAR(8)   NOT NULL COMMENT '如 2026-W45',
    `reason`      VARCHAR(500) NULL COMMENT '推荐理由（对读者可见）',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_slot_week` (`week`, `created_at`),
    KEY `idx_slot_article` (`article_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '伯乐推荐位';

CREATE TABLE `writing_streak` (
    `user_id`          BIGINT   NOT NULL,
    `current_streak`   INT      NOT NULL DEFAULT 0 COMMENT '当前连续天数',
    `longest_streak`   INT      NOT NULL DEFAULT 0 COMMENT '历史最长连续天数',
    `last_active_date` DATE     NULL COMMENT '最近打卡日',
    `makeup_cards`     INT      NOT NULL DEFAULT 0 COMMENT '补签卡',
    `updated_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '日更打卡';

CREATE TABLE `writing_streak_day` (
    `user_id`     BIGINT      NOT NULL,
    `active_date` DATE        NOT NULL,
    `source`      VARCHAR(16) NOT NULL DEFAULT 'save' COMMENT 'save/publish/makeup',
    `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_id`, `active_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '打卡日历明细';

CREATE TABLE `series` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `author_id`   BIGINT       NOT NULL,
    `title`       VARCHAR(120) NOT NULL,
    `description` VARCHAR(500) NULL,
    `status`      VARCHAR(16)  NOT NULL DEFAULT 'ongoing' COMMENT 'ongoing/finished',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_series_author` (`author_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '连载/专题';

CREATE TABLE `series_article` (
    `series_id`  BIGINT   NOT NULL,
    `article_id` BIGINT   NOT NULL,
    `order_no`   INT      NOT NULL DEFAULT 0 COMMENT '目录顺序，越小越靠前',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`series_id`, `article_id`),
    KEY `idx_sa_article` (`article_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '连载文章';

CREATE TABLE `series_subscription` (
    `series_id`  BIGINT   NOT NULL,
    `user_id`    BIGINT   NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`series_id`, `user_id`),
    KEY `idx_ss_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '连载订阅';

CREATE TABLE `article_read_stat` (
    `article_id`     BIGINT   NOT NULL,
    `complete_count` INT      NOT NULL DEFAULT 0 COMMENT '读完次数（前端到达正文底部上报）',
    `updated_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`article_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文章完读统计';

CREATE TABLE `campaign` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `title`       VARCHAR(120) NOT NULL COMMENT '活动标题',
    `description` VARCHAR(500) NULL COMMENT '活动说明',
    `prize_pool`  VARCHAR(120) NULL COMMENT '奖池描述',
    `join_rule`   VARCHAR(500) NULL COMMENT '参与条件',
    `judge_rule`  VARCHAR(500) NULL COMMENT '评选方式',
    `start_at`    DATETIME     NOT NULL COMMENT '开始时间',
    `end_at`      DATETIME     NOT NULL COMMENT '结束时间',
    `status`      VARCHAR(16)  NOT NULL DEFAULT 'draft' COMMENT 'draft/active/ended',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_campaign_status` (`status`, `start_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '创作激励活动';
