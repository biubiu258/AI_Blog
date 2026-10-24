package com.aiblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("report")
public class Report {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reporterId;
    /** article / comment */
    private String targetType;
    private Long targetId;
    private String reason;
    /** pending / handled / dismissed */
    private String status;
    private Long handledBy;
    private LocalDateTime handledAt;
    private LocalDateTime createdAt;
}
