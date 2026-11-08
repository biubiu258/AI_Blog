package com.aiblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 日更打卡：当日有保存或发布即视为打卡，断更后连续天数归零。
 */
@Data
@TableName("writing_streak")
public class WritingStreak {

    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    private Integer currentStreak;

    private Integer longestStreak;

    private LocalDate lastActiveDate;

    private Integer makeupCards;

    private LocalDateTime updatedAt;
}
