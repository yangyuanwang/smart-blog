package com.blog.service;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.blog.common.BizException;
import com.blog.common.ResultCode;

/**
 * Redis 固定窗口限流：登录/留言/点赞/友链申请等写操作的频控统一收口。
 */
@Service
public class RateLimiterService {

    private final StringRedisTemplate redis;

    /**
     * 构造限流服务。
     *
     * @param redis Redis 客户端
     */
    public RateLimiterService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 检查并计数：窗口内首次调用设置过期时间，超过上限抛出限流业务异常。
     *
     * @param name   限流场景名（进入 Redis key）
     * @param key    维度标识（通常是 IP 或 IP+用户名）
     * @param limit  窗口内最大次数
     * @param window 窗口时长
     * @throws BizException 超过上限时抛出 TOO_MANY_REQUESTS 语义异常
     */
    public void check(String name, String key, int limit, Duration window) {
        if (key == null || key.isBlank()) {
            return;
        }
        String redisKey = "blog:rate:" + name + ":" + key;
        Long count = redis.opsForValue().increment(redisKey);
        if (count != null && count == 1L) {
            redis.expire(redisKey, window);
        }
        if (count != null && count > limit) {
            throw new BizException(ResultCode.TOO_MANY_REQUESTS.code(),
                    "操作太频繁，请 " + humanize(window) + "后再试");
        }
    }

    /**
     * 把限流窗口时长转成人话：不足一分钟显示秒，其余显示分钟。
     *
     * @param window 窗口时长
     * @return 形如 "30 秒 " 或 "15 分钟 " 的提示片段
     */
    private String humanize(Duration window) {
        long seconds = window.getSeconds();
        if (seconds < 60) {
            return seconds + " 秒 ";
        }
        long minutes = (seconds + 59) / 60;
        return minutes + " 分钟 ";
    }

    /**
     * 清除指定场景与维度的计数（登录成功后清除失败计数等场景）。
     *
     * @param name 限流场景名
     * @param key  维度标识
     */
    public void clear(String name, String key) {
        redis.delete("blog:rate:" + name + ":" + key);
    }
}
