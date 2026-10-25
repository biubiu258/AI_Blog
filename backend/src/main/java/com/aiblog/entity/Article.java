package com.aiblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("article")
public class Article {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long authorId;

    private Long categoryId;

    private String title;

    /** 经白名单清洗后的正文 HTML */
    private String contentHtml;

    private String summary;

    /** draft / published / private / scheduled */
    private String status;

    private LocalDateTime publishedAt;

    /** 定时发布时间（status=scheduled 时有效） */
    private LocalDateTime scheduledAt;

    /** 阅读量 */
    private Integer viewCount;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
