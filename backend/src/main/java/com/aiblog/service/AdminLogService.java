package com.aiblog.service;

import com.aiblog.entity.AdminLog;
import com.aiblog.mapper.AdminLogMapper;
import com.aiblog.security.LoginUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 管理员操作审计：业务点埋点写入 admin_log，提供分页查询。
 * 审计写入失败不阻断业务，仅记录应用日志。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminLogService {

    private final AdminLogMapper mapper;
    private final ObjectMapper objectMapper;

    public void record(LoginUser admin, String action, String target, Object detail) {
        try {
            AdminLog entry = new AdminLog();
            entry.setAdminId(admin.getId());
            entry.setAdminUsername(admin.getUsername());
            entry.setAction(action);
            entry.setTarget(target);
            entry.setDetail(objectMapper.writeValueAsString(detail));
            mapper.insert(entry);
        } catch (Exception e) {
            log.error("审计日志写入失败 action={} target={}", action, target, e);
        }
    }

    public Map<String, Object> page(int page, int size, String action) {
        Page<AdminLog> result = mapper.selectPage(Page.of(page, Math.min(size, 50)),
                Wrappers.<AdminLog>lambdaQuery()
                        .eq(StringUtils.hasText(action), AdminLog::getAction, action)
                        .orderByDesc(AdminLog::getId));
        return Map.of("total", result.getTotal(), "items", result.getRecords());
    }
}
