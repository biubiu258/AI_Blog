package com.aiblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("article_view")
public class ArticleView {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleId;
    private String viewerKey;
    private LocalDate viewDate;
    private LocalDateTime createdAt;
}
