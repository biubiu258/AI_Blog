package com.aiblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创作激励活动：后台配置时间、奖池、参与条件与评选方式。
 */
@Data
@TableName("campaign")
public class Campaign {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String description;

    private String prizePool;

    private String joinRule;

    private String judgeRule;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    /** draft / active / ended */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
