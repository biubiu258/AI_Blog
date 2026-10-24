package com.aiblog.dto.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommentCreateRequest {

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 2000, message = "评论最长 2000 个字符")
    private String content;

    /** 回复某条评论时传入其 id */
    private Long parentId;
}
