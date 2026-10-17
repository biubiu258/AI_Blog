package com.aiblog.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 启用 @Async：向量同步等旁路任务异步执行，不阻塞主业务。
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
