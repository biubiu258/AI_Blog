package com.aiblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 打卡日历明细：每个用户每天最多一条，重复写入由主键去重。
 */
@Data
@TableName("writing_streak_day")
public class WritingStreakDay {

    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    private LocalDate activeDate;

    /** save / publish / makeup */
    private String source;

    private LocalDateTime createdAt;
}
