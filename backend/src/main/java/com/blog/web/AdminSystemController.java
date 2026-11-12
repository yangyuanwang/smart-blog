package com.blog.web;

import com.blog.common.BizException;
import com.blog.common.Texts;
import com.blog.entity.User;
import com.blog.service.ArticleService;
import com.blog.service.ContentService;
import com.blog.service.FileStorage;
import com.blog.service.SettingService;
import com.blog.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/admin")
public class AdminSystemController {

    private final ArticleService articleService;
    private final ContentService contentService;
    private final UserService userService;
    private final SettingService settingService;
    private final FileStorage fileStorage;

    public AdminSystemController(ArticleService articleService, ContentService contentService, UserService userService,
                                 SettingService settingService, FileStorage fileStorage) {
        this.articleService = articleService;
        this.contentService = contentService;
        this.userService = userService;
        this.settingService = settingService;
        this.fileStorage = fileStorage;
    }

    @GetMapping({"", "/"})
    public String dashboard(Model model) {
        model.addAttribute("articleCount", articleService.count());
        model.addAttribute("publicCount", articleService.publicCount());
        model.addAttribute("commentCount", contentService.commentCount());
        model.addAttribute("linkCount", contentService.linkCount());
        model.addAttribute("visitCount", articleService.visits());
        model.addAttribute("messageCount", contentService.messageCount());
        model.addAttribute("javaVersion", System.getProperty("java.version"));
        model.addAttribute("osName", System.getProperty("os.name"));
        return "admin/index";
    }

    @GetMapping("/users")
    public String users(@RequestParam(defaultValue = "1") long page,
                        @RequestParam(required = false) String keyword,
                        Model model) {
        model.addAttribute("pager", userService.page(page, 12, keyword));
        model.addAttribute("keyword", keyword);
        return "admin/users";
    }

    @PostMapping("/users/state")
    public String state(@RequestParam Integer uid, @RequestParam String state, HttpSession session, RedirectAttributes ra) {
        User self = (User) session.getAttribute(UserService.LOGIN_USER);
        try {
            userService.changeState(uid, state, self.getUid());
            ra.addFlashAttribute("msg", "0".equals(state) ? "账号已锁定" : "账号已解锁");
        } catch (BizException ex) {
            ra.addFlashAttribute("msg", ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/role")
    public String role(@RequestParam Integer uid, @RequestParam String role, HttpSession session, RedirectAttributes ra) {
        User self = (User) session.getAttribute(UserService.LOGIN_USER);
        try {
            userService.changeRole(uid, role, self.getUid());
            ra.addFlashAttribute("msg", "角色已更新");
        } catch (BizException ex) {
            ra.addFlashAttribute("msg", ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    @GetMapping("/logs")
    public String logs(@RequestParam(defaultValue = "1") long page, Model model) {
        model.addAttribute("pager", userService.logs(page, 15));
        return "admin/logs";
    }

    @GetMapping("/settings")
    public String settings() {
        return "admin/settings";
    }

    @PostMapping("/settings")
    public String saveSettings(@RequestParam Map<String, String> form, RedirectAttributes ra) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("site_name", Texts.cut(form.get("site_name"), 40));
        values.put("site_subtitle", Texts.cut(form.get("site_subtitle"), 80));
        values.put("footer", Texts.cut(form.get("footer"), 80));
        values.put("about", form.get("about") == null ? "" : form.get("about"));
        String pageSize = Texts.trim(form.get("page_size"));
        if (!pageSize.matches("\\d+")) {
            pageSize = "6";
        }
        values.put("page_size", pageSize);
        values.put("comment_audit", "1".equals(form.get("comment_audit")) ? "1" : "0");
        values.put("admin_message_pass", "1".equals(form.get("admin_message_pass")) ? "1" : "0");
        values.put("allow_comment", "1".equals(form.get("allow_comment")) ? "1" : "0");
        values.put("allow_register", "1".equals(form.get("allow_register")) ? "1" : "0");
        values.put("show_visit", "1".equals(form.get("show_visit")) ? "1" : "0");
        settingService.save(values);
        ra.addFlashAttribute("msg", "设置已保存");
        return "redirect:/admin/settings";
    }

    @GetMapping("/profile")
    public String profile() {
        return "admin/profile";
    }

    @PostMapping("/profile")
    public String saveProfile(@RequestParam String nickname, @RequestParam(required = false) String phone,
                              @RequestParam(value = "imgFile", required = false) MultipartFile imgFile,
                              HttpSession session, RedirectAttributes ra) {
        User user = (User) session.getAttribute(UserService.LOGIN_USER);
        try {
            userService.updateProfile(user, nickname, phone, fileStorage.save(imgFile));
            ra.addFlashAttribute("msg", "资料已更新");
        } catch (BizException ex) {
            ra.addFlashAttribute("msg", ex.getMessage());
        }
        return "redirect:/admin/profile";
    }

    @PostMapping("/password")
    public String password(@RequestParam String oldPassword, @RequestParam String newPassword,
                           HttpSession session, RedirectAttributes ra) {
        User user = (User) session.getAttribute(UserService.LOGIN_USER);
        try {
            userService.changePassword(user, oldPassword, newPassword);
            ra.addFlashAttribute("msg", "密码已修改");
        } catch (BizException ex) {
            ra.addFlashAttribute("msg", ex.getMessage());
        }
        return "redirect:/admin/profile";
    }
}
