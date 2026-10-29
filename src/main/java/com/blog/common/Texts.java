package com.blog.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class Texts {

    private Texts() {
    }

    public static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    }

    public static String md5(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(trim(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                String hex = Integer.toHexString(b & 0xff);
                if (hex.length() == 1) {
                    builder.append('0');
                }
                builder.append(hex);
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("MD5 计算失败", ex);
        }
    }

    public static String cut(String value, int max) {
        String text = trim(value);
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }

    public static String ip(String value) {
        String text = trim(value);
        if (text.length() <= 17) {
            return text;
        }
        return text.substring(0, 17);
    }
}
