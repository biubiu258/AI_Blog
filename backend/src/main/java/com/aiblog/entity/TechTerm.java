package com.aiblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 技术术语：规范写法 + 禁用/异体写法，用于术语规则检查。
 */
@Data
@TableName("tech_term")
public class TechTerm {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规范写法 */
    private String canonical;

    private String category;

    private String definition;

    /** 禁用/异体写法，逗号、顿号或换行分隔 */
    private String variants;

    /** 1=区分大小写 */
    private Integer caseSensitive;

    /** active / disabled */
    private String status;

    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
