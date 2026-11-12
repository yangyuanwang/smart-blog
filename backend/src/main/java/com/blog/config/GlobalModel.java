package com.blog.config;

import com.blog.entity.User;
import com.blog.service.ContentService;
import com.blog.service.SettingService;
import com.blog.service.UserService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpSession;
import java.util.Map;

@ControllerAdvice
public class GlobalModel {

    private final SettingService settingService;
    private final ContentService contentService;

    public GlobalModel(SettingService settingService, ContentService contentService) {
        this.settingService = settingService;
        this.contentService = contentService;
    }

    @ModelAttribute("site")
    public Map<String, String> site() {
        return settingService.all();
    }

    @ModelAttribute("loginUser")
    public User loginUser(HttpSession session) {
        return (User) session.getAttribute(UserService.LOGIN_USER);
    }

    @ModelAttribute("pendingMessages")
    public long pendingMessages() {
        return contentService.pendingMessageCount();
    }
}
