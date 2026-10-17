package com.aiblog.controller;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.aiblog.common.ApiResponse;
import com.aiblog.dto.auth.LoginRequest;
import com.aiblog.dto.auth.LoginResponse;
import com.aiblog.dto.auth.RegisterRequest;
import com.aiblog.dto.auth.UserInfoResponse;
import com.aiblog.security.CaptchaStore;
import com.aiblog.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CaptchaStore captchaStore;

    /** 开发/测试辅助：回显验证码明文，默认关闭；仅自动化测试时以启动参数开启 */
    @Value("${app.captcha.echo-code:false}")
    private boolean echoCaptchaCode;

    @PostMapping("/register")
    public ApiResponse<UserInfoResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest http) {
        return ApiResponse.ok(authService.login(request, clientIp(http)));
    }

    /** 登录图形验证码：返回一次性 captchaId 与 base64 图片，2 分钟有效 */
    @GetMapping("/captcha")
    public ApiResponse<Map<String, String>> captcha() {
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(120, 40, 4, 14);
        String captchaId = captchaStore.create(captcha.getCode());
        Map<String, String> data = new java.util.HashMap<>();
        data.put("captchaId", captchaId);
        data.put("image", captcha.getImageBase64Data());
        if (echoCaptchaCode) {
            data.put("code", captcha.getCode());
        }
        return ApiResponse.ok(data);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        // JWT 无状态：登出由前端丢弃令牌即可，后端返回成功
        return ApiResponse.ok();
    }

    /** 取真实客户端 IP：优先 X-Forwarded-For 首段（Nginx 场景），否则远端地址 */
    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
