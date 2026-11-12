package com.blog.security;

/**
 * 认证通过后的登录用户主体，挂在 SecurityContext 中供控制器读取。
 *
 * @param uid      用户 ID
 * @param username 用户名
 * @param nickname 昵称（可能为空串）
 * @param role     角色：0 管理员 1 普通用户
 */
public record LoginUser(Integer uid, String username, String nickname, String role) {

    /**
     * 判断是否管理员（仅依据 role 字段，不存在用户名硬编码特权）。
     *
     * @return 管理员返回 true
     */
    public boolean admin() {
        return "0".equals(role);
    }
}
