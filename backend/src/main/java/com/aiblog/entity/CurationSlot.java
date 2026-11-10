package com.aiblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 伯乐推荐位：一周内由伯乐人工确认的文章，reason 对读者展示。
 */
@Data
@TableName("curation_slot")
public class CurationSlot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long curatorId;

    private Long articleId;

    /** ISO 周次，如 2026-W45 */
    private String week;

    private String reason;

    private LocalDateTime createdAt;
}
