package com.blog.web;

import com.blog.common.BizException;
import com.blog.common.Texts;
import com.blog.entity.User;
import com.blog.service.SettingService;
import com.blog.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Random;

@Controller
public class AuthController {

    private static final char[] CODE = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private final UserService userService;
    private final SettingService settingService;
    private final Random random = new Random();

    public AuthController(UserService userService, SettingService settingService) {
        this.userService = userService;
        this.settingService = settingService;
    }

    @GetMapping("/captcha")
    public void captcha(HttpSession session, HttpServletResponse response) throws IOException {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            builder.append(CODE[random.nextInt(CODE.length)]);
        }
        String code = builder.toString();
        session.setAttribute("captcha", code);
        BufferedImage image = new BufferedImage(120, 42, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(245, 239, 230));
        graphics.fillRect(0, 0, 120, 42);
        graphics.setFont(new Font("SansSerif", Font.BOLD, 26));
        for (int i = 0; i < code.length(); i++) {
            graphics.setColor(new Color(40 + random.nextInt(80), 30 + random.nextInt(40), 20));
            graphics.drawString(String.valueOf(code.charAt(i)), 12 + i * 26, 30);
        }
        graphics.setColor(new Color(160, 120, 90));
        for (int i = 0; i < 6; i++) {
            graphics.drawLine(random.nextInt(120), random.nextInt(42), random.nextInt(120), random.nextInt(42));
        }
        graphics.dispose();
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("image/png");
        ImageIO.write(image, "png", response.getOutputStream());
    }

    @GetMapping("/login")
    public String loginPage() {
        return "front/login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password, @RequestParam String code,
                        HttpServletRequest request, HttpSession session, Model model) {
        return doLogin(username, password, code, request, session, model, false);
    }

    @GetMapping("/admin/login")
    public String adminLoginPage(@RequestParam(required = false) String denied, Model model) {
        if ("1".equals(denied)) {
            model.addAttribute("error", "当前账号没有后台权限");
        }
        return "admin/login";
    }

    @PostMapping("/admin/login")
    public String adminLogin(@RequestParam String username, @RequestParam String password, @RequestParam String code,
                             HttpServletRequest request, HttpSession session, Model model) {
        return doLogin(username, password, code, request, session, model, true);
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("open", settingService.enabled("allow_register", true));
        return "front/register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String username, @RequestParam String password,
                           @RequestParam(required = false) String nickname, @RequestParam(required = false) String phone,
                           @RequestParam String code, HttpSession session, Model model) {
        model.addAttribute("open", settingService.enabled("allow_register", true));
        if (!settingService.enabled("allow_register", true)) {
            model.addAttribute("error", "注册已关闭");
            return "front/register";
        }
        try {
            checkCaptcha(code, session);
            userService.register(username, password, nickname, phone);
        } catch (BizException ex) {
            model.addAttribute("error", ex.getMessage());
            return "front/register";
        }
        model.addAttribute("msg", "注册成功，请登录");
        return "front/login";
    }

    @GetMapping("/reset")
    public String resetPage() {
        return "front/reset";
    }

    @PostMapping("/reset")
    public String reset(@RequestParam String username, @RequestParam String phone, @RequestParam String password,
                        @RequestParam String code, HttpSession session, Model model) {
        try {
            checkCaptcha(code, session);
            userService.resetPassword(username, phone, password);
        } catch (BizException ex) {
            model.addAttribute("error", ex.getMessage());
            return "front/reset";
        }
        model.addAttribute("msg", "密码已重置，请登录");
        return "front/login";
    }

    @PostMapping("/logout")
    public String logout(@RequestParam(required = false) String next, HttpSession session) {
        session.invalidate();
        if (next != null && next.startsWith("/") && !next.startsWith("//")) {
            return "redirect:" + next;
        }
        return "redirect:/";
    }

    private String doLogin(String username, String password, String code, HttpServletRequest request,
                           HttpSession session, Model model, boolean admin) {
        try {
            String right = checkCaptcha(code, session);
            User user = userService.login(username, password, code, right, clientIp(request), admin);
            request.changeSessionId();
            session = request.getSession();
            session.setAttribute(UserService.LOGIN_USER, user);
        } catch (BizException ex) {
            model.addAttribute("error", ex.getMessage());
            return admin ? "admin/login" : "front/login";
        }
        return admin ? "redirect:/admin" : "redirect:/";
    }

    private String checkCaptcha(String code, HttpSession session) {
        String right = (String) session.getAttribute("captcha");
        session.removeAttribute("captcha");
        if (right == null || Texts.blank(code) || !right.equalsIgnoreCase(code.trim())) {
            throw new BizException("验证码错误");
        }
        return right;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (!Texts.blank(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
