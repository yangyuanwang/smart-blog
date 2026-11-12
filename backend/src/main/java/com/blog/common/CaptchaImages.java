package com.blog.common;

import java.util.Random;

/**
 * 图形验证码图片生成：Java2D 绘制 4 位数字验证码并输出 Base64 PNG（SecureRandom 防可预测）。
 */
public final class CaptchaImages {

    private CaptchaImages() {
    }

    /**
     * 生成验证码 Base64 图片。
     *
     * @param code 4 位数字验证码
     * @return data:image/png;base64, 前缀的图片数据
     */
    public static String base64(String code) {
        int width = 120;
        int height = 40;
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        Random random = new java.security.SecureRandom();
        // 背景与干扰线：轻微噪点提升机器识别成本
        g.setColor(new java.awt.Color(0xF6, 0xF4, 0xEF));
        g.fillRect(0, 0, width, height);
        for (int i = 0; i < 6; i++) {
            g.setColor(new java.awt.Color(120 + random.nextInt(80),
                    120 + random.nextInt(80), 120 + random.nextInt(80)));
            g.drawLine(random.nextInt(width), random.nextInt(height),
                    random.nextInt(width), random.nextInt(height));
        }
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 26));
        for (int i = 0; i < code.length(); i++) {
            g.setColor(new java.awt.Color(0x3F, 0x5C, 0x4E));
            int x = 12 + i * 26;
            int y = 28 + (random.nextInt(5) - 2);
            g.rotate((random.nextInt(9) - 4) * Math.PI / 180.0, x, y);
            g.drawString(String.valueOf(code.charAt(i)), x, y);
            g.rotate(-(random.nextInt(9) - 4) * Math.PI / 180.0, x, y);
        }
        g.dispose();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try {
            javax.imageio.ImageIO.write(image, "png", out);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("验证码图片生成失败", ex);
        }
        return "data:image/png;base64,"
                + java.util.Base64.getEncoder().encodeToString(out.toByteArray());
    }
}
