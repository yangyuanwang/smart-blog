package com.blog.web;

import com.blog.common.BizException;
import com.blog.common.Texts;
import com.blog.entity.Article;
import com.blog.entity.User;
import com.blog.service.ArticleService;
import com.blog.service.ContentService;
import com.blog.service.SettingService;
import com.blog.service.SmartService;
import com.blog.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.HashSet;
import java.util.Set;

@Controller
public class FrontController {

    private final ArticleService articleService;
    private final ContentService contentService;
    private final SettingService settingService;
    private final SmartService smartService;

    public FrontController(ArticleService articleService, ContentService contentService,
                           SettingService settingService, SmartService smartService) {
        this.articleService = articleService;
        this.contentService = contentService;
        this.settingService = settingService;
        this.smartService = smartService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("latest", articleService.latest(6));
        model.addAttribute("hot", articleService.hot(5));
        model.addAttribute("categories", contentService.categories());
        model.addAttribute("notices", contentService.topNotices(3));
        model.addAttribute("articleCount", articleService.publicCount());
        model.addAttribute("visitCount", articleService.visits());
        model.addAttribute("messageCount", contentService.messageCount());
        return "front/index";
    }

    @GetMapping("/articles")
    public String articles(@RequestParam(defaultValue = "1") long page,
                           @RequestParam(required = false) Integer cid,
                           @RequestParam(required = false) String q,
                           @RequestParam(required = false) String tag,
                           Model model) {
        model.addAttribute("pager", articleService.pagePublic(page, settingService.pageSize(), cid, q, tag));
        model.addAttribute("categories", contentService.categories());
        model.addAttribute("tags", contentService.tags());
        model.addAttribute("hot", articleService.hot(5));
        model.addAttribute("cid", cid);
        model.addAttribute("q", q);
        model.addAttribute("tag", tag);
        return "front/articles";
    }

    @GetMapping("/article/{id}")
    public String article(@PathVariable Integer id, HttpSession session, Model model) {
        User user = (User) session.getAttribute(UserService.LOGIN_USER);
        Article article = articleService.detail(id, UserService.isAdmin(user));
        model.addAttribute("article", article);
        model.addAttribute("html", smartService.render(article.getContent()));
        model.addAttribute("related", articleService.related(article, 4));
        model.addAttribute("comments", contentService.commentsOf(id));
        model.addAttribute("allowComment", settingService.enabled("allow_comment", true));
        return "front/article";
    }

    @PostMapping("/article/{id}/like")
    public String like(@PathVariable Integer id, HttpSession session, RedirectAttributes ra) {
        Set<Integer> liked = liked(session);
        if (liked.contains(id)) {
            ra.addFlashAttribute("msg", "这篇已经点过赞了");
            return "redirect:/article/" + id;
        }
        try {
            articleService.like(id);
            liked.add(id);
            ra.addFlashAttribute("msg", "已加上一赞");
        } catch (BizException ex) {
            ra.addFlashAttribute("msg", ex.getMessage());
        }
        return "redirect:/article/" + id;
    }

    @PostMapping("/article/{id}/comment")
    public String comment(@PathVariable Integer id, @RequestParam(required = false) String nickname,
                          @RequestParam String content, HttpSession session, RedirectAttributes ra) {
        if (!settingService.enabled("allow_comment", true)) {
            ra.addFlashAttribute("msg", "评论已关闭");
            return "redirect:/article/" + id;
        }
        User user = (User) session.getAttribute(UserService.LOGIN_USER);
        try {
            contentService.addComment(id, user, nickname, content, settingService.enabled("comment_audit", false));
            ra.addFlashAttribute("msg", settingService.enabled("comment_audit", false) ? "评论已提交，等待审核" : "评论已发布");
        } catch (BizException ex) {
            ra.addFlashAttribute("msg", ex.getMessage());
        }
        return "redirect:/article/" + id;
    }

    @GetMapping("/messages")
    public String messages(@RequestParam(defaultValue = "1") long page, HttpSession session, Model model) {
        User user = (User) session.getAttribute(UserService.LOGIN_USER);
        model.addAttribute("pager", contentService.frontMessages(page, user));
        model.addAttribute("allowComment", settingService.enabled("allow_comment", true));
        return "front/messages";
    }

    @PostMapping("/messages")
    public String addMessage(@RequestParam(required = false) String nickname,
                             @RequestParam(required = false) String content,
                             @RequestParam(required = false) String code,
                             @RequestParam(required = false) Integer parentId,
                             @RequestParam(defaultValue = "1") long page,
                             HttpServletRequest request, HttpSession session, RedirectAttributes ra) {
        User user = (User) session.getAttribute(UserService.LOGIN_USER);
        try {
            if (!settingService.enabled("allow_comment", true)) {
                throw new BizException("留言已关闭");
            }
            if (user == null) {
                checkCaptcha(code, session);
            }
            boolean skipAudit = user != null && UserService.isAdmin(user)
                    && settingService.enabled("admin_message_pass", true);
            contentService.addMessage(user, nickname, content, clientIp(request), parentId, skipAudit);
            ra.addFlashAttribute("note", skipAudit ? "留言已发布" : "留言提交成功，等待管理员审核");
        } catch (BizException ex) {
            ra.addFlashAttribute("noteError", ex.getMessage());
        }
        return "redirect:/messages?page=" + (page < 1 ? 1 : page);
    }

    @PostMapping("/messages/delete")
    public String deleteMessage(@RequestParam Integer id, @RequestParam(defaultValue = "1") long page,
                                HttpSession session, RedirectAttributes ra) {
        User user = (User) session.getAttribute(UserService.LOGIN_USER);
        try {
            contentService.deleteOwnMessage(id, user);
            ra.addFlashAttribute("note", "留言已删除");
        } catch (BizException ex) {
            ra.addFlashAttribute("noteError", ex.getMessage());
        }
        return "redirect:/messages?page=" + (page < 1 ? 1 : page);
    }

    @GetMapping("/diaries")
    public String diaries(Model model) {
        model.addAttribute("diaries", contentService.diaries());
        return "front/diaries";
    }

    @GetMapping("/links")
    public String links(Model model) {
        model.addAttribute("links", contentService.links());
        return "front/links";
    }

    @GetMapping("/about")
    public String about() {
        return "front/about";
    }

    private void checkCaptcha(String code, HttpSession session) {
        String right = (String) session.getAttribute("captcha");
        session.removeAttribute("captcha");
        if (right == null || Texts.blank(code) || !right.equalsIgnoreCase(Texts.trim(code))) {
            throw new BizException("验证码错误");
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (!Texts.blank(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @SuppressWarnings("unchecked")
    private Set<Integer> liked(HttpSession session) {
        Object value = session.getAttribute("liked");
        if (value instanceof Set) {
            return (Set<Integer>) value;
        }
        Set<Integer> liked = new HashSet<Integer>();
        session.setAttribute("liked", liked);
        return liked;
    }
}
