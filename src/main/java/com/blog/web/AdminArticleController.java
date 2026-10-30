package com.blog.web;

import com.blog.common.BizException;
import com.blog.common.Texts;
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
                       @RequestParam(required = false) String status,
                       Model model) {
        String filter = articleStatus(status);
        model.addAttribute("pager", articleService.pageAdmin(page, 10, title, filter));
        model.addAttribute("title", title);
        model.addAttribute("status", filter);
        return "admin/articles";
    }

    @GetMapping("/edit")
    public String edit(@RequestParam(required = false) Integer id, Model model) {
        Article article = id == null ? new Article() : articleService.get(id);
        if (id == null) {
            article.setPublishStatus("0");
        } else if (article.getPublishStatus() == null) {
            article.setPublishStatus("1".equals(article.getIsOpen()) ? "1" : "2");
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
    public String delete(@RequestParam Integer id,
                         @RequestParam(defaultValue = "1") long page,
                         @RequestParam(required = false) String title,
                         @RequestParam(required = false) String status,
                         RedirectAttributes ra) {
        articleService.delete(id);
        ra.addFlashAttribute("msg", "文章已删除");
        return back(ra, page, title, status);
    }

    @PostMapping("/status")
    public String status(@RequestParam Integer id,
                         @RequestParam String publishStatus,
                         @RequestParam(defaultValue = "1") long page,
                         @RequestParam(required = false) String title,
                         @RequestParam(required = false) String status,
                         RedirectAttributes ra) {
        try {
            ra.addFlashAttribute("msg", articleService.changeStatus(id, publishStatus));
        } catch (BizException ex) {
            ra.addFlashAttribute("msg", ex.getMessage());
        }
        return back(ra, page, title, status);
    }

    @PostMapping("/open")
    public String open(@RequestParam Integer id,
                       @RequestParam String isOpen,
                       @RequestParam(defaultValue = "1") long page,
                       @RequestParam(required = false) String title,
                       @RequestParam(required = false) String status,
                       RedirectAttributes ra) {
        String publishStatus = "1".equals(isOpen) ? "1" : "2";
        try {
            ra.addFlashAttribute("msg", articleService.changeStatus(id, publishStatus));
        } catch (BizException ex) {
            ra.addFlashAttribute("msg", ex.getMessage());
        }
        return back(ra, page, title, status);
    }

    @PostMapping("/hot")
    public String hot(@RequestParam Integer id,
                      @RequestParam Integer isHot,
                      @RequestParam(defaultValue = "1") long page,
                      @RequestParam(required = false) String title,
                      @RequestParam(required = false) String status,
                      RedirectAttributes ra) {
        articleService.updateHot(id, isHot);
        ra.addFlashAttribute("msg", isHot != null && isHot == 1 ? "已设为热门" : "已取消热门");
        return back(ra, page, title, status);
    }

    private String back(RedirectAttributes ra, long page, String title, String status) {
        ra.addAttribute("page", page);
        if (!Texts.blank(title)) {
            ra.addAttribute("title", title);
        }
        String filter = articleStatus(status);
        if (filter != null) {
            ra.addAttribute("status", filter);
        }
        return "redirect:/admin/articles";
    }

    private String articleStatus(String status) {
        if ("0".equals(status) || "1".equals(status) || "2".equals(status)) {
            return status;
        }
        return null;
    }
}
