package com.blog.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;

/**
 * JWT 双令牌服务：签发/校验 access（短效）与 refresh（长效）令牌，
 * refresh 令牌的白名单保存在 Redis 中，可随时吊销（登出/改密即失效）。
 */
@Service
public class JwtService {

    /** access 令牌 claim：类型 */
    private static final String CLAIM_TYPE = "type";

    /** access 令牌类型值 */
    private static final String TYPE_ACCESS = "access";

    /** refresh 令牌类型值 */
    private static final String TYPE_REFRESH = "refresh";

    /** refresh 令牌白名单的 Redis key 前缀 */
    private static final String REFRESH_KEY = "blog:refresh:";

    private final SecretKey key;
    private final long accessTtlMinutes;
    private final long refreshTtlDays;
    private final StringRedisTemplate redis;

    /**
     * 构造 JWT 服务。
     *
     * @param secret           HS256 密钥（生产必须由 JWT_SECRET 环境变量注入强随机值）
     * @param accessTtlMinutes access 令牌有效期（分钟）
     * @param refreshTtlDays   refresh 令牌有效期（天）
     * @param redis            Redis 客户端，用于 refresh 白名单
     */
    public JwtService(@Value("${blog.jwt.secret}") String secret,
                      @Value("${blog.jwt.access-ttl-minutes}") long accessTtlMinutes,
                      @Value("${blog.jwt.refresh-ttl-days}") long refreshTtlDays,
                      StringRedisTemplate redis) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlMinutes = accessTtlMinutes;
        this.refreshTtlDays = refreshTtlDays;
        this.redis = redis;
    }

    /**
     * 登录成功后的令牌对。
     *
     * @param accessToken  短效访问令牌
     * @param refreshToken 长效刷新令牌（已写入 Redis 白名单）
     */
    public record TokenPair(String accessToken, String refreshToken) {
    }

    /**
     * 为指定用户签发新的令牌对。
     *
     * @param user 登录用户
     * @return 令牌对
     */
    public TokenPair issue(LoginUser user) {
        long now = System.currentTimeMillis();
        String access = Jwts.builder()
                .subject(String.valueOf(user.uid()))
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim("username", user.username())
                .claim("nickname", user.nickname())
                .claim("role", user.role())
                .issuedAt(new java.util.Date(now))
                .expiration(new java.util.Date(now + accessTtlMinutes * 60_000L))
                .signWith(key)
                .compact();
        String jti = UUID.randomUUID().toString().replace("-", "");
        String refresh = Jwts.builder()
                .subject(String.valueOf(user.uid()))
                .id(jti)
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(new java.util.Date(now))
                .expiration(new java.util.Date(now + refreshTtlDays * 86_400_000L))
                .signWith(key)
                .compact();
        redis.opsForValue().set(REFRESH_KEY + user.uid() + ":" + jti, "1",
                Duration.ofDays(refreshTtlDays));
        return new TokenPair(access, refresh);
    }

    /**
     * 解析 access 令牌为登录主体。
     *
     * @param token JWT 字符串
     * @return 登录主体；令牌无效或类型不对时返回 null
     */
    public LoginUser parseAccess(String token) {
        Claims claims = parse(token, TYPE_ACCESS);
        if (claims == null) {
            return null;
        }
        return new LoginUser(Integer.valueOf(claims.getSubject()),
                String.valueOf(claims.get("username")),
                String.valueOf(claims.get("nickname")),
                String.valueOf(claims.get("role")));
    }

    /**
     * 校验 refresh 令牌并返回其主体与 jti；要求白名单命中。
     *
     * @param token refresh 令牌
     * @return 主体数组（LoginUser + jti 两个元素），无效或已吊销时返回 null
     */
    public Object[] validateRefresh(String token) {
        Claims claims = parse(token, TYPE_REFRESH);
        if (claims == null || claims.getId() == null) {
            return null;
        }
        String key = REFRESH_KEY + claims.getSubject() + ":" + claims.getId();
        if (!Boolean.TRUE.equals(redis.hasKey(key))) {
            return null;
        }
        return new Object[]{new LoginUser(Integer.valueOf(claims.getSubject()), null, null, null), claims.getId()};
    }

    /**
     * 吊销 refresh 令牌（登出、刷新轮换、改密时调用）。
     *
     * @param uid 用户 ID
     * @param jti refresh 令牌 ID
     */
    public void revoke(Integer uid, String jti) {
        redis.delete(REFRESH_KEY + uid + ":" + jti);
    }

    /**
     * 吊销某用户全部 refresh 令牌（改密/锁定账号时调用）。
     *
     * @param uid 用户 ID
     */
    public void revokeAll(Integer uid) {
        var pattern = REFRESH_KEY + uid + ":*";
        var keys = redis.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    /**
     * 解析并校验指定类型的令牌。
     *
     * @param token JWT 字符串
     * @param type  期望的令牌类型 claim
     * @return Claims；签名无效、过期或类型不符时返回 null
     */
    private Claims parse(String token, String type) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            if (!type.equals(claims.get(CLAIM_TYPE, String.class))) {
                return null;
            }
            return claims;
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }
}
