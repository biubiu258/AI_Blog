package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.entity.Article;
import com.aiblog.entity.Comment;
import com.aiblog.entity.Report;
import com.aiblog.entity.User;
import com.aiblog.mapper.ArticleMapper;
import com.aiblog.mapper.CommentMapper;
import com.aiblog.mapper.ReportMapper;
import com.aiblog.mapper.UserMapper;
import com.aiblog.security.CurrentUser;
import com.aiblog.security.LoginUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 举报与处理：用户举报文章/评论，管理端可忽略或删除目标内容。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportMapper reportMapper;
    private final ArticleMapper articleMapper;
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final VectorSyncService vectorSyncService;

    public Map<String, Object> create(String targetType, Long targetId, String reason) {
        if (!"article".equals(targetType) && !"comment".equals(targetType)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "targetType 仅支持 article / comment");
        }
        if ("article".equals(targetType) && articleMapper.selectById(targetId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        if ("comment".equals(targetType) && commentMapper.selectById(targetId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "评论不存在");
        }
        Long userId = CurrentUser.id();
        Long pending = reportMapper.selectCount(Wrappers.<Report>lambdaQuery()
                .eq(Report::getReporterId, userId)
                .eq(Report::getTargetType, targetType)
                .eq(Report::getTargetId, targetId)
                .eq(Report::getStatus, "pending"));
        if (pending != null && pending > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "你已举报过该内容，等待处理");
        }
        Report report = new Report();
        report.setReporterId(userId);
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setReason(reason.strip());
        report.setStatus("pending");
        reportMapper.insert(report);
        return Map.of("id", report.getId());
    }

    public Map<String, Object> adminPage(int page, int size, String status) {
        Page<Report> result = reportMapper.selectPage(Page.of(page, Math.min(size, 50)),
                Wrappers.<Report>lambdaQuery()
                        .eq(org.springframework.util.StringUtils.hasText(status), Report::getStatus, status)
                        .orderByDesc(Report::getCreatedAt));
        List<Report> records = result.getRecords();
        Map<Long, User> reporters = records.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(records.stream().map(Report::getReporterId)
                        .collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Set<Long> articleIds = records.stream()
                .filter(r -> "article".equals(r.getTargetType()))
                .map(Report::getTargetId).collect(Collectors.toSet());
        Map<Long, String> articleTitles = articleIds.isEmpty() ? Map.of()
                : articleMapper.selectBatchIds(articleIds).stream()
                .collect(Collectors.toMap(Article::getId, Article::getTitle));
        Set<Long> commentIds = records.stream()
                .filter(r -> "comment".equals(r.getTargetType()))
                .map(Report::getTargetId).collect(Collectors.toSet());
        Map<Long, String> commentTexts = commentIds.isEmpty() ? Map.of()
                : commentMapper.selectBatchIds(commentIds).stream()
                .collect(Collectors.toMap(Comment::getId, Comment::getContent));

        List<Map<String, Object>> items = records.stream().map(r -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.getId());
            item.put("targetType", r.getTargetType());
            item.put("targetId", r.getTargetId());
            item.put("reason", r.getReason());
            item.put("status", r.getStatus());
            item.put("createdAt", r.getCreatedAt());
            User reporter = reporters.get(r.getReporterId());
            item.put("reporterId", r.getReporterId());
            item.put("reporterNickname", reporter == null ? null : reporter.getNickname());
            item.put("targetTitle", "article".equals(r.getTargetType())
                    ? articleTitles.get(r.getTargetId()) : commentTexts.get(r.getTargetId()));
            return item;
        }).toList();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("total", result.getTotal());
        resp.put("items", items);
        return resp;
    }

    /** action: dismiss 忽略 / delete 删除被举报内容 */
    public void handle(Long id, String action, LoginUser admin) {
        Report report = reportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "举报不存在");
        }
        if (!"pending".equals(report.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该举报已处理");
        }
        if ("delete".equals(action)) {
            if ("article".equals(report.getTargetType())) {
                Article article = articleMapper.selectById(report.getTargetId());
                if (article != null) {
                    articleMapper.deleteById(article.getId());
                    vectorSyncService.delete(article.getId());
                }
            } else {
                commentMapper.deleteById(report.getTargetId());
            }
            report.setStatus("handled");
        } else if ("dismiss".equals(action)) {
            report.setStatus("dismissed");
        } else {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "action 仅支持 dismiss / delete");
        }
        report.setHandledBy(admin.getId());
        report.setHandledAt(LocalDateTime.now());
        reportMapper.updateById(report);
        log.info("举报处理: id={}, action={}, admin={}", id, action, admin.getId());
    }

    public long pendingCount() {
        Long count = reportMapper.selectCount(Wrappers.<Report>lambdaQuery()
                .eq(Report::getStatus, "pending"));
        return Objects.requireNonNullElse(count, 0L);
    }
}
