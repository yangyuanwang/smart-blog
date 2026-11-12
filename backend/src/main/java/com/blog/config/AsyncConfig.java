package com.blog.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 异步与定时任务开关：AI 摘要异步生成、浏览量定时落库依赖本配置。
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
