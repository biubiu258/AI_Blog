-- 迭代 1：AI 参与声明与内容标识（对齐《人工智能生成合成内容标识办法》）
SET NAMES utf8mb4;

ALTER TABLE `article`
    ADD COLUMN `ai_usage_level` VARCHAR(16) NOT NULL DEFAULT 'none'
        COMMENT 'AI 参与程度：none 未使用 / assisted 辅助创作 / generated 主要生成',
    ADD COLUMN `ai_declared_at` DATETIME NULL COMMENT '作者主动声明时间',
    ADD COLUMN `ai_trace_ids`   TEXT     NULL COMMENT '关联的 ai_output trace_id 列表（JSON 数组）',
    ADD COLUMN `human_badge`    TINYINT  NOT NULL DEFAULT 0 COMMENT '人类创作徽章，迭代 2 启用';

CREATE TABLE `content_label_log` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `article_id`  BIGINT       NOT NULL COMMENT '文章 id',
    `from_level`  VARCHAR(16)  NULL COMMENT '变更前等级，NULL 表示首次声明',
    `to_level`    VARCHAR(16)  NOT NULL COMMENT '变更后等级',
    `operator_id` BIGINT       NULL COMMENT '操作人；NULL 表示系统自动',
    `reason`      VARCHAR(200) NULL COMMENT '变更原因',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_label_article` (`article_id`, `created_at`),
    KEY `idx_label_level` (`to_level`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 内容标识变更日志';

-- 已发布的历史文章默认视为未使用 AI（保守取值，作者可在后台修正）
UPDATE `article` SET `ai_usage_level` = 'none' WHERE `ai_usage_level` IS NULL;
