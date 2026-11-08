package com.aiblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 连载订阅（联合主键：series_id + user_id）。
 */
@Data
@TableName("series_subscription")
public class SeriesSubscription {

    @TableId(value = "series_id", type = IdType.INPUT)
    private Long seriesId;

    private Long userId;

    private LocalDateTime createdAt;
}
