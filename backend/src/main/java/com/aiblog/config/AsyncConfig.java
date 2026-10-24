package com.aiblog.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启用 @Async 与 @Scheduled：向量同步、定时发布等旁路任务不阻塞主业务。
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
