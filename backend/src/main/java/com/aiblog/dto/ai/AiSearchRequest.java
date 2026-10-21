package com.aiblog.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 语义搜索请求（契约 5.2）：query 必填且 <= 200；topK 可选，默认 10，上限由配置钳制。 */
@Data
public class AiSearchRequest {

    @NotBlank(message = "query 不能为空")
    @Size(max = 200, message = "query 长度不能超过 200")
    private String query;

    private Integer topK;
}
