package com.blog.service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.blog.common.BizException;

/**
 * 邮件服务：仅用于找回密码验证码。SMTP 未配置（host 为空）时所有方法优雅降级。
 */
@Service
@ConditionalOnProperty(prefix = "spring.mail", name = "host")
public class MailService {

    /** 找回密码验证码 Redis key 前缀 */
    private static final String CODE_KEY = "blog:mail:code:";

    /** 注册邮箱验证码 Redis key 前缀 */
    private static final String REG_CODE_KEY = "blog:mail:regcode:";

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redis;
    private final String from;
    private final String host;

    /**
     * 构造邮件服务。
     *
     * @param mailSender Spring Mail 发送器
     * @param redis      Redis 客户端（验证码暂存）
     * @param username   SMTP 用户名（作为发件人）
     * @param smtpHost   SMTP 主机（用于可用性判断）
     */
    public MailService(JavaMailSender mailSender, StringRedisTemplate redis,
                       @Value("${spring.mail.username:}") String username,
                       @Value("${spring.mail.host:}") String smtpHost) {
        this.mailSender = mailSender;
        this.redis = redis;
        this.from = username;
        this.host = smtpHost;
    }

    /**
     * 邮件服务是否真正可用：host 必须非空（Bean 存在不代表 SMTP 已配置）。
     *
     * @return 可用返回 true
     */
    public boolean enabled() {
        return host != null && !host.isBlank();
    }

    /**
     * 发送找回密码验证码：6 位数字，Redis 暂存 10 分钟，重复请求覆盖旧码。
     *
     * @param to 收件邮箱
     * @return 验证码（仅测试用途返回；生产链路中控制器不回传给前端）
     */
    public String sendResetCode(String to) {
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("smart-blog 找回密码验证码");
        message.setText("你的验证码是：" + code + "，10 分钟内有效。若非本人操作请忽略本邮件。");
        try {
            mailSender.send(message);
        } catch (org.springframework.mail.MailException ex) {
            throw new BizException("验证码邮件发送失败，请稍后再试或联系管理员");
        }
        redis.opsForValue().set(CODE_KEY + to, code, Duration.ofMinutes(10));
        return code;
    }

    /**
     * 校验找回密码验证码（校验后立即删除，一次性使用）。
     *
     * @param to   邮箱
     * @param code 用户输入的验证码
     * @return 校验通过返回 true
     */
    public boolean verifyResetCode(String to, String code) {
        String key = CODE_KEY + to;
        String saved = redis.opsForValue().get(key);
        if (saved == null || !saved.equals(code == null ? "" : code.trim())) {
            return false;
        }
        redis.delete(key);
        return true;
    }

    /**
     * 发送注册邮箱验证码：6 位数字，Redis 暂存 10 分钟。
     *
     * @param to 收件邮箱
     * @throws BizException 发送失败（SMTP 不可达等）
     */
    public void sendRegisterCode(String to) {
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("smart-blog 注册验证码");
        message.setText("你的注册验证码是：" + code + "，10 分钟内有效。若非本人操作请忽略本邮件。");
        try {
            mailSender.send(message);
        } catch (org.springframework.mail.MailException ex) {
            throw new BizException("验证码邮件发送失败，请稍后再试");
        }
        redis.opsForValue().set(REG_CODE_KEY + to, code, Duration.ofMinutes(10));
    }

    /**
     * 校验注册邮箱验证码（校验后立即删除，一次性使用）。
     *
     * @param to   邮箱
     * @param code 用户输入的验证码
     * @return 校验通过返回 true
     */
    public boolean verifyRegisterCode(String to, String code) {
        String key = REG_CODE_KEY + to;
        String saved = redis.opsForValue().get(key);
        if (saved == null || !saved.equals(code == null ? "" : code.trim())) {
            return false;
        }
        redis.delete(key);
        return true;
    }
}
