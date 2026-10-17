package com.aiblog.controller;

import com.aiblog.common.ApiResponse;
import com.aiblog.dto.auth.UserInfoResponse;
import com.aiblog.dto.user.PreferenceRequest;
import com.aiblog.dto.user.UpdateUserRequest;
import com.aiblog.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<UserInfoResponse> me() {
        return ApiResponse.ok(userService.me());
    }

    @PutMapping("/me")
    public ApiResponse<UserInfoResponse> updateMe(@Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.ok(userService.updateMe(request));
    }

    /** 写作偏好（P1）：GET 读取，PUT 保存，随生成请求传给 Python */
    @GetMapping("/preference")
    public ApiResponse<PreferenceRequest> getPreference() {
        return ApiResponse.ok(userService.getMyPreference());
    }

    @PutMapping("/preference")
    public ApiResponse<PreferenceRequest> updatePreference(@Valid @RequestBody PreferenceRequest request) {
        return ApiResponse.ok(userService.updateMyPreference(request));
    }
}
