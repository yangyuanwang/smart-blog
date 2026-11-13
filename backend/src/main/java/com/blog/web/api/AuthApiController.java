package com.blog.web.api;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.blog.common.BizException;
import com.blog.common.Ip;
import com.blog.common.Result;
import com.blog.security.LoginUser;
import com.blog.security.Auths;
import com.blog.security.JwtService;
import com.blog.service.AuthService;
import com.blog.service.MailService;
import com.blog.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 认证 API：登录/注册/刷新/登出/验证码/邮箱找回密码。
 */
@Tag(name = "auth", description = "认证与令牌")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthApiController {

    private final AuthService authService;
    private final UserService userService;
    private final MailService mailService;
    private final Ip ip;

    /**
     * 构造认证控制器。
     *
     * @param authService 认证服务
     * @param userService 用户服务（注册重名校验等）
     * @param mailService 邮件服务（SMTP 未配置时 Bean 不存在，注入 null）
     * @param ip          客户端 IP 提取
     */
    public AuthApiController(AuthService authService, UserService userService,
                             org.springframework.beans.factory.ObjectProvider<MailService> mailService, Ip ip) {
        this.authService = authService;
        this.userService = userService;
        this.mailService = mailService.getIfAvailable();
        this.ip = ip;
    }

    /**
     * 登录请求体。
     *
     * @param username    用户名
     * @param password    密码
     * @param captchaId   验证码 ID（可选）
     * @param captchaCode 验证码输入（可选）
     */
    public record LoginRequest(@NotBlank String username, @NotBlank String password,
                               String captchaId, String captchaCode) {
    }

    /**
     * 注册请求体。
     *
     * @param username  用户名
     * @param password  密码
     * @param nickname  昵称（可选）
     * @param email     邮箱（可选；填写且邮件服务可用时需验证码）
     * @param emailCode 邮箱验证码（与 email 配套）
     */
    public record RegisterRequest(@NotBlank @Size(min = 2, max = 32) String username,
                                  @NotBlank @Size(min = 6, max = 32) String password,
                                  String nickname, String email, String emailCode) {
    }

    /**
     * 注册邮箱验证码发送请求体。
     *
     * @param email 目标邮箱
     */
    public record EmailCodeRequest(@NotBlank @Size(max = 128) String email) {
    }

    /**
     * 刷新令牌请求体。
     *
     * @param refreshToken 刷新令牌
     */
    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    /**
     * 找回密码验证码请求体。
     *
     * @param username 用户名
     */
    public record ResetRequestRequest(@NotBlank String username) {
    }

    /**
     * 找回密码确认请求体。
     *
     * @param username    用户名
     * @param email       账号绑定邮箱
     * @param code        验证码
     * @param newPassword 新密码
     */
    public record ResetConfirmRequest(@NotBlank String username, @NotBlank String email,
                                      @NotBlank String code, @NotBlank @Size(min = 6) String newPassword) {
    }

    /**
     * 登录并签发双令牌。
     *
     * @param request 登录请求
     * @param httpRequest 原始请求（取客户端 IP）
     * @return 访问令牌、刷新令牌与用户公开视图
     */
    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        AuthService.LoginResult result = authService.login(request.username(), request.password(),
                request.captchaId(), request.captchaCode(), ip.of(httpRequest), false);
        return Result.ok(resultView(result.accessToken(), result.refreshToken(), result.user()));
    }

    /**
     * 注册并直接登录（注册开关开启时可用）。
     * 邮箱验证策略：填写邮箱且邮件服务可用时必须携带验证码。
     *
     * @param request 注册请求
     * @param httpRequest 原始请求
     * @return 令牌对与用户公开视图
     */
    @Operation(summary = "注册")
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        AuthService.LoginResult result = authService.register(request.username(), request.password(),
                request.nickname(), request.email(), request.emailCode(), mailService, ip.of(httpRequest));
        return Result.ok(resultView(result.accessToken(), result.refreshToken(), result.user()));
    }

    /**
     * 发送注册邮箱验证码（SMTP 可用时；限流防滥用）。
     *
     * @param request 邮箱
     * @param httpRequest 原始请求
     * @return 成功响应
     */
    @Operation(summary = "发送注册邮箱验证码")
    @PostMapping("/email/code")
    public Result<Void> sendEmailCode(@RequestBody EmailCodeRequest request, HttpServletRequest httpRequest) {
        authService.sendRegisterEmailCode(request.email(), mailService, ip.of(httpRequest));
        return Result.ok();
    }

    /**
     * 邮件服务是否可用（前端据此决定注册页是否展示验证码输入）。
     *
     * @return enabled = SMTP 已配置
     */
    @Operation(summary = "邮件服务可用性")
    @GetMapping("/mail-enabled")
    public Result<Map<String, Boolean>> mailEnabled() {
        return Result.ok(Map.of("enabled", mailService != null && mailService.enabled()));
    }

    /**
     * 后台管理员登录（额外校验管理员角色）。
     *
     * @param request 登录请求
     * @param httpRequest 原始请求
     * @return 令牌对与用户公开视图
     */
    @Operation(summary = "后台登录")
    @PostMapping("/admin-login")
    public Result<Map<String, Object>> adminLogin(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        AuthService.LoginResult result = authService.login(request.username(), request.password(),
                request.captchaId(), request.captchaCode(), ip.of(httpRequest), true);
        return Result.ok(resultView(result.accessToken(), result.refreshToken(), result.user()));
    }

    /**
     * 刷新令牌（轮换：旧 refresh 吊销，返回新令牌对）。
     *
     * @param request 刷新请求
     * @return 新令牌对
     */
    @Operation(summary = "刷新令牌")
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(@RequestBody RefreshRequest request) {
        JwtService.TokenPair pair = authService.refresh(request.refreshToken());
        if (pair == null) {
            throw new com.blog.common.BizException(com.blog.common.ResultCode.UNAUTHORIZED.code(),
                    "登录已过期，请重新登录");
        }
        return Result.ok(Map.of("accessToken", pair.accessToken(), "refreshToken", pair.refreshToken()));
    }

    /**
     * 登出：吊销 refresh 令牌。
     *
     * @param request 刷新请求（携带待吊销的 refreshToken）
     * @return 成功响应
     */
    @Operation(summary = "登出")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return Result.ok();
    }

    /**
     * 获取图形验证码（开关开启时登录需要）。
     *
     * @return captchaId 与 base64 图片
     */
    @Operation(summary = "图形验证码")
    @GetMapping("/captcha")
    public Result<Map<String, String>> captcha() {
        return Result.ok(authService.captcha());
    }

    /**
     * 请求找回密码验证码（不暴露账号是否存在）。
     *
     * @param request 用户名
     * @return 成功响应（提示统一）
     */
    @Operation(summary = "找回密码-请求验证码")
    @PostMapping("/reset/request")
    public Result<Void> requestReset(@RequestBody ResetRequestRequest request, HttpServletRequest httpRequest) {
        authService.requestPasswordReset(request.username(), mailService, ip.of(httpRequest));
        return Result.ok();
    }

    /**
     * 确认找回密码（验证码 + 新密码）。
     *
     * @param request 确认请求
     * @return 成功响应
     */
    @Operation(summary = "找回密码-确认")
    @PostMapping("/reset/confirm")
    public Result<Void> confirmReset(@RequestBody ResetConfirmRequest request) {
        authService.confirmPasswordReset(request.username(), request.email(), request.code(),
                request.newPassword(), mailService);
        return Result.ok();
    }

    /**
     * 组装登录返回视图：令牌对 + 脱敏用户。
     *
     * @param access  访问令牌
     * @param refresh 刷新令牌
     * @param user    数据库用户（密码置空后）
     * @return 响应数据
     */
    private Map<String, Object> resultView(String access, String refresh, com.blog.entity.User user) {
        return Map.of("accessToken", access, "refreshToken", refresh,
                "user", Map.of("uid", user.getUid(), "username", user.getUsername(),
                        "nickname", user.getNickname() == null ? "" : user.getNickname(),
                        "role", user.getRole() == null ? "1" : user.getRole(),
                        "img", user.getImg() == null ? "" : user.getImg(),
                        "admin", "0".equals(user.getRole())));
    }
}
