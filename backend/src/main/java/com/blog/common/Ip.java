package com.blog.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 客户端 IP 提取工具：只在请求来自可信反代时才信任 X-Forwarded-For，
 * 防止外部伪造头绕过限流。所有需要 IP 的写操作（登录/留言/点赞等）统一走本类。
 */
@Component
public class Ip {

    private final Set<String> trustedProxies;

    /**
     * 构造并解析可信代理列表。
     *
     * @param trustedProxies 配置项 blog.security.trusted-proxies，逗号分隔的可信代理 IP
     */
    public Ip(@Value("${blog.security.trusted-proxies}") String trustedProxies) {
        Set<String> proxies = new HashSet<String>();
        for (String part : Texts.trim(trustedProxies).split(",")) {
            if (!Texts.blank(part)) {
                proxies.add(part.trim());
            }
        }
        this.trustedProxies = proxies;
    }

    /**
     * 解析客户端真实 IP。
     * <p>规则：直连来源在可信代理列表内时取 X-Forwarded-For 的第一个段，否则取直连地址，伪造头不生效。</p>
     *
     * @param request 当前请求
     * @return 客户端 IP（可能是 IPv6），最长 64 字符
     */
    public String of(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        String client = remote;
        if (remote != null && trustedProxies.contains(remote)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (!Texts.blank(forwarded)) {
                String first = forwarded.split(",")[0].trim();
                if (!Texts.blank(first)) {
                    client = first;
                }
            }
        }
        return Texts.cut(client, 64);
    }

    /**
     * 判断给定地址是否属于可信代理。
     *
     * @param address 待判断的地址
     * @return 属于可信代理时返回 true
     */
    public boolean isTrusted(String address) {
        return address != null && trustedProxies.contains(address);
    }

    /**
     * 返回可信代理列表的只读视图（便于诊断）。
     *
     * @return 可信代理地址集合
     */
    public Set<String> trusted() {
        return Set.copyOf(Arrays.asList(trustedProxies.toArray(new String[0])));
    }
}
