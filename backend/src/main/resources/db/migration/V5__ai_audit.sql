-- =============================================================
-- AI 输出审计：保存所有 AI 返回内容，支持按用户、文章、调用链溯源，
-- 并提供 pending/passed/flagged 审核工作流。
-- =============================================================
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `ai_output` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `trace_id`         VARCHAR(64)  NOT NULL COMMENT '调用追踪号，Java 生成并透传 Python',
    `user_id`          BIGINT       NOT NULL COMMENT '发起用户',
    `article_id`       BIGINT       NULL COMMENT '关联文章（可空）',
    `action`           VARCHAR(16)  NOT NULL COMMENT 'generate/review/search/care',
    `task`             VARCHAR(16)  NULL COMMENT 'outline/draft/.../suggest',
    `source`           VARCHAR(16)  NOT NULL DEFAULT 'python' COMMENT 'python/mock/fallback',
    `model`            VARCHAR(64)  NULL COMMENT '模型标识',
    `prompt_version`   VARCHAR(32)  NULL COMMENT '提示词版本',
    `request_digest`   CHAR(64)     NULL COMMENT '输入 JSON 的 SHA-256',
    `request_snapshot` MEDIUMTEXT   NULL COMMENT '输入快照（截断）',
    `output`           MEDIUMTEXT   NULL COMMENT 'AI 返回内容全文',
    `output_preview`   VARCHAR(500) NULL COMMENT '输出摘要，供列表展示',
    `output_length`    INT          NOT NULL DEFAULT 0 COMMENT '输出字符数',
    `status`           VARCHAR(16)  NOT NULL DEFAULT 'streaming' COMMENT 'streaming/success/failed/canceled',
    `error_code`       VARCHAR(32)  NULL,
    `error_message`    VARCHAR(500) NULL,
    `tokens_in`        INT          NOT NULL DEFAULT 0,
    `tokens_out`       INT          NOT NULL DEFAULT 0,
    `duration_ms`      INT          NOT NULL DEFAULT 0,
    `adopted`          TINYINT      NOT NULL DEFAULT 0 COMMENT '内容是否被采纳',
    `review_status`    VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending/passed/flagged',
    `reviewed_by`      BIGINT       NULL,
    `reviewed_at`      DATETIME     NULL,
    `review_note`      VARCHAR(500) NULL,
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `finished_at`      DATETIME     NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_output_trace` (`trace_id`),
    KEY `idx_ai_output_user` (`user_id`, `created_at`),
    KEY `idx_ai_output_action` (`action`, `created_at`),
    KEY `idx_ai_output_review` (`review_status`, `created_at`)
) ENGINE = InnoDB COMMENT 'AI 输出审计';
