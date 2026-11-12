package com.blog.service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.blog.common.Texts;
import com.blog.entity.Article;
import com.blog.mapper.ArticleMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 浏览量 Redis 缓冲计数：访问先写 Redis（会话去重），定时批量落库，
 * 避免热门文章每次浏览都打一次 UPDATE。
 */
@Slf4j
@Service
public class ViewCounterService {

    /** 浏览量缓冲 key 前缀：blog:view:{aid} */
    private static final String VIEW_KEY = "blog:view:";

    /** 会话去重 key 前缀：blog:viewed:{aid}:{sid}，TTL 6 小时 */
    private static final String VIEWED_KEY = "blog:viewed:";

    private final ArticleMapper articleMapper;
    private final StringRedisTemplate redis;

    /**
     * 构造浏览量计数服务。
     *
     * @param articleMapper 文章 Mapper
     * @param redis         Redis 客户端
     */
    public ViewCounterService(ArticleMapper articleMapper, StringRedisTemplate redis) {
        this.articleMapper = articleMapper;
        this.redis = redis;
    }

    /**
     * 记录一次浏览：同一浏览器会话 6 小时内只计一次，命中去重后累加 Redis 计数。
     *
     * @param articleId 文章 ID
     * @param sessionId 浏览器会话标识（前端生成，稳定存于本地）
     * @return 本次是否计入
     */
    public boolean record(Integer articleId, String sessionId) {
        if (articleId == null || Texts.blank(sessionId)) {
            return false;
        }
        String viewedKey = VIEWED_KEY + articleId + ":" + Texts.cut(sessionId, 64);
        Boolean first = redis.opsForValue().setIfAbsent(viewedKey, "1", Duration.ofHours(6));
        if (!Boolean.TRUE.equals(first)) {
            return false;
        }
        redis.opsForValue().increment(VIEW_KEY + articleId);
        return true;
    }

    /**
     * 查询某文章的 Redis 未落库增量（详情页展示用，叠加数据库存量）。
     *
     * @param articleId 文章 ID
     * @return Redis 中的未落库浏览数，无缓冲时为 0
     */
    public long buffered(Integer articleId) {
        String value = redis.opsForValue().get(VIEW_KEY + articleId);
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    /**
     * 定时批量落库：每 5 分钟 SCAN 全部缓冲 key，GETDEL 后按文章累计更新数据库。
     */
    @Scheduled(fixedDelay = 300_000L, initialDelay = 300_000L)
    public void flush() {
        Map<Integer, Long> pending = new HashMap<Integer, Long>();
        try (var cursor = redis.scan(ScanOptions.scanOptions().match(VIEW_KEY + "*").count(200).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                String value = redis.opsForValue().getAndDelete(key);
                if (value == null) {
                    continue;
                }
                try {
                    long delta = Long.parseLong(value);
                    if (delta <= 0) {
                        continue;
                    }
                    Integer aid = Integer.valueOf(key.substring(VIEW_KEY.length()));
                    pending.merge(aid, delta, Long::sum);
                } catch (NumberFormatException ex) {
                    log.warn("跳过非法浏览量缓冲 key: {}", key);
                }
            }
        }
        for (Map.Entry<Integer, Long> entry : pending.entrySet()) {
            articleMapper.update(null, new UpdateWrapper<Article>()
                    .eq("aid", entry.getKey())
                    .setSql("visit_count = IFNULL(visit_count,0) + " + entry.getValue()));
        }
        if (!pending.isEmpty()) {
            log.info("浏览量批量落库完成：{} 篇文章，共 {} 次", pending.size(),
                    pending.values().stream().mapToLong(Long::longValue).sum());
        }
    }
}
