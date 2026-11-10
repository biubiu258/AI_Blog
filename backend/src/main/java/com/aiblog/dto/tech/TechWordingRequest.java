package com.aiblog.dto.tech;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 术语与措辞检查请求：只检查选中段落，不扫描全文。
 */
@Data
public class TechWordingRequest {

    private Long articleId;

    @Size(max = 200, message = "章节路径最长 200 个字符")
    private String sectionPath;

    @NotBlank(message = "text 不能为空")
    @Size(min = 20, max = 1500, message = "检查内容需为 20~1500 字")
    private String text;
}
