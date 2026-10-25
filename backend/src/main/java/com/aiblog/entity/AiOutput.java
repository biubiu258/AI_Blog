package com.aiblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 输出审计：每次 AI 调用的输入快照、返回内容、追踪号与审核状态。
 */
@Data
@TableName("ai_output")
public class AiOutput {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 调用追踪号，Java 生成，同时透传 Python 日志 */
    private String traceId;

    private Long userId;

    private Long articleId;

    /** generate / review / search / care */
    private String action;

    /** 生成任务：outline/draft/.../suggest */
    private String task;

    /** python / mock / fallback */
    private String source;

    private String model;

    private String promptVersion;

    /** 输入 JSON 的 SHA-256，便于比对相同输入 */
    private String requestDigest;

    /** 输入快照（超长截断） */
    private String requestSnapshot;

    /** AI 返回内容全文 */
    private String output;

    /** 列表展示用摘要 */
    private String outputPreview;

    private Integer outputLength;

    /** streaming / success / failed / canceled */
    private String status;

    private String errorCode;

    private String errorMessage;

    private Integer tokensIn;

    private Integer tokensOut;

    private Integer durationMs;

    private Integer adopted;

    /** pending / passed / flagged */
    private String reviewStatus;

    private Long reviewedBy;

    private LocalDateTime reviewedAt;

    private String reviewNote;

    private LocalDateTime createdAt;

    private LocalDateTime finishedAt;
}
