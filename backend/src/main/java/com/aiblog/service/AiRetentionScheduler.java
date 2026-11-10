package com.aiblog.service;

import com.aiblog.entity.AiOutput;
import com.aiblog.entity.ContentLabelLog;
import com.aiblog.mapper.AiOutputMapper;
import com.aiblog.mapper.ContentLabelLogMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 审计数据留存：《人工智能生成合成内容标识办法》要求日志留存不少于 6 个月。
 * 每日清理一次，只删除超出留存期且已完成人工审核的记录；待审记录永远保留。
 * 标识变更日志是合规证据，保留期更长（12 个月）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiRetentionScheduler {

    static final int AI_OUTPUT_RETENTION_MONTHS = 6;
    static final int LABEL_LOG_RETENTION_MONTHS = 12;

    private final AiOutputMapper aiOutputMapper;
    private final ContentLabelLogMapper contentLabelLogMapper;

    @Scheduled(cron = "${app.schedule.retention-cron:0 0 3 * * ?}")
    public void purgeExpiredAuditRecords() {
        LocalDateTime now = LocalDateTime.now();

        int outputs = aiOutputMapper.delete(Wrappers.<AiOutput>lambdaQuery()
                .lt(AiOutput::getCreatedAt, now.minusMonths(AI_OUTPUT_RETENTION_MONTHS))
                .ne(AiOutput::getReviewStatus, "pending"));
        if (outputs > 0) {
            log.info("清理超期 AI 审计记录: {} 条（留存 {} 个月，待审记录不受影响）",
                    outputs, AI_OUTPUT_RETENTION_MONTHS);
        }

        int labelLogs = contentLabelLogMapper.delete(Wrappers.<ContentLabelLog>lambdaQuery()
                .lt(ContentLabelLog::getCreatedAt, now.minusMonths(LABEL_LOG_RETENTION_MONTHS)));
        if (labelLogs > 0) {
            log.info("清理超期标识变更日志: {} 条（留存 {} 个月）",
                    labelLogs, LABEL_LOG_RETENTION_MONTHS);
        }
    }
}
