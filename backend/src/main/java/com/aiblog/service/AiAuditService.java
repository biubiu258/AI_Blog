package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.entity.AiOutput;
import com.aiblog.mapper.AiOutputMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * AI 输出审计：所有 AI 返回内容先落 ai_output，再回到业务链路。
 * 写入失败只记应用日志，绝不阻断生成/审阅/搜索主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAuditService {

    public static final String STATUS_STREAMING = "streaming";
    public static final String REVIEW_PENDING = "pending";
    public static final String REVIEW_PASSED = "passed";
    public static final String REVIEW_FLAGGED = "flagged";

    private static final Set<String> REVIEW_STATUSES =
            Set.of(REVIEW_PENDING, REVIEW_PASSED, REVIEW_FLAGGED);
    private static final int MAX_REQUEST_SNAPSHOT = 20000;
    private static final int MAX_PREVIEW = 300;

    private final AiOutputMapper outputMapper;
    private final ObjectMapper objectMapper;

    /**
     * 调用开始即建记录（status=streaming），保证即使进程中断也有调用痕迹。
     *
     * @return 审计记录 id；写入失败返回 null
     */
    public Long begin(String traceId, Long userId, Long articleId,
                      String action, String task, Object request) {
        try {
            String json = writeJson(request);
            AiOutput output = new AiOutput();
            output.setTraceId(traceId);
            output.setUserId(userId);
            output.setArticleId(articleId);
            output.setAction(action);
            output.setTask(task);
            output.setSource("python");
            output.setRequestDigest(sha256(json));
            output.setRequestSnapshot(truncate(json, MAX_REQUEST_SNAPSHOT));
            output.setOutputLength(0);
            output.setStatus(STATUS_STREAMING);
            output.setReviewStatus(REVIEW_PENDING);
            output.setTokensIn(0);
            output.setTokensOut(0);
            output.setDurationMs(0);
            output.setAdopted(0);
            outputMapper.insert(output);
            return output.getId();
        } catch (Exception e) {
            log.error("AI 审计记录创建失败 traceId={} action={}", traceId, action, e);
            return null;
        }
    }

    /** 调用结束：写入返回内容、终态、用量与模型元数据。 */
    public void finish(Long id, AiResult result) {
        if (id == null || result == null) {
            return;
        }
        try {
            String content = result.output() == null ? "" : result.output();
            AiOutput patch = new AiOutput();
            patch.setId(id);
            patch.setOutput(content);
            patch.setOutputPreview(preview(content));
            patch.setOutputLength(content.length());
            patch.setStatus(result.status());
            patch.setSource(result.source());
            patch.setErrorCode(result.errorCode());
            patch.setErrorMessage(truncate(result.errorMessage(), 500));
            patch.setTokensIn(result.tokensIn());
            patch.setTokensOut(result.tokensOut());
            patch.setDurationMs((int) result.durationMs());
            patch.setModel(result.model());
            patch.setPromptVersion(result.promptVersion());
            patch.setFinishedAt(LocalDateTime.now());
            outputMapper.updateById(patch);
        } catch (Exception e) {
            log.error("AI 审计结果写入失败 id={} status={}", id, result.status(), e);
        }
    }

    /** 建议被采纳：同步标记最近一次同动作输出，便于审核时区分实际使用情况。 */
    public void markLatestAdopted(Long userId, String action) {
        try {
            AiOutput latest = outputMapper.selectOne(Wrappers.<AiOutput>lambdaQuery()
                    .eq(AiOutput::getUserId, userId)
                    .eq(AiOutput::getAction, action)
                    .orderByDesc(AiOutput::getId)
                    .last("LIMIT 1"));
            if (latest != null && (latest.getAdopted() == null || latest.getAdopted() == 0)) {
                AiOutput patch = new AiOutput();
                patch.setId(latest.getId());
                patch.setAdopted(1);
                outputMapper.updateById(patch);
            }
        } catch (Exception e) {
            log.warn("AI 采纳标记写入失败 userId={} action={}: {}", userId, action, e.getMessage());
        }
    }

    /**
     * 为最近一次尚未关联文章的审计记录补上 articleId。
     * 典型场景：发布时的关怀检测发生在文章入库之前。
     */
    public void attachArticle(Long userId, String action, Long articleId) {
        if (userId == null || articleId == null) {
            return;
        }
        try {
            AiOutput latest = outputMapper.selectOne(Wrappers.<AiOutput>lambdaQuery()
                    .eq(AiOutput::getUserId, userId)
                    .eq(AiOutput::getAction, action)
                    .isNull(AiOutput::getArticleId)
                    .orderByDesc(AiOutput::getId)
                    .last("LIMIT 1"));
            if (latest != null) {
                AiOutput patch = new AiOutput();
                patch.setId(latest.getId());
                patch.setArticleId(articleId);
                outputMapper.updateById(patch);
            }
        } catch (Exception e) {
            log.warn("AI 审计文章关联失败 userId={} action={} articleId={}: {}",
                    userId, action, articleId, e.getMessage());
        }
    }

    public Map<String, Object> page(int page, int size, String action, String status,
                                    String reviewStatus, String keyword, Long userId) {
        Page<AiOutput> result = outputMapper.selectPage(
                Page.of(Math.max(page, 1), Math.min(Math.max(size, 1), 50)),
                Wrappers.<AiOutput>lambdaQuery()
                        .select(AiOutput::getId, AiOutput::getTraceId, AiOutput::getUserId,
                                AiOutput::getArticleId, AiOutput::getAction, AiOutput::getTask,
                                AiOutput::getSource, AiOutput::getModel, AiOutput::getStatus,
                                AiOutput::getOutputPreview, AiOutput::getOutputLength,
                                AiOutput::getErrorCode, AiOutput::getErrorMessage,
                                AiOutput::getReviewStatus, AiOutput::getReviewedAt,
                                AiOutput::getAdopted, AiOutput::getDurationMs,
                                AiOutput::getCreatedAt)
                        .eq(StringUtils.hasText(action), AiOutput::getAction, action)
                        .eq(StringUtils.hasText(status), AiOutput::getStatus, status)
                        .eq(StringUtils.hasText(reviewStatus), AiOutput::getReviewStatus, reviewStatus)
                        .eq(userId != null, AiOutput::getUserId, userId)
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(AiOutput::getOutputPreview, keyword)
                                .or().like(AiOutput::getTraceId, keyword)
                                .or().like(AiOutput::getErrorMessage, keyword))
                        .orderByDesc(AiOutput::getId));
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("total", result.getTotal());
        resp.put("items", result.getRecords());
        return resp;
    }

    public AiOutput detail(Long id) {
        AiOutput output = outputMapper.selectById(id);
        if (output == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "AI 输出记录不存在");
        }
        return output;
    }

    /** 审核：passed 通过 / flagged 标记问题 / pending 重置待审。 */
    public AiOutput review(Long id, Long adminId, String status, String note) {
        if (!StringUtils.hasText(status) || !REVIEW_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "审核状态仅支持 pending/passed/flagged");
        }
        detail(id);
        AiOutput patch = new AiOutput();
        patch.setId(id);
        patch.setReviewStatus(status);
        patch.setReviewNote(truncate(note, 500));
        patch.setReviewedBy(adminId);
        patch.setReviewedAt(LocalDateTime.now());
        outputMapper.updateById(patch);
        return detail(id);
    }

    public long pendingCount() {
        return outputMapper.selectCount(Wrappers.<AiOutput>lambdaQuery()
                .eq(AiOutput::getReviewStatus, REVIEW_PENDING));
    }

    private String writeJson(Object value) {
        if (value == null) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private static String preview(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String flat = content.replaceAll("\\s+", " ").trim();
        return truncate(flat, MAX_PREVIEW);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 结束态写入参数。
     *
     * @param output       AI 返回内容全文
     * @param status       success / failed / canceled
     * @param source       python / mock / fallback
     * @param errorCode    失败码（成功可为空）
     * @param errorMessage 失败原因（成功可为空）
     * @param tokensIn     输入 token
     * @param tokensOut    输出 token
     * @param durationMs   耗时
     * @param model        模型标识
     * @param promptVersion 提示词版本
     */
    public record AiResult(String output, String status, String source,
                           String errorCode, String errorMessage,
                           int tokensIn, int tokensOut, long durationMs,
                           String model, String promptVersion) {

        public static AiResult success(String output, String source, int tokensIn, int tokensOut,
                                       long durationMs, String model, String promptVersion) {
            return new AiResult(output, "success", source, null, null,
                    tokensIn, tokensOut, durationMs, model, promptVersion);
        }

        public static AiResult failed(String output, String source, String errorCode,
                                      String errorMessage, long durationMs) {
            return new AiResult(output, "failed", source, errorCode, errorMessage,
                    0, 0, durationMs, null, null);
        }

        public static AiResult canceled(String output, String source, long durationMs) {
            return new AiResult(output, "canceled", source, null, null,
                    0, 0, durationMs, null, null);
        }
    }
}
