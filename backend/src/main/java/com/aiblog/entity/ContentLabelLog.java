package com.aiblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 内容标识变更日志：每次等级调整必须留痕，满足《标识办法》可追溯要求。
 */
@Data
@TableName("content_label_log")
public class ContentLabelLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long articleId;

    /** 变更前等级，NULL 表示首次声明 */
    private String fromLevel;

    private String toLevel;

    /** 操作人；NULL 表示系统自动 */
    private Long operatorId;

    private String reason;

    private LocalDateTime createdAt;
}
