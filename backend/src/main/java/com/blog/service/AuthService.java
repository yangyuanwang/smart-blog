package com.blog.service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.blog.common.BizException;
import com.blog.common.ResultCode;
import com.blog.common.Texts;
import com.blog.entity.LoginLog;
import com.blog.entity.User;
import com.blog.mapper.LoginLogMapper;
import com.blog.mapper.UserMapper;
import com.blog.security.JwtService;
import com.blog.security.LoginUser;

/**
 * 认证服务：登录（BCrypt 校验 + 历史 MD5 登录期自动升级）、注册、双令牌签发与轮换、
 * 邮箱验证码找回密码。所有写操作带 Redis 限流，登录日志沿用 t_login_log。
 */
@Service
public class AuthService {

    /** 登录失败限流场景名：5 次 / 15 分钟 / IP+用户名 */
    private static final String RATE_LOGIN_FAIL = "login-fail";

    /** 登录失败锁定阈值 */
    private static final int LOGIN_FAIL_LIMIT = 5;

    /** 图形验证码 Redis key 前缀 */
    private static final String CAPTCHA_KEY = "blog:captcha:";

    private final UserMapper userMapper;
    private final LoginLogMapper loginLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RateLimiterService rateLimiter;
    private final SettingService settingService;
    private final StringRedisTemplate redis;
    private final boolean captchaEnabled;

    /**
     * 构造认证服务。
     *
     * @param userMapper     用户 Mapper
     * @param loginLogMapper 登录日志 Mapper
     * @param passwordEncoder BCrypt 编码器
     * @param jwtService     JWT 服务
     * @param rateLimiter    限流服务
     * @param settingService 站点设置（注册开关、验证码开关）
     * @param redis          Redis 客户端（验证码暂存）
     * @param captchaEnabled 是否要求登录验证码
     */
    public AuthService(UserMapper userMapper, LoginLogMapper loginLogMapper, PasswordEncoder passwordEncoder,
                       JwtService jwtService, RateLimiterService rateLimiter, SettingService settingService,
                       StringRedisTemplate redis,
                       @Value("${blog.security.captcha-enabled}") boolean captchaEnabled) {
        this.userMapper = userMapper;
        this.loginLogMapper = loginLogMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
        this.settingService = settingService;
        this.redis = redis;
        this.captchaEnabled = captchaEnabled;
    }

    /**
     * 登录结果：令牌对 + 用户公开信息。
     *
     * @param accessToken  访问令牌
     * @param refreshToken 刷新令牌
     * @param user         用户公开视图（不含密码）
     */
    public record LoginResult(String accessToken, String refreshToken, User user) {
    }

    /**
     * 登录：验证码（可选）→ 限流 → 口令校验（BCrypt 或历史 MD5 自动升级）→ 签发双令牌。
     *
     * @param username  用户名
     * @param password  明文密码
     * @param captchaId 验证码 ID（开关开启时必填）
     * @param captchaCode 用户输入的验证码
     * @param ip        客户端 IP
     * @param adminOnly 是否要求管理员角色（后台登录用）
     * @return 令牌对与用户公开视图
     */
    public LoginResult login(String username, String password, String captchaId, String captchaCode,
                             String ip, boolean adminOnly) {
        String name = Texts.trim(username);
        verifyCaptcha(captchaId, captchaCode);
        String rateKey = ip + "|" + name;
        rateLimiter.check(RATE_LOGIN_FAIL, rateKey, LOGIN_FAIL_LIMIT, Duration.ofMinutes(15));
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", name));
        if (user == null || !matchAndUpgrade(user, password)) {
            writeLog(user == null ? null : user.getUid(), name, ip, false, "账号或密码错误");
            throw new BizException("账号或密码错误");
        }
        if ("0".equals(user.getState())) {
            writeLog(user.getUid(), name, ip, false, "账号已锁定");
            throw new BizException("账号已锁定");
        }
        if (adminOnly && !isAdmin(user)) {
            writeLog(user.getUid(), name, ip, false, "没有后台权限");
            throw new BizException("没有后台权限");
        }
        rateLimiter.clear(RATE_LOGIN_FAIL, rateKey);
        int count = user.getLoginCount() == null ? 1 : user.getLoginCount() + 1;
        userMapper.update(null, new UpdateWrapper<User>()
                .eq("uid", user.getUid())
                .set("lastLoginTime", Texts.now())
                .set("loginIp", Texts.cut(ip, 17))
                .set("loginCount", count));
        user.setLoginCount(count);
        writeLog(user.getUid(), name, ip, true, "登录成功");
        JwtService.TokenPair pair = jwtService.issue(toPrincipal(user));
        user.setPassword(null);
        return new LoginResult(pair.accessToken(), pair.refreshToken(), user);
    }

    /**
     * 注册访客账号（注册开关开启时可用），密码直接 BCrypt 存储。
     * 邮箱验证策略：SMTP 可用且填了邮箱时必须携带验证码；SMTP 未配置时邮箱保存但标记未验证（优雅降级）。
     *
     * @param username  用户名
     * @param password  明文密码
     * @param nickname  昵称（可空，默认用户名）
     * @param email     邮箱（可空，找回密码用）
     * @param emailCode 邮箱验证码（邮箱已填且邮件服务可用时必填）
     * @param mail      邮件服务（SMTP 未配置时为 null）
     * @param ip        客户端 IP（限流）
     * @return 注册并登录后的令牌结果
     */
    public LoginResult register(String username, String password, String nickname, String email,
                                String emailCode, MailService mail, String ip) {
        if (!settingService.enabled("allow_register", true)) {
            throw new BizException("当前未开放注册");
        }
        String name = Texts.trim(username);
        if (name.length() < 2 || name.length() > 32) {
            throw new BizException("用户名需要 2 到 32 个字符");
        }
        if (password == null || password.length() < 6 || password.length() > 32) {
            throw new BizException("密码需要 6 到 32 个字符");
        }
        Long exists = userMapper.selectCount(new QueryWrapper<User>().eq("username", name));
        if (exists != null && exists > 0) {
            throw new BizException("用户名已存在");
        }
        String mailBox = Texts.blank(email) ? null : Texts.cut(email, 128);
        // 填了邮箱且邮件服务可用：必须先通过邮箱验证码确认所有权
        if (mail != null && mail.enabled() && mailBox != null) {
            rateLimiter.check("email-code", ip + "|" + mailBox, 5, Duration.ofMinutes(10));
            if (!mail.verifyRegisterCode(mailBox, emailCode)) {
                throw new BizException("邮箱验证码错误或已过期，请先获取验证码");
            }
        }
        User user = new User();
        user.setUsername(name);
        user.setNickname(Texts.blank(nickname) ? name : Texts.cut(nickname, 32));
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("1");
        user.setState("1");
        user.setCreateTime(Texts.today());
        user.setEmail(mailBox);
        user.setLoginCount(0);
        userMapper.insert(user);
        JwtService.TokenPair pair = jwtService.issue(toPrincipal(user));
        user.setPassword(null);
        return new LoginResult(pair.accessToken(), pair.refreshToken(), user);
    }

    /**
     * 发送注册邮箱验证码（SMTP 可用时）。
     *
     * @param email 目标邮箱
     * @param mail  邮件服务
     * @param ip    客户端 IP（限流）
     */
    public void sendRegisterEmailCode(String email, MailService mail, String ip) {
        rateLimiter.check("email-code", ip + "|" + Texts.trim(email), 5, Duration.ofMinutes(10));
        if (mail == null || !mail.enabled()) {
            throw new BizException("邮件服务未配置，暂时无法发送验证码");
        }
        mail.sendRegisterCode(Texts.trim(email));
    }

    /**
     * 刷新令牌轮换：校验 refresh 白名单 → 吊销旧令牌 → 签发新令牌对。
     *
     * @param refreshToken 刷新令牌
     * @return 新令牌对；令牌无效或已吊销时返回 null
     */
    public JwtService.TokenPair refresh(String refreshToken) {
        Object[] validated = jwtService.validateRefresh(refreshToken);
        if (validated == null) {
            return null;
        }
        LoginUser principal = (LoginUser) validated[0];
        String jti = (String) validated[1];
        jwtService.revoke(principal.uid(), jti);
        User user = userMapper.selectById(principal.uid());
        if (user == null || "0".equals(user.getState())) {
            return null;
        }
        return jwtService.issue(toPrincipal(user));
    }

    /**
     * 登出：吊销 refresh 令牌白名单（客户端同时丢弃 access 令牌）。
     *
     * @param refreshToken 刷新令牌
     */
    public void logout(String refreshToken) {
        Object[] validated = jwtService.validateRefresh(refreshToken);
        if (validated != null) {
            jwtService.revoke(((LoginUser) validated[0]).uid(), (String) validated[1]);
        }
    }

    /**
     * 生成图形验证码：Redis 暂存 5 分钟，返回 ID 与 Base64 图片。
     *
     * @return 验证码 ID 与 base64 图片数据
     */
    public Map<String, String> captcha() {
        String code = String.format("%04d", ThreadLocalRandom.current().nextInt(10_000));
        String id = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(CAPTCHA_KEY + id, code.toLowerCase(), Duration.ofMinutes(5));
        String image = com.blog.common.CaptchaImages.base64(code);
        Map<String, String> result = new HashMap<String, String>();
        result.put("captchaId", id);
        result.put("image", image);
        return result;
    }

    /**
     * 发起找回密码：校验用户存在与邮箱绑定，发送 6 位验证码。
     *
     * @param username 用户名
     * @param mail     邮件服务（SMTP 未配置时为 null）
     * @param ip       客户端 IP（限流）
     */
    public void requestPasswordReset(String username, MailService mail, String ip) {
        rateLimiter.check("reset-request", ip, 5, Duration.ofMinutes(15));
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", Texts.trim(username)));
        // 不暴露用户是否存在：统一提示，防止账号枚举
        if (mail == null || user == null || Texts.blank(user.getEmail())) {
            return;
        }
        mail.sendResetCode(user.getEmail());
    }

    /**
     * 确认找回密码：验证码通过后写入 BCrypt 新密码并吊销全部会话。
     *
     * @param username 用户名
     * @param email    收件邮箱（与账号绑定邮箱比对）
     * @param code     验证码
     * @param newPassword 新密码
     * @param mail     邮件服务
     */
    public void confirmPasswordReset(String username, String email, String code, String newPassword, MailService mail) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new BizException("新密码至少 6 位");
        }
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", Texts.trim(username)));
        if (user == null || Texts.blank(user.getEmail()) || !user.getEmail().equals(Texts.trim(email))) {
            throw new BizException("用户名或邮箱不匹配");
        }
        if (mail == null || !mail.verifyResetCode(user.getEmail(), code)) {
            throw new BizException("验证码错误或已过期");
        }
        userMapper.update(null, new UpdateWrapper<User>()
                .eq("uid", user.getUid())
                .set("password", passwordEncoder.encode(newPassword)));
        jwtService.revokeAll(user.getUid());
    }

    /**
     * 校验图形验证码（开关关闭时跳过）。
     *
     * @param captchaId   验证码 ID
     * @param captchaCode 用户输入
     */
    private void verifyCaptcha(String captchaId, String captchaCode) {
        if (!captchaEnabled) {
            return;
        }
        if (Texts.blank(captchaId) || Texts.blank(captchaCode)) {
            throw new BizException("请输入验证码");
        }
        String key = CAPTCHA_KEY + Texts.trim(captchaId);
        String saved = redis.opsForValue().get(key);
        redis.delete(key);
        if (saved == null || !saved.equalsIgnoreCase(Texts.trim(captchaCode))) {
            throw new BizException("验证码错误或已过期");
        }
    }

    /**
     * 口令匹配并升级：BCrypt 直接校验；历史 MD5 命中则重哈希为 BCrypt 落库。
     *
     * @param user     数据库用户
     * @param password 明文口令
     * @return 匹配成功返回 true
     */
    private boolean matchAndUpgrade(User user, String password) {
        String stored = user.getPassword();
        if (stored == null) {
            return false;
        }
        if (stored.startsWith("$2")) {
            return passwordEncoder.matches(password == null ? "" : password, stored);
        }
        // 历史 MD5 口令：命中即升级为 BCrypt（登录期平滑迁移）
        if (Texts.md5(password).equalsIgnoreCase(stored)) {
            String encoded = passwordEncoder.encode(password);
            userMapper.update(null, new UpdateWrapper<User>().eq("uid", user.getUid()).set("password", encoded));
            user.setPassword(encoded);
            return true;
        }
        return false;
    }

    /**
     * 是否管理员：仅依据 role 字段（移除用户名硬编码特权）。
     *
     * @param user 用户
     * @return 管理员返回 true
     */
    public static boolean isAdmin(User user) {
        return user != null && "0".equals(user.getRole());
    }

    /**
     * 由数据库用户构建令牌主体。
     *
     * @param user 数据库用户
     * @return 登录主体
     */
    private LoginUser toPrincipal(User user) {
        return new LoginUser(user.getUid(), user.getUsername(),
                user.getNickname() == null ? "" : user.getNickname(), user.getRole());
    }

    /**
     * 写登录日志。
     *
     * @param uid      用户 ID（可空）
     * @param username 用户名
     * @param ip       客户端 IP
     * @param success  是否成功
     * @param message  说明
     */
    private void writeLog(Integer uid, String username, String ip, boolean success, String message) {
        LoginLog log = new LoginLog();
        log.setUid(uid);
        log.setUsername(Texts.cut(username, 32));
        log.setIp(Texts.cut(ip, 64));
        log.setSuccess(success ? "1" : "0");
        log.setMessage(Texts.cut(message, 100));
        log.setLoginTime(Texts.now());
        loginLogMapper.insert(log);
    }
}
