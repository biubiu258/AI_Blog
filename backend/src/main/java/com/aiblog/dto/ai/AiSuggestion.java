package com.aiblog.dto.ai;

import lombok.Data;

/**
 * 单条局部建议：quote 已由 Python 校验为正文中的唯一原文。
 */
@Data
public class AiSuggestion {

    private String type;

    /** low / mid / high */
    private String severity;

    private String quote;

    private String reason;

    /** 为空表示只提示，不直接替换 */
    private String replacement;
}
