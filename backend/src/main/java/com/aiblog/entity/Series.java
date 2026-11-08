package com.aiblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 专题/连载：作者把多篇文章按目录顺序组织，读者可订阅更新。
 */
@Data
@TableName("series")
public class Series {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long authorId;

    private String title;

    private String description;

    /** ongoing / finished */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
