package com.blog.web;

import com.blog.common.BizException;
import com.blog.entity.Article;
import com.blog.entity.User;
import com.blog.service.ArticleService;
import com.blog.service.ContentService;
import com.blog.service.FileStorage;
import com.blog.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;

@Controller
@RequestMapping("/admin/articles")
public class AdminArticleController {

    private final ArticleService articleService;
    private final ContentService contentService;
    private final FileStorage fileStorage;

    public AdminArticleController(ArticleService articleService, ContentService contentService, FileStorage fileStorage) {
        this.articleService = articleService;
        this.contentService = contentService;
        this.fileStorage = fileStorage;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "1") long page,
                       @RequestParam(required = false) String title,
                       Model model) {
        model.addAttribute("pager", articleService.pageAdmin(page, 10, title));
        model.addAttribute("title", title);
        return "admin/articles";
    }

    @GetMapping("/edit")
    public String edit(@RequestParam(required = false) Integer id, Model model) {
        Article article = id == null ? new Article() : articleService.get(id);
        if (article.getIsOpen() == null) {
            article.setIsOpen("1");
        }
        model.addAttribute("article", article);
        model.addAttribute("categories", contentService.categories());
        model.addAttribute("tags", contentService.tags());
        return "admin/article-form";
    }

    @PostMapping("/save")
    public String save(Article article,
                       @RequestParam(value = "tags", required = false) String[] tags,
                       @RequestParam(value = "logoFile", required = false) MultipartFile logoFile,
                       HttpSession session,
                       RedirectAttributes ra) {
        if (tags != null && tags.length > 0) {
            StringBuilder builder = new StringBuilder();
            for (String tag : tags) {
                if (builder.length() > 0) {
                    builder.append(", ");
                }
                builder.append(tag);
            }
            article.setTagNames(builder.toString());
        }
        try {
            User user = (User) session.getAttribute(UserService.LOGIN_USER);
            articleService.save(article, user, fileStorage.save(logoFile));
        } catch (BizException ex) {
            ra.addFlashAttribute("msg", ex.getMessage());
            return "redirect:/admin/articles/edit" + (article.getAid() == null ? "" : "?id=" + article.getAid());
        }
        ra.addFlashAttribute("msg", "文章已保存");
        return "redirect:/admin/articles";
    }

    @PostMapping("/delete")
    public String delete(@RequestParam Integer id, RedirectAttributes ra) {
        articleService.delete(id);
        ra.addFlashAttribute("msg", "文章已删除");
        return "redirect:/admin/articles";
    }

    @PostMapping("/open")
    public String open(@RequestParam Integer id, @RequestParam String isOpen, RedirectAttributes ra) {
        articleService.updateOpen(id, isOpen);
        ra.addFlashAttribute("msg", "1".equals(isOpen) ? "文章已公开" : "文章已设为私密");
        return "redirect:/admin/articles";
    }

    @PostMapping("/hot")
    public String hot(@RequestParam Integer id, @RequestParam Integer isHot, RedirectAttributes ra) {
        articleService.updateHot(id, isHot);
        ra.addFlashAttribute("msg", isHot != null && isHot == 1 ? "已设为热门" : "已取消热门");
        return "redirect:/admin/articles";
    }
}
