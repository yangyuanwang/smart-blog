package com.blog.web;

import com.blog.common.BizException;
import com.blog.entity.Diary;
import com.blog.entity.Link;
import com.blog.entity.Notice;
import com.blog.service.ContentService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
public class AdminContentController {

    private final ContentService contentService;

    public AdminContentController(ContentService contentService) {
        this.contentService = contentService;
    }

    @GetMapping("/categories")
    public String categories(Model model) {
        model.addAttribute("categories", contentService.categories());
        model.addAttribute("tags", contentService.tags());
        return "admin/categories";
    }

    @PostMapping("/categories")
    public String saveCategory(@RequestParam String cname, RedirectAttributes ra) {
        return run(ra, "/admin/categories", new Job() {
            @Override
            public void run() {
                contentService.saveCategory(cname);
            }
        }, "栏目已添加");
    }

    @PostMapping("/categories/delete")
    public String deleteCategory(@RequestParam Integer cid, RedirectAttributes ra) {
        return run(ra, "/admin/categories", new Job() {
            @Override
            public void run() {
                contentService.deleteCategory(cid);
            }
        }, "栏目已删除");
    }

    @PostMapping("/tags")
    public String saveTag(@RequestParam Integer cid, @RequestParam String tname, RedirectAttributes ra) {
        return run(ra, "/admin/categories", new Job() {
            @Override
            public void run() {
                contentService.saveTag(cid, tname);
            }
        }, "标签已添加");
    }

    @PostMapping("/tags/delete")
    public String deleteTag(@RequestParam Integer tid, RedirectAttributes ra) {
        contentService.deleteTag(tid);
        ra.addFlashAttribute("msg", "标签已删除");
        return "redirect:/admin/categories";
    }

    @GetMapping("/comments")
    public String comments(@RequestParam(defaultValue = "1") long page,
                           @RequestParam(required = false) String status,
                           Model model) {
        model.addAttribute("pager", contentService.pageComments(page, 12, status));
        model.addAttribute("status", status);
        return "admin/comments";
    }

    @PostMapping("/comments/status")
    public String commentStatus(@RequestParam Integer id, @RequestParam String status, RedirectAttributes ra) {
        contentService.updateCommentStatus(id, status);
        ra.addFlashAttribute("msg", "1".equals(status) ? "评论已通过" : "评论已隐藏");
        return "redirect:/admin/comments";
    }

    @PostMapping("/comments/delete")
    public String deleteComment(@RequestParam Integer id, RedirectAttributes ra) {
        contentService.deleteComment(id);
        ra.addFlashAttribute("msg", "评论已删除");
        return "redirect:/admin/comments";
    }

    @GetMapping("/messages")
    public String messages(@RequestParam(defaultValue = "1") long page,
                           @RequestParam(required = false) String status, Model model) {
        model.addAttribute("pager", contentService.pageMessages(page, 12, status));
        model.addAttribute("status", status);
        return "admin/messages";
    }

    @PostMapping("/messages/status")
    public String messageStatus(@RequestParam Integer id, @RequestParam String status,
                                @RequestParam(defaultValue = "1") long page,
                                @RequestParam(required = false) String filter, RedirectAttributes ra) {
        try {
            contentService.updateMessageStatus(id, status);
            ra.addFlashAttribute("msg", "1".equals(status) ? "留言已通过" : "留言已驳回");
        } catch (BizException ex) {
            ra.addFlashAttribute("msg", ex.getMessage());
        }
        return "redirect:" + messageList(page, filter);
    }

    @PostMapping("/messages/reply")
    public String reply(@RequestParam Integer id, @RequestParam String reply, RedirectAttributes ra) {
        contentService.replyMessage(id, reply);
        ra.addFlashAttribute("msg", "回复已保存");
        return "redirect:/admin/messages";
    }

    @PostMapping("/messages/delete")
    public String deleteMessage(@RequestParam Integer id,
                                @RequestParam(defaultValue = "1") long page,
                                @RequestParam(required = false) String filter, RedirectAttributes ra) {
        contentService.deleteMessage(id);
        ra.addFlashAttribute("msg", "留言已删除");
        return "redirect:" + messageList(page, filter);
    }

    private String messageList(long page, String filter) {
        String path = "/admin/messages?page=" + Math.max(page, 1);
        if ("0".equals(filter) || "1".equals(filter) || "2".equals(filter)) {
            path += "&status=" + filter;
        }
        return path;
    }

    @GetMapping("/diaries")
    public String diaries(@RequestParam(required = false) Integer id, Model model) {
        model.addAttribute("diaries", contentService.diaries());
        model.addAttribute("diary", id == null ? new Diary() : contentService.getDiary(id));
        return "admin/diaries";
    }

    @PostMapping("/diaries")
    public String saveDiary(Diary diary, RedirectAttributes ra) {
        return run(ra, "/admin/diaries", new Job() {
            @Override
            public void run() {
                contentService.saveDiary(diary);
            }
        }, "日记已保存");
    }

    @PostMapping("/diaries/delete")
    public String deleteDiary(@RequestParam Integer id, RedirectAttributes ra) {
        contentService.deleteDiary(id);
        ra.addFlashAttribute("msg", "日记已删除");
        return "redirect:/admin/diaries";
    }

    @GetMapping("/notices")
    public String notices(@RequestParam(required = false) Integer id, Model model) {
        model.addAttribute("notices", contentService.notices());
        Notice notice = id == null ? new Notice() : contentService.getNotice(id);
        if (notice != null && notice.getIsTop() == null) {
            notice.setIsTop("0");
        }
        model.addAttribute("notice", notice == null ? new Notice() : notice);
        return "admin/notices";
    }

    @PostMapping("/notices")
    public String saveNotice(Notice notice, RedirectAttributes ra) {
        return run(ra, "/admin/notices", new Job() {
            @Override
            public void run() {
                contentService.saveNotice(notice);
            }
        }, "公告已保存");
    }

    @PostMapping("/notices/delete")
    public String deleteNotice(@RequestParam Integer id, RedirectAttributes ra) {
        contentService.deleteNotice(id);
        ra.addFlashAttribute("msg", "公告已删除");
        return "redirect:/admin/notices";
    }

    @GetMapping("/links")
    public String links(@RequestParam(required = false) Integer id, Model model) {
        model.addAttribute("links", contentService.links());
        Link editing = new Link();
        if (id != null) {
            for (Link link : contentService.links()) {
                if (id.equals(link.getId())) {
                    editing = link;
                    break;
                }
            }
        }
        model.addAttribute("link", editing);
        return "admin/links";
    }

    @PostMapping("/links")
    public String saveLink(Link link, RedirectAttributes ra) {
        return run(ra, "/admin/links", new Job() {
            @Override
            public void run() {
                contentService.saveLink(link);
            }
        }, "友链已保存");
    }

    @PostMapping("/links/delete")
    public String deleteLink(@RequestParam Integer id, RedirectAttributes ra) {
        contentService.deleteLink(id);
        ra.addFlashAttribute("msg", "友链已删除");
        return "redirect:/admin/links";
    }

    private String run(RedirectAttributes ra, String path, Job job, String success) {
        try {
            job.run();
            ra.addFlashAttribute("msg", success);
        } catch (BizException ex) {
            ra.addFlashAttribute("msg", ex.getMessage());
        }
        return "redirect:" + path;
    }

    private interface Job {
        void run();
    }
}
