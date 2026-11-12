package com.blog.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.blog.common.Result;
import com.blog.common.ResultCode;
import com.blog.security.JwtAuthFilter;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Spring Security 6 配置：REST API 走 JWT 无状态授权，旧 Thymeleaf 页面沿用 Session 拦截器（过渡期共存）。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    /**
     * 构造安全配置。
     *
     * @param jwtAuthFilter JWT 过滤器
     */
    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    /**
     * BCrypt 密码编码器（cost 10），同时用于历史 MD5 密码的登录期升级。
     *
     * @return 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 主安全链：声明公开白名单、me 与 admin 授权边界，以及 401/403 的 JSON 输出。
     *
     * @param http HttpSecurity 构建器
     * @return 安全过滤链
     * @throws Exception 配置异常
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        // 认证接口：匿名可用
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // 管理端与个人中心必须在宽泛的 GET 放行之前声明（先匹配先生效）
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/me/**").authenticated()
                        // 公开内容：GET 只读全放行（服务层再做限流与频控）
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/**").permitAll()
                        // 匿名互动端点
                        .requestMatchers("/api/v1/articles/*/view", "/api/v1/articles/*/like").permitAll()
                        .requestMatchers("/api/v1/comments", "/api/v1/messages", "/api/v1/messages/*").permitAll()
                        .requestMatchers("/api/v1/link-applies").permitAll()
                        // 其余全部放行：旧 Thymeleaf 页面由 AuthInterceptor 管控
                        .anyRequest().permitAll())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) ->
                                writeJson(response, HttpStatus.UNAUTHORIZED, ResultCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, ex) ->
                                writeJson(response, HttpStatus.FORBIDDEN, ResultCode.FORBIDDEN)))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 开发环境 CORS：允许 Nuxt 前端跨域携带令牌调用 API。
     *
     * @return CORS 配置源
     */
    private CorsConfigurationSource corsSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000", "http://127.0.0.1:3000"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", config);
        return source;
    }

    /**
     * 向响应写出统一的 JSON 错误体。
     *
     * @param response 目标响应
     * @param status   HTTP 状态码
     * @param code     业务码
     * @throws IOException 写出失败
     */
    private void writeJson(HttpServletResponse response, HttpStatus status, ResultCode code) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(Result.fail(code.code(), code.message())));
    }
}
