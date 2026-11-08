package com.aiblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 伯乐/策展人：由运营指定任期，每期从候选池中人工确认推荐文章。
 */
@Data
@TableName("curator")
public class Curator {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** active / inactive */
    private String status;

    private LocalDate termStart;

    private LocalDate termEnd;

    private LocalDateTime createdAt;
}
