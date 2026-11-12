package com.blog.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 认证上下文工具：控制器里统一读取当前 JWT 登录主体，匿名时返回 null。
 */
public final class Auths {

    private Auths() {
    }

    /**
     * 获取当前登录主体。
     *
     * @return 登录主体；匿名请求返回 null
     */
    public static LoginUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser user) {
            return user;
        }
        return null;
    }

    /**
     * 当前是否已登录。
     *
     * @return 已登录返回 true
     */
    public static boolean authenticated() {
        return currentUser() != null;
    }
}
