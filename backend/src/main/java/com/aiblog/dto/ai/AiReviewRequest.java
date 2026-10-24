package com.aiblog.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 全文审阅请求：只转发当前草稿纯文本，不落库。
 */
@Data
public class AiReviewRequest {

    @Size(max = 200, message = "标题不能超过 200 字")
    private String title;

    @NotBlank(message = "content 不能为空")
    @Size(max = 20000, message = "审阅内容不能超过 20000 字")
    private String content;

    @Size(max = 100, message = "文章类型不能超过 100 字")
    private String articleType;

    @Size(max = 200, message = "风格不能超过 200 字")
    private String style;
}
