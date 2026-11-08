package com.aiblog.dto.tech;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 粘贴入库请求：正文以纯文本/Markdown 提交，由 Python 解析切块。
 */
@Data
public class TechDocRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题最长 200 个字符")
    private String title;

    /** article / guide / faq / api / spec */
    @Size(max = 32, message = "文档类型最长 32 个字符")
    private String docType;

    @Size(max = 200, message = "来源名称最长 200 个字符")
    private String sourceName;

    @Size(max = 500, message = "来源链接最长 500 个字符")
    private String sourceUrl;

    @Size(max = 64, message = "版本号最长 64 个字符")
    private String version;

    @Size(max = 120, message = "授权方式最长 120 个字符")
    private String license;

    /** markdown / text */
    @Size(max = 16, message = "文件类型最长 16 个字符")
    private String fileType;

    @NotBlank(message = "正文不能为空")
    @Size(max = 500_000, message = "正文过长")
    private String content;
}
