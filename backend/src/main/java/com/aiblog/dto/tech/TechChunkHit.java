package com.aiblog.dto.tech;

import lombok.Data;

/**
 * 语料块检索命中：块字段 + 来源文档字段，供证据展示与 RRF 合并。
 */
@Data
public class TechChunkHit {

    private Long id;

    private Long docId;

    private Integer chunkIndex;

    private Integer pageNo;

    private String sectionPath;

    private String content;

    private String title;

    private String sourceName;

    private String version;

    /** MySQL 全文相关度，越小越不相关 */
    private Double score;
}
