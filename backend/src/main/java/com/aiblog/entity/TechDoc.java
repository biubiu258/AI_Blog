package com.aiblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 技术语料文档：Markdown / 纯文本 / PDF 解析后的入库记录。
 */
@Data
@TableName("tech_doc")
public class TechDoc {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    /** article / guide / faq / api / spec */
    private String docType;

    private String sourceName;

    private String sourceUrl;

    private String version;

    private String license;

    /** 上传文件落盘路径，粘贴内容为空 */
    private String filePath;

    /** markdown / text / pdf */
    private String fileType;

    /** PDF 页数，非 PDF 为 0 */
    private Integer pageCount;

    /** draft / parsing / ready / failed */
    private String status;

    /** 文件内容 SHA-256，用于判重 */
    private String checksum;

    private Integer chunkCount;

    /** 失败原因，可直接展示 */
    private String errorMessage;

    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
