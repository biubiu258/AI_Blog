package com.aiblog.dto.tech;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 术语新增/修改请求。
 */
@Data
public class TechTermRequest {

    @NotBlank(message = "规范写法不能为空")
    @Size(max = 120, message = "规范写法最长 120 个字符")
    private String canonical;

    @Size(max = 64, message = "术语分类最长 64 个字符")
    private String category;

    @Size(max = 1000, message = "释义最长 1000 个字符")
    private String definition;

    @Size(max = 1000, message = "变体写法最长 1000 个字符")
    private String variants;

    private Boolean caseSensitive;

    /** active / disabled */
    @Size(max = 16, message = "状态取值非法")
    private String status;
}
