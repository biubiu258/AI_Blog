package com.aiblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 人文关怀检测审计（care_log）：只记 等级+用户+文章+时间，不含文章原文。
 */
@Data
@TableName("care_log")
public class CareLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 触发检测的作者 */
    private Long userId;

    /** 触发文章 */
    private Long articleId;

    /** 检测级别：low/mid/high（none 不落库） */
    private String level;

    private LocalDateTime createdAt;
}
