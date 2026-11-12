package com.blog.security;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * JWT 认证过滤器：从 Authorization: Bearer 中解析 access 令牌并写入 SecurityContext。
 * 只对 /api/v1/** 生效；令牌无效时保持匿名，由授权规则决定放行或 401。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    /** Bearer 前缀长度 */
    private static final int BEARER_PREFIX = "Bearer ".length();

    private final JwtService jwtService;

    /**
     * 构造过滤器。
     *
     * @param jwtService JWT 服务
     */
    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.length() > BEARER_PREFIX && header.startsWith("Bearer ")) {
            LoginUser user = jwtService.parseAccess(header.substring(BEARER_PREFIX));
            if (user != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                var authorities = user.admin()
                        ? java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                        : java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"));
                var auth = new UsernamePasswordAuthenticationToken(user, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }
}
