package com.aiblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 连载与文章关联（联合主键：series_id + article_id）。
 */
@Data
@TableName("series_article")
public class SeriesArticle {

    @TableId(value = "series_id", type = IdType.INPUT)
    private Long seriesId;

    private Long articleId;

    /** 目录顺序，越小越靠前 */
    private Integer orderNo;

    private LocalDateTime createdAt;
}
