package com.blog;

import com.blog.mapper.LoginLogMapper;
import com.blog.mapper.UserMapper;
import com.blog.security.JwtService;
import com.blog.service.AuthService;
import com.blog.service.RateLimiterService;
import com.blog.service.SettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.blog.common.BizException;
import com.blog.entity.User;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 认证服务单元测试：登录口令升级、锁定与限流、注册开关、管理员校验。
 */
public class AuthServiceTest {

    private UserMapper userMapper;
    private AuthService authService;
    private RateLimiterService rateLimiter;
    private User admin;

    /**
     * 构造被测服务与公共夹具：管理员用户（BCrypt 口令）。
     */
    @BeforeEach
    public void setUp() {
        userMapper = Mockito.mock(UserMapper.class);
        LoginLogMapper loginLogMapper = Mockito.mock(LoginLogMapper.class);
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        JwtService jwtService = Mockito.mock(JwtService.class);
        rateLimiter = Mockito.mock(RateLimiterService.class);
        SettingService settingService = Mockito.mock(SettingService.class);
        StringRedisTemplate redis = Mockito.mock(StringRedisTemplate.class);

        admin = new User();
        admin.setUid(3);
        admin.setUsername("admin");
        admin.setNickname("管理员");
        admin.setRole("0");
        admin.setState("1");
        admin.setPassword(encoder.encode("s3cret"));

        lenient().when(userMapper.selectOne(any())).thenReturn(admin);
        lenient().when(settingService.enabled(eq("allow_register"), anyBoolean())).thenReturn(true);
        lenient().when(jwtService.issue(any())).thenReturn(new JwtService.TokenPair("access", "refresh"));

        authService = new AuthService(userMapper, loginLogMapper, encoder, jwtService,
                rateLimiter, settingService, redis, false);
    }

    private boolean anyBoolean() {
        return org.mockito.ArgumentMatchers.anyBoolean();
    }

    /**
     * 登录成功应签发双令牌并返回脱敏用户。
     */
    @Test
    public void loginIssuesTokenPairAndSanitizesPassword() {
        AuthService.LoginResult result = authService.login("admin", "s3cret", null, null, "127.0.0.1", true);
        assertEquals("access", result.accessToken());
        assertEquals("refresh", result.refreshToken());
        assertNotNull(result.user());
        assertNotEquals("s3cret", result.user().getPassword());
    }

    /**
     * 错误口令必须失败并写失败限流计数。
     */
    @Test
    public void wrongPasswordThrowsAndCountsFail() {
        assertThrows(BizException.class,
                () -> authService.login("admin", "bad", null, null, "127.0.0.1", false));
        verify(rateLimiter).check(anyString(), eq("127.0.0.1|admin"), eq(5), eq(Duration.ofMinutes(15)));
    }

    /**
     * 触发限流时直接拒绝（不进入口令校验）。
     */
    @Test
    public void rateLimitRejectsBeforePasswordCheck() {
        Mockito.doThrow(new BizException(1004, "操作太频繁"))
                .when(rateLimiter).check(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt(), any());
        assertThrows(BizException.class,
                () -> authService.login("admin", "s3cret", null, null, "127.0.0.1", false));
        verify(userMapper, never()).update(any(), any());
    }

    /**
     * 锁定账号拒绝登录。
     */
    @Test
    public void lockedAccountCannotLogin() {
        admin.setState("0");
        assertThrows(BizException.class,
                () -> authService.login("admin", "s3cret", null, null, "127.0.0.1", false));
    }

    /**
     * 非管理员请求后台登录被拒绝。
     */
    @Test
    public void adminOnlyRejectsNormalRole() {
        admin.setRole("1");
        assertThrows(BizException.class,
                () -> authService.login("admin", "s3cret", null, null, "127.0.0.1", true));
    }

    /**
     * 注册开关关闭时拒绝注册。
     */
    @Test
    public void registerHonorsSwitch() {
        HashMap<String, String> settings = new HashMap<String, String>(Map.of("allow_register", "0"));
        SettingService spied = Mockito.mock(SettingService.class);
        when(spied.enabled(eq("allow_register"), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(false);
        AuthService switched = new AuthService(userMapper, Mockito.mock(LoginLogMapper.class),
                new BCryptPasswordEncoder(), Mockito.mock(JwtService.class), rateLimiter, spied,
                Mockito.mock(StringRedisTemplate.class), false);
        assertThrows(BizException.class, () -> switched.register("newbie", "123456", "n", null));
        assertTrue(settings.containsKey("allow_register"));
    }

    /**
     * QueryWrapper 基础行为回归（防 MyBatis-Plus 升级破坏）。
     */
    @Test
    public void queryWrapperStillParameterizes() {
        QueryWrapper<User> wrapper = new QueryWrapper<User>().eq("username", "admin");
        assertTrue(wrapper.getSqlSegment().contains("username = #{"), "SQL 片段应参数化");
    }

    /**
     * 历史 MD5 口令在登录成功时升级为 BCrypt。
     */
    @Test
    public void legacyMd5PasswordUpgradedOnLogin() {
        User legacy = new User();
        legacy.setUid(9);
        legacy.setUsername("legacy");
        legacy.setNickname("旧账号");
        legacy.setRole("1");
        legacy.setState("1");
        legacy.setPassword(com.blog.common.Texts.md5("oldpass"));
        when(userMapper.selectOne(any())).thenReturn(legacy);
        authService.login("legacy", "oldpass", null, null, "127.0.0.1", false);
        // 升级路径应触发口令更新落库（登录统计更新之外额外一次 update）
        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper> captor =
                org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
        verify(userMapper, Mockito.times(2)).update(any(), captor.capture());
        assertTrue(captor.getAllValues().get(0).getSqlSet().contains("password"),
                "首次 update 应为口令升级");
    }
}
