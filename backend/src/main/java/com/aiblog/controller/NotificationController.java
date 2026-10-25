package com.aiblog.controller;

import com.aiblog.common.ApiResponse;
import com.aiblog.security.CurrentUser;
import com.aiblog.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 站内通知：前端轮询未读数与列表。
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) String type) {
        return ApiResponse.ok(notificationService.page(CurrentUser.id(), page, size, unreadOnly, type));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Object>> unreadCount() {
        return ApiResponse.ok(Map.of("count", notificationService.unreadCount(CurrentUser.id())));
    }

    /** ids 为空表示全部已读 */
    @PostMapping("/read")
    public ApiResponse<Void> markRead(@RequestBody(required = false) ReadRequest request) {
        List<Long> ids = request == null ? null : request.ids();
        notificationService.markRead(CurrentUser.id(), ids);
        return ApiResponse.ok();
    }

    public record ReadRequest(List<Long> ids) {}
}
