package com.aiblog.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名最长 64 个字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在 6 到 64 位之间")
    private String password;

    @Size(max = 64, message = "昵称最长 64 个字符")
    private String nickname;

    @Size(max = 128, message = "邮箱最长 128 个字符")
    private String email;
}
