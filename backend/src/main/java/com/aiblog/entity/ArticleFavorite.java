package com.aiblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("article_favorite")
public class ArticleFavorite {
    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;
    private Long articleId;
    private LocalDateTime createdAt;
}
