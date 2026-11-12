package com.blog.web.api;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.blog.common.BizException;
import com.blog.common.Result;
import com.blog.common.Texts;
import com.blog.entity.User;
import com.blog.mapper.UserMapper;
import com.blog.security.Auths;
import com.blog.security.JwtService;
import com.blog.security.LoginUser;
import com.blog.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 个人中心 API：资料查看/修改、密码修改（改密后吊销全部会话并换发新令牌）。
 */
@Tag(name = "me", description = "个人中心")
@RestController
@RequestMapping("/api/v1/me")
public class MeApiController {

    private final UserMapper userMapper;
    private final UserService userService;
    private final JwtService jwtService;

    /**
     * 构造个人中心控制器。
     *
     * @param userMapper  用户 Mapper
     * @param userService 用户服务
     * @param jwtService  JWT 服务（改密后吊销会话）
     */
    public MeApiController(UserMapper userMapper, UserService userService, JwtService jwtService) {
        this.userMapper = userMapper;
        this.userService = userService;
        this.jwtService = jwtService;
    }

    /**
     * 资料修改请求体。
     *
     * @param nickname 昵称
     * @param email    邮箱
     * @param phone    手机号（选填）
     * @param img      头像地址（选填）
     */
    public record ProfileRequest(@Size(max = 32) String nickname, @Size(max = 128) String email,
                                 @Size(max = 11) String phone, String img) {
    }

    /**
     * 改密请求体。
     *
     * @param oldPassword 原密码
     * @param newPassword 新密码
     */
    public record PasswordRequest(@NotBlank String oldPassword, @NotBlank @Size(min = 6, max = 32) String newPassword) {
    }

    /**
     * 当前用户资料。
     *
     * @return 脱敏后的用户信息
     */
    @Operation(summary = "我的资料")
    @GetMapping("/profile")
    public Result<Map<String, Object>> profile() {
        User user = requireUser();
        user.setPassword(null);
        return Result.ok(Map.of("uid", user.getUid(), "username", user.getUsername(),
                "nickname", user.getNickname() == null ? "" : user.getNickname(),
                "email", user.getEmail() == null ? "" : user.getEmail(),
                "phone", user.getPhone() == null ? "" : user.getPhone(),
                "img", user.getImg() == null ? "" : user.getImg(),
                "role", user.getRole() == null ? "1" : user.getRole(),
                "admin", "0".equals(user.getRole())));
    }

    /**
     * 修改资料。
     *
     * @param request 资料请求
     * @return 更新后的资料
     */
    @Operation(summary = "修改资料")
    @PutMapping("/profile")
    public Result<Map<String, Object>> updateProfile(@RequestBody ProfileRequest request) {
        User user = requireUser();
        userService.updateProfile(user, request.nickname(), request.phone(), request.img());
        if (!Texts.blank(request.email())) {
            userMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<User>()
                    .eq("uid", user.getUid())
                    .set("email", Texts.cut(request.email(), 128)));
        }
        return profile();
    }

    /**
     * 修改密码：BCrypt/历史 MD5 校验原密码，成功后吊销全部会话并换发新令牌对。
     *
     * @param request 改密请求
     * @return 新令牌对
     */
    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public Result<Map<String, String>> changePassword(@RequestBody PasswordRequest request) {
        User user = requireUser();
        String stored = user.getPassword();
        boolean matches = stored != null
                && (stored.startsWith("$2")
                        ? new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                                .matches(request.oldPassword(), stored)
                        : Texts.md5(request.oldPassword()).equalsIgnoreCase(stored));
        if (!matches) {
            throw new BizException("原密码不正确");
        }
        String encoded = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                .encode(request.newPassword());
        userMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<User>()
                .eq("uid", user.getUid())
                .set("password", encoded));
        jwtService.revokeAll(user.getUid());
        LoginUser principal = Auths.currentUser();
        JwtService.TokenPair pair = jwtService.issue(new LoginUser(user.getUid(), user.getUsername(),
                user.getNickname() == null ? "" : user.getNickname(), user.getRole()));
        return Result.ok(Map.of("accessToken", pair.accessToken(), "refreshToken", pair.refreshToken()));
    }

    /**
     * 获取当前登录用户实体（未登录抛 401 语义业务异常）。
     *
     * @return 用户实体
     */
    private User requireUser() {
        LoginUser principal = Auths.currentUser();
        if (principal == null) {
            throw new BizException(com.blog.common.ResultCode.UNAUTHORIZED.code(), "未登录");
        }
        User user = userMapper.selectById(principal.uid());
        if (user == null) {
            throw new BizException(com.blog.common.ResultCode.UNAUTHORIZED.code(), "账号不存在或已注销");
        }
        return user;
    }
}
