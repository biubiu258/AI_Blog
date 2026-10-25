package com.aiblog.service;

import com.aiblog.entity.Article;
import com.aiblog.mapper.ArticleMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时发布：每分钟扫描 status=scheduled 且到期的文章。
 * 发布时重跑人文关怀检测，high 转为仅作者可见（与手动发布一致）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleScheduler {

    private final ArticleMapper articleMapper;
    private final CareService careService;
    private final VectorSyncService vectorSyncService;
    private final NotificationService notificationService;

    @Scheduled(fixedDelayString = "${app.schedule.scan-interval-ms:60000}",
            initialDelayString = "${app.schedule.scan-initial-delay-ms:20000}")
    public void publishDueArticles() {
        List<Article> due = articleMapper.selectList(Wrappers.<Article>lambdaQuery()
                .eq(Article::getStatus, "scheduled")
                .le(Article::getScheduledAt, LocalDateTime.now())
                .orderByAsc(Article::getScheduledAt)
                .last("LIMIT 20"));
        for (Article article : due) {
            CareService.CareResult care = careService.check(
                    article.getAuthorId(), article.getTitle(), article.getContentHtml());
            article.setScheduledAt(null);
            article.setPublishedAt(LocalDateTime.now());
            article.setStatus(care.high() ? "private" : "published");
            articleMapper.updateById(article);
            careService.record(article.getAuthorId(), article.getId(), care.level());
            if ("published".equals(article.getStatus())) {
                vectorSyncService.upsert(article);
                notificationService.notify(article.getAuthorId(), "system", null,
                        article.getId(), null, "定时发布成功：《" + article.getTitle() + "》");
            }
            log.info("定时发布: articleId={}, status={}", article.getId(), article.getStatus());
        }
    }
}
