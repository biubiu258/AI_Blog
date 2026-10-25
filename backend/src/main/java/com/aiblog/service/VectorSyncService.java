package com.aiblog.service;

import com.aiblog.config.AiProperties;
import com.aiblog.entity.Article;
import com.aiblog.entity.VectorOutbox;
import com.aiblog.mapper.ArticleMapper;
import com.aiblog.mapper.VectorOutboxMapper;
import com.aiblog.util.HtmlSanitizer;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量同步（outbox 模式）：主业务只写 outbox，定时任务重试推送 Python。
 * 失败不回滚主业务；重试 5 次后标记 failed，便于人工排查。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSyncService {

    private static final int MAX_RETRIES = 5;

    private final WebClient aiWebClient;
    private final AiProperties properties;
    private final VectorOutboxMapper outboxMapper;
    private final ArticleMapper articleMapper;

    public void upsert(Article article) {
        if (!properties.isVectorSyncEnabled() || article == null || article.getId() == null) {
            return;
        }
        enqueue(article.getId(), "upsert");
    }

    public void delete(Long articleId) {
        if (!properties.isVectorSyncEnabled() || articleId == null) {
            return;
        }
        enqueue(articleId, "delete");
    }

    private void enqueue(Long articleId, String action) {
        VectorOutbox outbox = new VectorOutbox();
        outbox.setArticleId(articleId);
        outbox.setAction(action);
        outbox.setStatus("pending");
        outbox.setRetries(0);
        outbox.setNextRetryAt(LocalDateTime.now());
        outboxMapper.insert(outbox);
    }

    /** 每 30 秒处理一批待同步任务；可用 app.vector.worker-interval-ms 调整 */
    @Scheduled(fixedDelayString = "${app.vector.worker-interval-ms:30000}",
            initialDelayString = "${app.vector.worker-initial-delay-ms:15000}")
    public void drain() {
        if (!properties.isVectorSyncEnabled()) {
            return;
        }
        List<VectorOutbox> pending = outboxMapper.selectList(Wrappers.<VectorOutbox>lambdaQuery()
                .eq(VectorOutbox::getStatus, "pending")
                .le(VectorOutbox::getNextRetryAt, LocalDateTime.now())
                .orderByAsc(VectorOutbox::getId)
                .last("LIMIT 20"));
        for (VectorOutbox task : pending) {
            process(task);
        }
    }

    private void process(VectorOutbox task) {
        try {
            if ("delete".equals(task.getAction())) {
                pushDelete(task.getArticleId());
            } else {
                Article article = articleMapper.selectById(task.getArticleId());
                if (article == null || !"published".equals(article.getStatus())) {
                    pushDelete(task.getArticleId());
                } else {
                    pushUpsert(article);
                }
            }
            task.setStatus("done");
            outboxMapper.updateById(task);
            log.info("向量同步成功 action={} articleId={}", task.getAction(), task.getArticleId());
        } catch (Exception e) {
            int retries = (task.getRetries() == null ? 0 : task.getRetries()) + 1;
            task.setRetries(retries);
            if (retries >= MAX_RETRIES) {
                task.setStatus("failed");
                log.warn("向量同步失败已达上限 action={} articleId={}: {}",
                        task.getAction(), task.getArticleId(), e.getMessage());
            } else {
                long delaySeconds = Math.min(30L * (1L << retries), 1800L);
                task.setNextRetryAt(LocalDateTime.now().plusSeconds(delaySeconds));
                log.warn("向量同步失败，{}s 后重试({}/{}) articleId={}: {}",
                        delaySeconds, retries, MAX_RETRIES, task.getArticleId(), e.getMessage());
            }
            outboxMapper.updateById(task);
        }
    }

    private void pushUpsert(Article article) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("article_id", String.valueOf(article.getId()));
        body.put("title", article.getTitle() == null ? "" : article.getTitle());
        body.put("content", HtmlSanitizer.toPlainText(article.getContentHtml()));
        aiWebClient.post()
                .uri("/v1/vectors/upsert")
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    private void pushDelete(Long articleId) {
        aiWebClient.delete()
                .uri("/v1/vectors/{id}", articleId)
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}
