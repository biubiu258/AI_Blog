package com.aiblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员操作审计日志（admin_log）。
 */
@Data
@TableName("admin_log")
public class AdminLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作管理员 */
    private Long adminId;

    private String adminUsername;

    /** 动作：BAN/UNBAN/SET_ROLE/ARTICLE_UNPUBLISH/ARTICLE_DELETE/COMMENT_DELETE */
    private String action;

    /** 目标摘要，如 user:5 / article:1,2,3 */
    private String target;

    /** 详情 JSON */
    private String detail;

    private LocalDateTime createdAt;
}
