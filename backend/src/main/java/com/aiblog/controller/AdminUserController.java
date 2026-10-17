package com.aiblog.controller;

import com.aiblog.common.ApiResponse;
import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.dto.admin.AdminUserItem;
import com.aiblog.entity.User;
import com.aiblog.mapper.UserMapper;
import com.aiblog.security.CurrentUser;
import com.aiblog.service.AdminLogService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.annotation.JsonFormat;
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

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 管理端：用户列表、封禁/解封、角色分配（需求 1.2 + 风控增强）。
 * 仅超级管理员（ADMIN）可访问；内容管理员（CONTENT_ADMIN）无权进入。
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserMapper userMapper;
    private final AdminLogService adminLogService;

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        Page<User> result = userMapper.selectPage(Page.of(page, Math.min(size, 50)),
                Wrappers.<User>lambdaQuery()
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(User::getUsername, keyword)
                                .or().like(User::getNickname, keyword))
                        .eq(status != null, User::getStatus, status)
                        .orderByAsc(User::getId));
        return ApiResponse.ok(Map.of(
                "total", result.getTotal(),
                "items", result.getRecords().stream().map(AdminUserItem::from).toList()));
    }

    /**
     * 封禁（status=1：必填原因，可选期限，NULL=永久）/ 解封（status=0）。
     * 不允许操作超级管理员账号；CONTENT_ADMIN 可被超级管理员封禁。
     */
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
        if (request.getStatus() == 1) {
            if (!StringUtils.hasText(request.getReason())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "封禁必须填写原因");
            }
            user.setStatus(1);
            user.setBanReason(request.getReason().trim());
            user.setBannedUntil(request.getUntil());
        } else {
            user.setStatus(0);
            user.setBanReason(null);
            user.setBannedUntil(null);
        }
        userMapper.updateById(user);
        adminLogService.record(CurrentUser.get(), request.getStatus() == 1 ? "BAN" : "UNBAN",
                "user:" + id, Map.of(
                        "reason", user.getBanReason() == null ? "" : user.getBanReason(),
                        "until", user.getBannedUntil() == null ? "" : user.getBannedUntil().toString()));
        return ApiResponse.ok();
    }

    /**
     * 角色调整：仅允许 USER <-> CONTENT_ADMIN。
     * 防提权：ADMIN 不能经界面授予/收回，不能修改自己的角色，全部变更入审计。
     */
    @PutMapping("/{id}/role")
    public ApiResponse<Void> setRole(@PathVariable Long id, @RequestBody RoleRequest request) {
        if (!"USER".equals(request.getRole()) && !"CONTENT_ADMIN".equals(request.getRole())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "仅支持调整为 USER 或 CONTENT_ADMIN");
        }
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if ("ADMIN".equals(user.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能修改超级管理员角色");
        }
        if (user.getId().equals(CurrentUser.get().getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能修改自己的角色");
        }
        String from = user.getRole();
        user.setRole(request.getRole());
        userMapper.updateById(user);
        adminLogService.record(CurrentUser.get(), "SET_ROLE", "user:" + id,
                Map.of("from", from == null ? "" : from, "to", request.getRole()));
        return ApiResponse.ok();
    }

    @Data
    public static class StatusRequest {
        private Integer status;
        /** 封禁原因，封禁时必填 */
        private String reason;
        /** 封禁截止时间，空 = 永久 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
        private LocalDateTime until;
    }

    @Data
    public static class RoleRequest {
        private String role;
    }
}
