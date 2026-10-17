package com.aiblog.controller;

import com.aiblog.common.ApiResponse;
import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.dto.admin.AdminUserItem;
import com.aiblog.entity.User;
import com.aiblog.mapper.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端：用户列表与封禁/解封（需求 1.2）。
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserMapper userMapper;

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        Page<User> result = userMapper.selectPage(Page.of(page, Math.min(size, 50)),
                Wrappers.<User>lambdaQuery()
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(User::getUsername, keyword)
                                .or().like(User::getNickname, keyword))
                        .orderByAsc(User::getId));
        return ApiResponse.ok(Map.of(
                "total", result.getTotal(),
                "items", result.getRecords().stream().map(AdminUserItem::from).toList()));
    }

    /** 封禁（status=1）/ 解封（status=0），不允许操作管理员账号 */
    @PutMapping("/{id}/status")
    public ApiResponse<Void> setStatus(@PathVariable Long id, @RequestBody StatusRequest request) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if ("ADMIN".equals(user.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能封禁管理员账号");
        }
        if (request.getStatus() == null || (request.getStatus() != 0 && request.getStatus() != 1)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "status 仅支持 0（正常）或 1（封禁）");
        }
        user.setStatus(request.getStatus());
        userMapper.updateById(user);
        return ApiResponse.ok();
    }

    @Data
    public static class StatusRequest {
        private Integer status;
    }
}
