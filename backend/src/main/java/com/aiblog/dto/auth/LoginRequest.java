package com.aiblog.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 图形验证码 id（GET /api/auth/captcha 返回） */
    @NotBlank(message = "验证码不能为空")
    private String captchaId;

    /** 用户输入的验证码 */
    @NotBlank(message = "验证码不能为空")
    private String captchaCode;
}
