package com.aiblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 技术语料块：检索与证据展示的最小单位。
 */
@Data
@TableName("tech_doc_chunk")
public class TechDocChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long docId;

    private Integer chunkIndex;

    /** PDF 页码，从 1 开始；非 PDF 为 null */
    private Integer pageNo;

    private String sectionPath;

    private String content;

    private Integer tokenCount;

    private String chunkHash;

    private LocalDateTime createdAt;
}
