package com.aiblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("article_version")
public class ArticleVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleId;
    private String title;
    private String contentHtml;
    private String summary;
    private Long savedBy;
    private LocalDateTime savedAt;
}
