package com.aiblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章完读统计：前端滚动到正文底部上报，用于策展候选排序。
 */
@Data
@TableName("article_read_stat")
public class ArticleReadStat {

    @TableId(value = "article_id", type = IdType.INPUT)
    private Long articleId;

    private Integer completeCount;

    private LocalDateTime updatedAt;
}
