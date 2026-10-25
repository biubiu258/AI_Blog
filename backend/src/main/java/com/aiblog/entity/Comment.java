package com.aiblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("comment")
public class Comment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long articleId;

    private Long authorId;

    /** 评论内容（纯文本） */
    private String content;

    /** 父评论 id，NULL 为一级评论（两级结构，回复的回复归入同一父） */
    private Long parentId;

    /** 被回复用户 */
    private Long replyToUserId;

    /** approved / hidden */
    private String status;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;
}
