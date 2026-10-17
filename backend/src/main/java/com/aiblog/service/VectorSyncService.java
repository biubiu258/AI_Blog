package com.aiblog.service;

import com.aiblog.config.AiProperties;
import com.aiblog.entity.Article;
import com.aiblog.util.HtmlSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流程三（向量同步，异步）：文章发布/更新/删除时通知 Python 维护 Chroma 向量。
 * 失败不回滚主业务，仅记录日志并允许事后重放（架构 3.4）。
 * P0 阶段 ai.vector-sync-enabled=false 时不触发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSyncService {

    private final WebClient aiWebClient;
    private final AiProperties properties;

    @Async
    public void upsert(Article article) {
        if (!properties.isVectorSyncEnabled() || article == null || article.getId() == null) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("article_id", String.valueOf(article.getId()));
        body.put("title", article.getTitle() == null ? "" : article.getTitle());
        body.put("content", HtmlSanitizer.toPlainText(article.getContentHtml()));
        try {
            aiWebClient.post()
                    .uri("/v1/vectors/upsert")
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            log.info("向量同步成功 upsert articleId={}", article.getId());
        } catch (Exception e) {
            log.warn("向量同步失败 upsert articleId={}, 可事后重放: {}", article.getId(), e.getMessage());
        }
    }

    @Async
    public void delete(Long articleId) {
        if (!properties.isVectorSyncEnabled() || articleId == null) {
            return;
        }
        try {
            aiWebClient.delete()
                    .uri("/v1/vectors/{id}", articleId)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            log.info("向量同步成功 delete articleId={}", articleId);
        } catch (Exception e) {
            log.warn("向量同步失败 delete articleId={}, 可事后重放: {}", articleId, e.getMessage());
        }
    }
}
