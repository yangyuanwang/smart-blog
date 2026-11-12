package com.blog.service;

import com.blog.common.BizException;
import com.blog.common.Texts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
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
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, root.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BizException("图片保存失败");
        }
        return "/uploads/" + filename;
    }

    private String extension(String name) {
        if (Texts.blank(name) || !name.contains(".")) {
            return "";
        }
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
