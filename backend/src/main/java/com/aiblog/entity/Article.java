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

    /** AI 参与程度：none / assisted / generated */
    private String aiUsageLevel;

    /** 作者主动声明时间 */
    private LocalDateTime aiDeclaredAt;

    /** 关联的 ai_output trace_id 列表（JSON 数组） */
    private String aiTraceIds;

    /** 人类创作徽章（迭代 2 启用） */
    private Integer humanBadge;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
