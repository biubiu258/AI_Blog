package com.aiblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 个人文风档案：AI 从用户旧文提炼的风格特征，生成/审阅时注入。
 */
@Data
@TableName("style_profile")
public class StyleProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 风格名称，如「我的散文体」 */
    private String name;

    /** AI 提炼的风格特征 JSON */
    private String features;

    /** 风格一句话摘要，拼入生成提示 */
    private String summary;

    /** 训练样本文章数 */
    private Integer sampleCount;

    /** 是否默认文风 */
    private Integer isDefault;

    /** building / ready / failed */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
