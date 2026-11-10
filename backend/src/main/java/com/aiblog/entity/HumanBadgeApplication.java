package com.aiblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 人类创作徽章申请：连续 none 声明 + 抽审通过后授予。
 */
@Data
@TableName("human_badge_application")
public class HumanBadgeApplication {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 申请时提交的 none 等级文章 id 列表 JSON */
    private String articleIds;

    /** pending / passed / rejected */
    private String status;

    /** 审核管理员 */
    private Long reviewerId;

    /** 审核意见；拒绝时向用户展示 */
    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime reviewedAt;
}
