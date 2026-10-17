package com.aiblog.dto.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategorySaveRequest {

    @NotBlank(message = "分类名不能为空")
    @Size(max = 64, message = "分类名最长 64 个字符")
    private String name;

    @Size(max = 255, message = "描述最长 255 个字符")
    private String description;
}
