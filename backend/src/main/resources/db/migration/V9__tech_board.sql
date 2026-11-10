-- 技术专项：技术板块分类、语料库与术语表（规格见 docs/specs/tech-rag/spec.md）
SET NAMES utf8mb4;

-- 分类编码：把现有「技术教程」置为 tech，缺失则插入一行
ALTER TABLE `category`
    ADD COLUMN `code` VARCHAR(32) NULL COMMENT '分类编码，如 tech' AFTER `name`,
    ADD UNIQUE KEY `uk_category_code` (`code`);

UPDATE `category` SET `code` = 'tech' WHERE `name` = '技术教程' AND `code` IS NULL;

INSERT INTO `category` (`name`, `description`, `code`)
SELECT '技术教程', '技术实践、工具与排障教程', 'tech'
WHERE NOT EXISTS (SELECT 1 FROM `category` WHERE `code` = 'tech');

-- 技术语料文档
CREATE TABLE `tech_doc` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `title`         VARCHAR(200) NOT NULL COMMENT '文档标题',
    `doc_type`      VARCHAR(32)  NOT NULL DEFAULT 'article' COMMENT 'article/guide/faq/api/spec',
    `source_name`   VARCHAR(200) NULL COMMENT '来源名称',
    `source_url`    VARCHAR(500) NULL COMMENT '来源链接',
    `version`       VARCHAR(64)  NULL COMMENT '版本号，如 v2',
    `license`       VARCHAR(120) NULL COMMENT '授权方式',
    `file_path`     VARCHAR(500) NULL COMMENT '上传文件落盘路径',
    `file_type`     VARCHAR(16)  NOT NULL DEFAULT 'text' COMMENT 'markdown/text/pdf',
    `page_count`    INT          NOT NULL DEFAULT 0 COMMENT 'PDF 页数，非 PDF 为 0',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'draft' COMMENT 'draft/parsing/ready/failed',
    `checksum`      CHAR(64)     NULL COMMENT 'SHA-256，用于判重',
    `chunk_count`   INT          NOT NULL DEFAULT 0 COMMENT '切块数量',
    `error_message` VARCHAR(500) NULL COMMENT '失败原因，可直接展示',
    `created_by`    BIGINT       NULL COMMENT '创建人',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tech_doc_checksum` (`checksum`),
    KEY `idx_tech_doc_status` (`status`, `updated_at`),
    KEY `idx_tech_doc_type` (`doc_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '技术语料文档';

-- 语料切块：中文召回依赖 ngram 全文索引
CREATE TABLE `tech_doc_chunk` (
    `id`           BIGINT   NOT NULL AUTO_INCREMENT,
    `doc_id`       BIGINT   NOT NULL COMMENT '所属文档',
    `chunk_index`  INT      NOT NULL COMMENT '块序号，从 0 开始',
    `page_no`      INT      NULL COMMENT 'PDF 页码，从 1 开始；非 PDF 为 NULL',
    `section_path` VARCHAR(300) NULL COMMENT '章节路径，如 3.2 令牌刷新',
    `content`      LONGTEXT NOT NULL COMMENT '块正文',
    `token_count`  INT      NOT NULL DEFAULT 0 COMMENT '估算 token 数',
    `chunk_hash`   CHAR(64) NULL COMMENT '块内容 SHA-256',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_chunk_doc_index` (`doc_id`, `chunk_index`),
    KEY `idx_chunk_doc` (`doc_id`),
    FULLTEXT KEY `ft_chunk_content` (`content`) WITH PARSER ngram
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '技术语料块';

-- 术语表：规范写法与异体/禁用写法
CREATE TABLE `tech_term` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT,
    `canonical`      VARCHAR(120)  NOT NULL COMMENT '规范写法',
    `category`       VARCHAR(64)   NOT NULL DEFAULT '通用' COMMENT '术语分类',
    `definition`     VARCHAR(1000) NULL COMMENT '释义',
    `variants`       VARCHAR(1000) NULL COMMENT '禁用/异体写法，逗号、顿号或换行分隔',
    `case_sensitive` TINYINT       NOT NULL DEFAULT 0 COMMENT '1=区分大小写',
    `status`         VARCHAR(16)   NOT NULL DEFAULT 'active' COMMENT 'active/disabled',
    `created_by`     BIGINT        NULL COMMENT '创建人',
    `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_term_canonical_category` (`canonical`, `category`),
    KEY `idx_term_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '技术术语表';
