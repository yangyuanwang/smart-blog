package com.blog.common;

import java.util.regex.Pattern;

public final class MessageText {

    private static final Pattern URL = Pattern.compile("(?i)(https?://|www\\.|[a-z0-9-]+\\.(com|cn|net|org|xyz|top|vip)(/|\\b))");
    private static final String[] BLOCKED = {
            "赌博", "色情", "裸聊", "发票", "办证", "刷单", "代孕"
    };

    private MessageText() {
    }

    public static String nickname(String raw) {
        String name = Texts.trim(raw);
        // 匿名访客昵称选填：留空自动生成「游客_xxxx」，不强制填写
        if (name.isEmpty()) {
            name = "游客_" + Texts.trim(java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 4));
        }
        if (name.length() > 16) {
            throw new BizException("昵称不能超过 16 个字");
        }
        reject(name);
        return name;
    }

    public static String content(String raw) {
        String text = Texts.trim(raw);
        if (text.isEmpty() || text.length() > 500) {
            throw new BizException("留言需要 1 到 500 个字");
        }
        reject(text);
        return text;
    }

    private static void reject(String text) {
        if (URL.matcher(text).find()) {
            throw new BizException("内容里不能留网址");
        }
        for (String word : BLOCKED) {
            if (text.contains(word)) {
                throw new BizException("内容包含不适宜发布的词语");
            }
        }
    }
}
