package com.aiblog.controller;

import com.aiblog.common.ApiResponse;
import com.aiblog.dto.auth.LoginRequest;
import com.aiblog.dto.auth.LoginResponse;
import com.aiblog.dto.auth.RegisterRequest;
import com.aiblog.dto.auth.UserInfoResponse;
import com.aiblog.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ApiResponse<UserInfoResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        // JWT 无状态：登出由前端丢弃令牌即可，后端返回成功
        return ApiResponse.ok();
    }
}
