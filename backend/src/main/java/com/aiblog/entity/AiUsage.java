package com.aiblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_usage")
public class AiUsage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** generate / review / suggest */
    private String action;
    private Integer tokensIn;
    private Integer tokensOut;
    private Integer adopted;
    private LocalDateTime createdAt;
}
