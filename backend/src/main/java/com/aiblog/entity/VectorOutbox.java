package com.aiblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("vector_outbox")
public class VectorOutbox {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleId;
    /** upsert / delete */
    private String action;
    /** pending / done / failed */
    private String status;
    private Integer retries;
    private LocalDateTime nextRetryAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
