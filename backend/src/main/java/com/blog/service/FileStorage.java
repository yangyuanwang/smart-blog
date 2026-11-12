package com.blog.service;

import com.blog.common.BizException;
import com.blog.common.Texts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

@Service
public class FileStorage {

    @Value("${blog.upload-dir}")
    private String uploadDir;

    private Path root;

    @PostConstruct
    public void init() throws IOException {
        Path path = Paths.get(uploadDir);
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir")).resolve(uploadDir);
        }
        this.root = path.toAbsolutePath();
        Files.createDirectories(this.root);
    }

    public String save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String original = file.getOriginalFilename();
        String ext = extension(original);
        if (!"jpg".equals(ext) && !"jpeg".equals(ext) && !"png".equals(ext) && !"gif".equals(ext) && !"webp".equals(ext)) {
            throw new BizException("只支持 jpg、png、gif、webp 图片");
        }
        byte[] head = readMagic(file);
        if (!magicMatches(ext, head)) {
            // 扩展名伪装防护：内容魔数必须与扩展名一致
            throw new BizException("文件内容与图片格式不符");
        }
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, root.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BizException("图片保存失败");
        }
        return "/uploads/" + filename;
    }

    /**
     * 读取文件头魔数字节（不足 12 字节时按实际长度返回）。
     *
     * @param file 上传文件
     * @return 文件头字节
     */
    private byte[] readMagic(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            byte[] head = new byte[12];
            int read = input.readNBytes(head, 0, head.length);
            if (read < head.length) {
                byte[] actual = new byte[read];
                System.arraycopy(head, 0, actual, 0, read);
                return actual;
            }
            return head;
        } catch (IOException ex) {
            throw new BizException("图片读取失败");
        }
    }

    /**
     * 校验魔数与扩展名匹配（JPEG/PNG/GIF/WebP）。
     *
     * @param ext  扩展名
     * @param head 文件头
     * @return 匹配返回 true
     */
    private boolean magicMatches(String ext, byte[] head) {
        if (head.length < 4) {
            return false;
        }
        if ("jpg".equals(ext) || "jpeg".equals(ext)) {
            return (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8;
        }
        if ("png".equals(ext)) {
            return (head[0] & 0xFF) == 0x89 && (head[1] & 0xFF) == 0x50;
        }
        if ("gif".equals(ext)) {
            return head[0] == 'G' && head[1] == 'I' && head[2] == 'F';
        }
        if ("webp".equals(ext)) {
            return head.length >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                    && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
        }
        return false;
    }

    private String extension(String name) {
        if (Texts.blank(name) || !name.contains(".")) {
            return "";
        }
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
