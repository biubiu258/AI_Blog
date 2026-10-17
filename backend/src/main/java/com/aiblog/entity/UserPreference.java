package com.aiblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户写作偏好（P1）：Java 读取后作为 user_context.preferences 传给 Python。
 */
@Data
@TableName("user_preference")
public class UserPreference {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 口吻/风格 */
    private String tone;

    /** 目标读者 */
    private String targetReader;

    /** 常用结构描述，可 JSON */
    private String structure;

    /** 其他偏好，可 JSON */
    private String extra;

    private LocalDateTime updatedAt;
}
