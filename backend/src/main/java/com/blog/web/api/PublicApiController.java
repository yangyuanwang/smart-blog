package com.blog.web.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.PageResult;
import com.blog.common.Result;
import com.blog.common.Texts;
import com.blog.entity.Article;
import com.blog.entity.Diary;
import com.blog.entity.Link;
import com.blog.entity.Notice;
import com.blog.entity.Project;
import com.blog.mapper.ProjectMapper;
import com.blog.security.Auths;
import com.blog.security.LoginUser;
import com.blog.service.ArticleService;
import com.blog.service.ContentService;
import com.blog.service.SmartService;
import com.blog.service.SettingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 公开内容 API：首页聚合、文章列表/详情、栏目/标签、日记、公告、友链、作品集、搜索。
 */
@Tag(name = "public", description = "公开内容")
@RestController
public class PublicApiController {

    private final ArticleService articleService;
    private final ContentService contentService;
    private final SettingService settingService;
    private final SmartService smartService;
    private final ProjectMapper projectMapper;
    private final com.blog.service.ViewCounterService viewCounterService;

    /**
     * 构造公开内容控制器。
     *
     * @param articleService 文章服务
     * @param contentService 内容服务
     * @param settingService 站点设置服务
     * @param smartService   Markdown 渲染服务
     * @param projectMapper  作品集 Mapper
     * @param viewCounterService 浏览量计数服务
     */
    public PublicApiController(ArticleService articleService, ContentService contentService,
                              SettingService settingService, SmartService smartService,
                              ProjectMapper projectMapper, com.blog.service.ViewCounterService viewCounterService) {
        this.articleService = articleService;
        this.contentService = contentService;
        this.settingService = settingService;
        this.smartService = smartService;
        this.projectMapper = projectMapper;
        this.viewCounterService = viewCounterService;
    }

    /**
     * 首页聚合数据。
     *
     * @return 最新文章、精选作品、公告、日记、站点信息与内容统计
     */
    @Operation(summary = "首页聚合")
    @GetMapping("/api/v1/home")
    public Result<Map<String, Object>> home() {
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("latest", articleService.latest(4));
        data.put("featuredProjects", featuredProjects());
        data.put("notices", contentService.topNotices(3));
        data.put("diaries", contentService.recentDiaries(3));
        data.put("site", publicSite());
        // 首页 Hero 的实时统计（文章数 / 已发布 / 总访问 / 标签数）
        Map<String, Object> stats = new HashMap<String, Object>();
        stats.put("articles", articleService.publicCount());
        stats.put("visits", articleService.visits());
        stats.put("tags", contentService.tags().stream().filter(tag -> tag.getTid() != null).count());
        stats.put("projects", projectMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Project>().eq("status", "1")));
        data.put("stats", stats);
        return Result.ok(data);
    }

    /**
     * 文章分页列表（列表不返回正文与 AI 摘要，减小载荷）。
     *
     * @param page 页码
     * @param size 每页条数（默认站点设置）
     * @param cid  栏目筛选
     * @param q    关键词
     * @param tag  标签筛选
     * @return 分页文章
     */
    @Operation(summary = "文章列表")
    @GetMapping("/api/v1/articles")
    public Result<PageResult<Article>> articles(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(required = false) Long size,
                                                @RequestParam(required = false) Integer cid,
                                                @RequestParam(required = false) String q,
                                                @RequestParam(required = false) String tag) {
        long pageSize = size == null ? settingService.pageSize() : Math.min(Math.max(size, 1), 50);
        Page<Article> result = articleService.pagePublic(page, pageSize, cid, q, tag);
        result.getRecords().forEach(article -> article.setContent(null));
        return Result.ok(PageResult.of(result));
    }

    /**
     * 文章详情：Markdown 渲染 HTML + TOC + 上一篇/下一篇 + 相关推荐 + 评论。
     * 草稿与下线文章仅管理员可见；浏览量由 POST /view 单独上报。
     *
     * @param id 文章 ID
     * @return 详情数据
     */
    @Operation(summary = "文章详情")
    @GetMapping("/api/v1/articles/{id}")
    public Result<Map<String, Object>> article(@PathVariable Integer id) {
        LoginUser viewer = Auths.currentUser();
        boolean admin = viewer != null && viewer.admin();
        Article article = articleService.get(id);
        if (!"1".equals(article.getIsOpen()) && !admin) {
            throw new com.blog.common.BizException("这篇文章还没有公开");
        }
        SmartService.Rendered rendered = smartService.renderWithToc(article.getContent());
        Map<String, Object> data = new HashMap<String, Object>();
        article.setContent(null);
        data.put("article", article);
        data.put("html", rendered.getHtml());
        data.put("toc", rendered.getToc());
        data.put("prev", articleService.previousPublished(article));
        data.put("next", articleService.nextPublished(article));
        data.put("related", relatedWithoutContent(article, 4));
        data.put("comments", contentService.commentsOf(id));
        data.put("allowComment", settingService.enabled("allow_comment", true));
        data.put("views", (article.getVisitCount() == null ? 0 : article.getVisitCount())
                + viewCounterService.buffered(id));
        return Result.ok(data);
    }

    /**
     * 热门文章榜。
     *
     * @param limit 条数（默认 5，上限 20）
     * @return 文章列表（不含正文）
     */
    @Operation(summary = "热门文章")
    @GetMapping("/api/v1/articles/hot")
    public Result<List<Article>> hot(@RequestParam(defaultValue = "5") int limit) {
        List<Article> list = articleService.hot(Math.min(Math.max(limit, 1), 20));
        list.forEach(article -> article.setContent(null));
        return Result.ok(list);
    }

    /**
     * 全文搜索（标题/摘要/正文 LIKE，供 ⌘K 面板调用）。
     *
     * @param q     关键词
     * @param limit 上限（默认 10）
     * @return 命中文章（不含正文）
     */
    @Operation(summary = "搜索")
    @GetMapping("/api/v1/search")
    public Result<List<Article>> search(@RequestParam String q, @RequestParam(defaultValue = "10") int limit) {
        if (Texts.blank(q)) {
            return Result.ok(List.of());
        }
        Page<Article> result = articleService.pagePublic(1, Math.min(Math.max(limit, 1), 20), null, q, null);
        result.getRecords().forEach(article -> article.setContent(null));
        return Result.ok(result.getRecords());
    }

    /**
     * 栏目列表（含文章计数）。
     *
     * @return 栏目列表
     */
    @Operation(summary = "栏目列表")
    @GetMapping("/api/v1/categories")
    public Result<List<com.blog.entity.Category>> categories() {
        return Result.ok(contentService.categories());
    }

    /**
     * 标签列表（含公开文章计数）。
     *
     * @return 标签列表
     */
    @Operation(summary = "标签列表")
    @GetMapping("/api/v1/tags")
    public Result<List<Map<String, Object>>> tags() {
        List<com.blog.entity.Tag> tags = contentService.tags();
        Map<Integer, Long> counts = new HashMap<Integer, Long>();
        for (Map<String, Object> row : articleService.countByTagId()) {
            Object tagId = row.get("tagId");
            Object cnt = row.get("cnt");
            if (tagId instanceof Number number && cnt instanceof Number count) {
                counts.put(number.intValue(), count.longValue());
            }
        }
        List<Map<String, Object>> data = new java.util.ArrayList<Map<String, Object>>();
        for (com.blog.entity.Tag tag : tags) {
            Map<String, Object> item = new HashMap<String, Object>();
            item.put("tid", tag.getTid());
            item.put("tname", tag.getTname());
            item.put("cid", tag.getCid());
            item.put("articleCount", counts.getOrDefault(tag.getTid(), 0L));
            data.add(item);
        }
        return Result.ok(data);
    }

    /**
     * 公告列表。
     *
     * @return 公告列表
     */
    @Operation(summary = "公告列表")
    @GetMapping("/api/v1/notices")
    public Result<List<Notice>> notices() {
        return Result.ok(contentService.notices());
    }

    /**
     * 日记时间线。
     *
     * @return 日记列表
     */
    @Operation(summary = "日记时间线")
    @GetMapping("/api/v1/diaries")
    public Result<List<Diary>> diaries() {
        return Result.ok(contentService.diaries());
    }

    /**
     * 友链列表。
     *
     * @return 友链列表
     */
    @Operation(summary = "友链列表")
    @GetMapping("/api/v1/links")
    public Result<List<Link>> links() {
        return Result.ok(contentService.links());
    }

    /**
     * 作品集列表（仅展示状态）。
     *
     * @return 作品列表
     */
    @Operation(summary = "作品集列表")
    @GetMapping("/api/v1/projects")
    public Result<List<Project>> projects() {
        return Result.ok(projectMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Project>()
                .eq("status", "1")
                .orderByDesc("featured")
                .orderByAsc("sort")
                .orderByDesc("id")));
    }

    /**
     * 站点公开设置子集（站名/页脚/关于等）。
     *
     * @return 设置键值
     */
    @Operation(summary = "站点设置")
    @GetMapping("/api/v1/site")
    public Result<Map<String, String>> site() {
        return Result.ok(publicSite());
    }

    /**
     * 精选作品（首页展示位）。
     *
     * @return 精选作品列表
     */
    private List<Project> featuredProjects() {
        return projectMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Project>()
                .eq("status", "1")
                .eq("featured", "1")
                .orderByAsc("sort")
                .last("limit 6"));
    }

    /**
     * 相关推荐并去掉正文载荷。
     *
     * @param article 当前文章
     * @param limit   条数
     * @return 相关文章
     */
    private List<Article> relatedWithoutContent(Article article, int limit) {
        List<Article> list = articleService.related(article, limit);
        list.forEach(item -> item.setContent(null));
        return list;
    }

    /**
     * 站点设置公开子集（不含内部开关）。
     *
     * @return 公开键值
     */
    private Map<String, String> publicSite() {
        Map<String, String> all = settingService.all();
        Map<String, String> data = new HashMap<String, String>();
        for (String key : List.of("site_name", "site_footer", "about", "icp")) {
            if (all.containsKey(key)) {
                data.put(key, all.get(key));
            }
        }
        return data;
    }
}
