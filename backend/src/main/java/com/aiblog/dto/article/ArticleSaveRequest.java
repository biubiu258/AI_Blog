package com.aiblog.dto.article;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建/更新文章请求。status 取 draft / published / scheduled。
 */
@Data
public class ArticleSaveRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题最长 200 个字符")
    private String title;

    private Long categoryId;

    /** 富文本正文（后端做白名单清洗后入库） */
    @Size(max = 500_000, message = "正文过长")
    private String contentHtml;

    @Size(max = 500, message = "摘要最长 500 个字符")
    private String summary;

    @NotBlank(message = "状态不能为空")
    private String status;

    /** 标签，最多 8 个；为空数组表示清空 */
    private List<String> tags;

    /** status=scheduled 时必填 */
    private LocalDateTime scheduledAt;
}
