package com.blog.web.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.ai.ArticleAiService;
import com.blog.common.BizException;
import com.blog.common.Ip;
import com.blog.common.PageResult;
import com.blog.common.Result;
import com.blog.common.Texts;
import com.blog.entity.Article;
import com.blog.entity.Category;
import com.blog.entity.Diary;
import com.blog.entity.Link;
import com.blog.entity.LinkApply;
import com.blog.entity.Message;
import com.blog.entity.Notice;
import com.blog.entity.Project;

import com.blog.entity.User;
import com.blog.mapper.CategoryMapper;
import com.blog.mapper.LinkApplyMapper;
import com.blog.mapper.ProjectMapper;
import com.blog.mapper.TagMapper;
import com.blog.mapper.UserMapper;
import com.blog.security.Auths;
import com.blog.service.ArticleService;
import com.blog.service.ContentService;
import com.blog.service.FileStorage;
import com.blog.service.SettingService;
import com.blog.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理端 API：仪表盘、文章、栏目/标签、评论/留言审核、友链申请审核、日记、公告、友链、
 * 作品集、用户管理、登录日志、站点设置。全部要求 ADMIN 角色（SecurityConfig 网关 + 方法级兜底）。
 */
@Tag(name = "admin", description = "管理端")
@RestController
@RequestMapping("/api/v1/admin")
public class AdminApiController {

    private final ArticleService articleService;
    private final ContentService contentService;
    private final UserService userService;
    private final SettingService settingService;
    private final ArticleAiService articleAiService;
    private final FileStorage fileStorage;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final ProjectMapper projectMapper;
    private final LinkApplyMapper linkApplyMapper;
    private final UserMapper userMapper;
    private final com.blog.security.JwtService jwtService;
    private final Ip ip;

    /**
     * 构造管理端控制器。
     *
     * @param articleService    文章服务
     * @param contentService    内容服务
     * @param userService       用户服务
     * @param settingService    站点设置服务
     * @param articleAiService  AI 摘要服务
     * @param fileStorage       上传存储
     * @param categoryMapper    栏目 Mapper
     * @param tagMapper         标签 Mapper
     * @param projectMapper     作品集 Mapper
     * @param linkApplyMapper   友链申请 Mapper
     * @param userMapper        用户 Mapper
     * @param jwtService        JWT 服务（锁定用户时吊销会话）
     * @param ip                客户端 IP 提取
     */
    public AdminApiController(ArticleService articleService, ContentService contentService,
                              UserService userService, SettingService settingService,
                              ArticleAiService articleAiService, FileStorage fileStorage,
                              CategoryMapper categoryMapper, TagMapper tagMapper,
                              ProjectMapper projectMapper, LinkApplyMapper linkApplyMapper,
                              UserMapper userMapper, com.blog.security.JwtService jwtService, Ip ip) {
        this.articleService = articleService;
        this.contentService = contentService;
        this.userService = userService;
        this.settingService = settingService;
        this.articleAiService = articleAiService;
        this.fileStorage = fileStorage;
        this.categoryMapper = categoryMapper;
        this.tagMapper = tagMapper;
        this.projectMapper = projectMapper;
        this.linkApplyMapper = linkApplyMapper;
        this.userMapper = userMapper;
        this.jwtService = jwtService;
        this.ip = ip;
    }

    /**
     * 审核状态请求体。
     *
     * @param status 目标状态
     */
    public record StatusRequest(@NotBlank String status) {
    }

    /**
     * 友链申请拒绝请求体。
     *
     * @param remark 拒绝理由
     */
    public record RejectRequest(@Size(max = 200) String remark) {
    }

    /**
     * 留言回复请求体。
     *
     * @param reply 回复内容
     */
    public record ReplyRequest(@NotBlank @Size(max = 1000) String reply) {
    }

    /**
     * 文章保存请求体（新建与更新共用；封面通过 /upload 单独上传后填 logo）。
     *
     * @param aid           文章 ID（更新时必填）
     * @param title         标题
     * @param digest        摘要（可空，默认自动摘要）
     * @param content       Markdown 正文
     * @param cid           栏目 ID
     * @param tagNames      标签逗号文本
     * @param publishStatus 0 草稿 1 发布 2 下线
     * @param isHot         是否热门
     * @param isTop         是否置顶
     * @param logo          封面地址
     * @param seoTitle      SEO 标题
     * @param seoKeywords   SEO 关键词
     * @param seoDesc       SEO 描述
     */
    public record ArticleForm(Integer aid, @NotBlank String title, String digest, @NotBlank String content,
                              Integer cid, String tagNames, @NotBlank String publishStatus,
                              Integer isHot, Integer isTop, String logo,
                              String seoTitle, String seoKeywords, String seoDesc) {
    }

    /**
     * 站点设置保存请求体。
     *
     * @param values 键值对
     */
    public record SettingsForm(Map<String, String> values) {
    }

    /**
     * 用户状态/角色/重置密码请求体。
     *
     * @param value 状态或角色值，或新密码
     */
    public record UserValueRequest(@NotBlank String value) {
    }

    /**
     * 仪表盘统计。
     *
     * @return 内容与互动总量
     */
    @Operation(summary = "仪表盘")
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("articles", articleService.count());
        data.put("published", articleService.publicCount());
        data.put("visits", articleService.visits());
        data.put("comments", contentService.commentCount());
        data.put("messages", contentService.messageCount());
        data.put("pendingMessages", contentService.pendingMessageCount());
        data.put("pendingLinkApplies", linkApplyMapper.selectCount(new QueryWrapper<LinkApply>().eq("status", "0")));
        data.put("categories", categoryMapper.selectCount(null));
        data.put("links", contentService.linkCount());
        data.put("projects", projectMapper.selectCount(null));
        data.put("users", userMapper.selectCount(null));
        return Result.ok(data);
    }

    /**
     * 文章分页（含草稿与下线）。
     *
     * @param page 页码
     * @param size 条数
     * @param title 标题筛选
     * @param publishStatus 状态筛选
     * @return 分页文章
     */
    @Operation(summary = "文章管理列表")
    @GetMapping("/articles")
    public Result<PageResult<Article>> articles(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size,
                                                @RequestParam(required = false) String title,
                                                @RequestParam(required = false) String publishStatus) {
        Page<Article> result = articleService.pageAdmin(page, Math.min(Math.max(size, 1), 50), title, publishStatus);
        result.getRecords().forEach(article -> article.setContent(null));
        return Result.ok(PageResult.of(result));
    }

    /**
     * 文章详情（编辑表单回显，含正文）。
     *
     * @param id 文章 ID
     * @return 文章完整数据
     */
    @Operation(summary = "文章编辑详情")
    @GetMapping("/articles/{id}")
    public Result<Article> article(@PathVariable Integer id) {
        return Result.ok(articleService.get(id));
    }

    /**
     * 新建文章（保存后若为发布状态则异步触发 AI 摘要）。
     *
     * @param form 文章表单
     * @return 文章 ID
     */
    @Operation(summary = "新建文章")
    @PostMapping("/articles")
    public Result<Map<String, Object>> createArticle(@RequestBody ArticleForm form) {
        Article article = toArticle(form);
        Integer aid = articleService.save(article, author(), null);
        triggerAi(article);
        return Result.ok(Map.of("aid", aid));
    }

    /**
     * 更新文章（状态变为发布时同样触发 AI 摘要）。
     *
     * @param id   文章 ID
     * @param form 文章表单
     * @return 成功响应
     */
    @Operation(summary = "更新文章")
    @PutMapping("/articles/{id}")
    public Result<Void> updateArticle(@PathVariable Integer id, @RequestBody ArticleForm form) {
        Article article = toArticle(form);
        article.setAid(id);
        articleService.save(article, author(), null);
        triggerAi(article);
        return Result.ok();
    }

    /**
     * 修改文章状态（草稿/发布/下线状态机）。
     *
     * @param id      文章 ID
     * @param request 目标状态
     * @return 操作结果文案
     */
    @Operation(summary = "文章状态变更")
    @PutMapping("/articles/{id}/status")
    public Result<Map<String, String>> changeStatus(@PathVariable Integer id, @RequestBody StatusRequest request) {
        return Result.ok(Map.of("message", articleService.changeStatus(id, request.status())));
    }

    /**
     * 设置热门标记。
     *
     * @param id    文章 ID
     * @param request 目标值
     * @return 成功响应
     */
    @Operation(summary = "设置热门")
    @PutMapping("/articles/{id}/hot")
    public Result<Void> updateHot(@PathVariable Integer id, @RequestBody StatusRequest request) {
        articleService.updateHot(id, "1".equals(request.status()) ? 1 : 0);
        return Result.ok();
    }

    /**
     * 删除文章。
     *
     * @param id 文章 ID
     * @return 成功响应
     */
    @Operation(summary = "删除文章")
    @DeleteMapping("/articles/{id}")
    public Result<Void> deleteArticle(@PathVariable Integer id) {
        articleService.delete(id);
        return Result.ok();
    }

    /**
     * 手动重新生成 AI 摘要。
     *
     * @param id 文章 ID
     * @return 成功响应（异步执行）
     */
    @Operation(summary = "重生成 AI 摘要")
    @PutMapping("/articles/{id}/ai-summary")
    public Result<Void> regenAiSummary(@PathVariable Integer id) {
        articleAiService.generateSummary(id);
        return Result.ok();
    }

    /**
     * 上传图片（封面/头像/作品封面），返回可访问地址。
     *
     * @param file 图片文件
     * @return 图片地址
     */
    @Operation(summary = "上传图片")
    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestPart("file") MultipartFile file) {
        String url = fileStorage.save(file);
        return Result.ok(Map.of("url", url == null ? "" : url));
    }

    /**
     * 栏目列表。
     *
     * @return 栏目列表（含计数）
     */
    @Operation(summary = "栏目列表")
    @GetMapping("/categories")
    public Result<List<Category>> categories() {
        return Result.ok(contentService.categories());
    }

    /**
     * 新增栏目。
     *
     * @param category 栏目实体（仅用 cname）
     * @return 成功响应
     */
    @Operation(summary = "新增栏目")
    @PostMapping("/categories")
    public Result<Void> createCategory(@RequestBody Category category) {
        contentService.saveCategory(category.getCname());
        return Result.ok();
    }

    /**
     * 栏目重命名（V1.1 第二批补齐能力）。
     *
     * @param cid      栏目 ID
     * @param category 实体（仅用 cname）
     * @return 成功响应
     */
    @Operation(summary = "栏目重命名")
    @PutMapping("/categories/{cid}")
    public Result<Void> renameCategory(@PathVariable Integer cid, @RequestBody Category category) {
        contentService.renameCategory(cid, category.getCname());
        return Result.ok();
    }

    /**
     * 删除栏目（栏目下有文章时拒绝）。
     *
     * @param cid 栏目 ID
     * @return 成功响应
     */
    @Operation(summary = "删除栏目")
    @DeleteMapping("/categories/{cid}")
    public Result<Void> deleteCategory(@PathVariable Integer cid) {
        contentService.deleteCategory(cid);
        return Result.ok();
    }

    /**
     * 标签列表。
     *
     * @return 标签列表
     */
    @Operation(summary = "标签列表")
    @GetMapping("/tags")
    public Result<List<com.blog.entity.Tag>> tags() {
        return Result.ok(contentService.tags());
    }

    /**
     * 新增标签。
     *
     * @param tag 标签实体（cid + tname）
     * @return 成功响应
     */
    @Operation(summary = "新增标签")
    @PostMapping("/tags")
    public Result<Void> createTag(@RequestBody com.blog.entity.Tag tag) {
        contentService.saveTag(tag.getCid(), tag.getTname());
        return Result.ok();
    }

    /**
     * 编辑标签（改名或换栏目）。
     *
     * @param tid 标签 ID
     * @param tag 标签实体
     * @return 成功响应
     */
    @Operation(summary = "编辑标签")
    @PutMapping("/tags/{tid}")
    public Result<Void> updateTag(@PathVariable Integer tid, @RequestBody com.blog.entity.Tag tag) {
        contentService.updateTag(tid, tag.getTname(), tag.getCid());
        return Result.ok();
    }

    /**
     * 删除标签（同时清理文章关联）。
     *
     * @param tid 标签 ID
     * @return 成功响应
     */
    @Operation(summary = "删除标签")
    @DeleteMapping("/tags/{tid}")
    public Result<Void> deleteTag(@PathVariable Integer tid) {
        contentService.deleteTag(tid);
        return Result.ok();
    }

    /**
     * 评论分页（批量补文章标题，消除 N+1）。
     *
     * @param page   页码
     * @param size   条数
     * @param status 状态筛选
     * @return 分页评论
     */
    @Operation(summary = "评论审核列表")
    @GetMapping("/comments")
    public Result<PageResult<com.blog.entity.Comment>> comments(@RequestParam(defaultValue = "1") long page,
                                                                @RequestParam(defaultValue = "10") long size,
                                                                @RequestParam(required = false) String status) {
        return Result.ok(PageResult.of(contentService.pageComments(page, Math.min(Math.max(size, 1), 50), status)));
    }

    /**
     * 评论审核（通过/隐藏）。
     *
     * @param id      评论 ID
     * @param request 目标状态
     * @return 成功响应
     */
    @Operation(summary = "评论审核")
    @PutMapping("/comments/{id}/audit")
    public Result<Void> auditComment(@PathVariable Integer id, @RequestBody StatusRequest request) {
        contentService.updateCommentStatus(id, request.status());
        return Result.ok();
    }

    /**
     * 删除评论。
     *
     * @param id 评论 ID
     * @return 成功响应
     */
    @Operation(summary = "删除评论")
    @DeleteMapping("/comments/{id}")
    public Result<Void> deleteComment(@PathVariable Integer id) {
        contentService.deleteComment(id);
        return Result.ok();
    }

    /**
     * 留言分页。
     *
     * @param page   页码
     * @param size   条数
     * @param status 状态筛选
     * @return 分页留言
     */
    @Operation(summary = "留言列表")
    @GetMapping("/messages")
    public Result<PageResult<Message>> messages(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size,
                                                @RequestParam(required = false) String status) {
        return Result.ok(PageResult.of(contentService.pageMessages(page, Math.min(Math.max(size, 1), 50), status)));
    }

    /**
     * 留言审核（通过/驳回）。
     *
     * @param id      留言 ID
     * @param request 目标状态
     * @return 成功响应
     */
    @Operation(summary = "留言审核")
    @PutMapping("/messages/{id}/audit")
    public Result<Void> auditMessage(@PathVariable Integer id, @RequestBody StatusRequest request) {
        contentService.updateMessageStatus(id, request.status());
        return Result.ok();
    }

    /**
     * 回复留言（通过并写入回复）。
     *
     * @param id      留言 ID
     * @param request 回复内容
     * @return 成功响应
     */
    @Operation(summary = "回复留言")
    @PutMapping("/messages/{id}/reply")
    public Result<Void> replyMessage(@PathVariable Integer id, @RequestBody ReplyRequest request) {
        contentService.replyMessage(id, request.reply());
        return Result.ok();
    }

    /**
     * 删除留言（含楼中楼）。
     *
     * @param id 留言 ID
     * @return 成功响应
     */
    @Operation(summary = "删除留言")
    @DeleteMapping("/messages/{id}")
    public Result<Void> deleteMessage(@PathVariable Integer id) {
        contentService.deleteMessage(id);
        return Result.ok();
    }

    /**
     * 友链申请分页。
     *
     * @param page   页码
     * @param size   条数
     * @param status 状态筛选
     * @return 分页申请
     */
    @Operation(summary = "友链申请列表")
    @GetMapping("/link-applies")
    public Result<PageResult<LinkApply>> linkApplies(@RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "10") long size,
                                                     @RequestParam(required = false) String status) {
        QueryWrapper<LinkApply> query = new QueryWrapper<LinkApply>().orderByDesc("id");
        if (Texts.blank(status)) {
            query.eq("status", status);
        }
        Page<LinkApply> result = linkApplyMapper.selectPage(new Page<LinkApply>(page, Math.min(Math.max(size, 1), 50)), query);
        return Result.ok(PageResult.of(result));
    }

    /**
     * 通过友链申请：申请转正为友链（默认排序追加）。
     *
     * @param id 申请 ID
     * @return 成功响应
     */
    @Operation(summary = "通过友链申请")
    @PutMapping("/link-applies/{id}/approve")
    public Result<Void> approveLinkApply(@PathVariable Integer id) {
        LinkApply apply = requireApply(id);
        Link link = new Link();
        link.setSiteName(apply.getSiteName());
        link.setSiteUrl(apply.getSiteUrl());
        link.setSiteDesc(apply.getSiteDesc());
        link.setSort(0);
        contentService.saveLink(link);
        linkApplyMapper.update(null, new UpdateWrapper<LinkApply>()
                .eq("id", id)
                .set("status", "1")
                .set("remark", "已通过"));
        return Result.ok();
    }

    /**
     * 拒绝友链申请（可附理由）。
     *
     * @param id      申请 ID
     * @param request 拒绝理由
     * @return 成功响应
     */
    @Operation(summary = "拒绝友链申请")
    @PutMapping("/link-applies/{id}/reject")
    public Result<Void> rejectLinkApply(@PathVariable Integer id, @RequestBody RejectRequest request) {
        requireApply(id);
        linkApplyMapper.update(null, new UpdateWrapper<LinkApply>()
                .eq("id", id)
                .set("status", "2")
                .set("remark", Texts.cut(request == null ? "" : request.remark(), 200)));
        return Result.ok();
    }

    /**
     * 友链列表。
     *
     * @return 友链列表
     */
    @Operation(summary = "友链列表")
    @GetMapping("/links")
    public Result<List<Link>> links() {
        return Result.ok(contentService.links());
    }

    /**
     * 保存友链（新增/更新）。
     *
     * @param link 友链实体
     * @return 成功响应
     */
    @Operation(summary = "保存友链")
    @PostMapping("/links")
    public Result<Void> saveLink(@RequestBody Link link) {
        contentService.saveLink(link);
        return Result.ok();
    }

    /**
     * 删除友链。
     *
     * @param id 友链 ID
     * @return 成功响应
     */
    @Operation(summary = "删除友链")
    @DeleteMapping("/links/{id}")
    public Result<Void> deleteLink(@PathVariable Integer id) {
        contentService.deleteLink(id);
        return Result.ok();
    }

    /**
     * 日记列表。
     *
     * @return 日记列表
     */
    @Operation(summary = "日记列表")
    @GetMapping("/diaries")
    public Result<List<Diary>> diaries() {
        return Result.ok(contentService.diaries());
    }

    /**
     * 保存日记。
     *
     * @param diary 日记实体
     * @return 成功响应
     */
    @Operation(summary = "保存日记")
    @PostMapping("/diaries")
    public Result<Void> saveDiary(@RequestBody Diary diary) {
        contentService.saveDiary(diary);
        return Result.ok();
    }

    /**
     * 删除日记。
     *
     * @param id 日记 ID
     * @return 成功响应
     */
    @Operation(summary = "删除日记")
    @DeleteMapping("/diaries/{id}")
    public Result<Void> deleteDiary(@PathVariable Integer id) {
        contentService.deleteDiary(id);
        return Result.ok();
    }

    /**
     * 公告列表。
     *
     * @return 公告列表
     */
    @Operation(summary = "公告列表")
    @GetMapping("/notices")
    public Result<List<Notice>> notices() {
        return Result.ok(contentService.notices());
    }

    /**
     * 保存公告。
     *
     * @param notice 公告实体
     * @return 成功响应
     */
    @Operation(summary = "保存公告")
    @PostMapping("/notices")
    public Result<Void> saveNotice(@RequestBody Notice notice) {
        contentService.saveNotice(notice);
        return Result.ok();
    }

    /**
     * 删除公告。
     *
     * @param id 公告 ID
     * @return 成功响应
     */
    @Operation(summary = "删除公告")
    @DeleteMapping("/notices/{id}")
    public Result<Void> deleteNotice(@PathVariable Integer id) {
        contentService.deleteNotice(id);
        return Result.ok();
    }

    /**
     * 作品集列表（含隐藏）。
     *
     * @return 作品列表
     */
    @Operation(summary = "作品集列表")
    @GetMapping("/projects")
    public Result<List<Project>> projects() {
        return Result.ok(projectMapper.selectList(new QueryWrapper<Project>()
                .orderByAsc("sort").orderByDesc("id")));
    }

    /**
     * 保存作品（新增/更新，自动维护时间戳）。
     *
     * @param project 作品实体
     * @return 成功响应
     */
    @Operation(summary = "保存作品")
    @PostMapping("/projects")
    public Result<Void> saveProject(@RequestBody Project project) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (project.getName() == null || project.getName().isBlank()) {
            throw new BizException("请填写项目名");
        }
        if (project.getSummary() == null || project.getSummary().isBlank()) {
            throw new BizException("请填写一句话简介");
        }
        project.setName(Texts.cut(project.getName(), 64));
        project.setSummary(Texts.cut(project.getSummary(), 200));
        project.setTechStack(Texts.cut(project.getTechStack(), 255));
        project.setRepoUrl(Texts.cut(project.getRepoUrl(), 255));
        project.setDemoUrl(Texts.cut(project.getDemoUrl(), 255));
        project.setFeatured("1".equals(project.getFeatured()) ? "1" : "0");
        project.setStatus("0".equals(project.getStatus()) ? "0" : "1");
        if (project.getSort() == null) {
            project.setSort(0);
        }
        if (project.getId() == null) {
            project.setCreateTime(now);
            projectMapper.insert(project);
        } else {
            project.setUpdateTime(now);
            projectMapper.updateById(project);
        }
        return Result.ok();
    }

    /**
     * 删除作品。
     *
     * @param id 作品 ID
     * @return 成功响应
     */
    @Operation(summary = "删除作品")
    @DeleteMapping("/projects/{id}")
    public Result<Void> deleteProject(@PathVariable Integer id) {
        projectMapper.deleteById(id);
        return Result.ok();
    }

    /**
     * 用户分页。
     *
     * @param page    页码
     * @param size    条数
     * @param keyword 用户名/昵称搜索
     * @return 分页用户（脱敏）
     */
    @Operation(summary = "用户列表")
    @GetMapping("/users")
    public Result<PageResult<User>> users(@RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "10") long size,
                                          @RequestParam(required = false) String keyword) {
        Page<User> result = userService.page(page, Math.min(Math.max(size, 1), 50), keyword);
        result.getRecords().forEach(user -> user.setPassword(null));
        return Result.ok(PageResult.of(result));
    }

    /**
     * 锁定/解锁用户。
     *
     * @param uid     用户 ID
     * @param request 目标状态
     * @return 成功响应
     */
    @Operation(summary = "锁定/解锁用户")
    @PutMapping("/users/{uid}/state")
    public Result<Void> changeUserState(@PathVariable Integer uid, @RequestBody UserValueRequest request) {
        userService.changeState(uid, request.value(), currentUser().uid());
        if ("0".equals(request.value())) {
            // 锁定即吊销其全部 refresh 会话
            jwtService.revokeAll(uid);
        }
        return Result.ok();
    }

    /**
     * 修改用户角色。
     *
     * @param uid     用户 ID
     * @param request 目标角色
     * @return 成功响应
     */
    @Operation(summary = "修改用户角色")
    @PutMapping("/users/{uid}/role")
    public Result<Void> changeUserRole(@PathVariable Integer uid, @RequestBody UserValueRequest request) {
        userService.changeRole(uid, request.value(), currentUser().uid());
        return Result.ok();
    }

    /**
     * 管理员重置用户密码（替代「用户名+手机号」找回）。
     *
     * @param uid     用户 ID
     * @param request 新密码
     * @return 成功响应
     */
    @Operation(summary = "重置用户密码")
    @PutMapping("/users/{uid}/password")
    public Result<Void> resetUserPassword(@PathVariable Integer uid, @RequestBody UserValueRequest request) {
        userService.resetPasswordByAdmin(uid, request.value());
        return Result.ok();
    }

    /**
     * 登录日志分页。
     *
     * @param page 页码
     * @param size 条数
     * @return 分页日志
     */
    @Operation(summary = "登录日志")
    @GetMapping("/logs")
    public Result<PageResult<com.blog.entity.LoginLog>> logs(@RequestParam(defaultValue = "1") long page,
                                                             @RequestParam(defaultValue = "20") long size) {
        return Result.ok(PageResult.of(userService.logs(page, Math.min(Math.max(size, 1), 100))));
    }

    /**
     * 站点设置读取。
     *
     * @return 全部设置
     */
    @Operation(summary = "站点设置读取")
    @GetMapping("/settings")
    public Result<Map<String, String>> settings() {
        return Result.ok(settingService.all());
    }

    /**
     * 站点设置保存（写库并即时失效缓存）。
     *
     * @param form 键值对
     * @return 成功响应
     */
    @Operation(summary = "站点设置保存")
    @PutMapping("/settings")
    public Result<Void> saveSettings(@RequestBody SettingsForm form) {
        if (form == null || form.values() == null || form.values().isEmpty()) {
            throw new BizException("没有需要保存的设置");
        }
        settingService.save(form.values());
        return Result.ok();
    }

    /**
     * 表单转文章实体（校验由 ArticleService.save 完成）。
     *
     * @param form 表单
     * @return 文章实体
     */
    private Article toArticle(ArticleForm form) {
        Article article = new Article();
        article.setTitle(form.title());
        article.setDigest(form.digest());
        article.setContent(form.content());
        article.setCid(form.cid());
        article.setTagNames(form.tagNames());
        article.setPublishStatus(form.publishStatus());
        article.setIsHot(form.isHot());
        article.setIsTop(form.isTop());
        article.setLogo(form.logo());
        article.setSeoTitle(form.seoTitle());
        article.setSeoKeywords(form.seoKeywords());
        article.setSeoDesc(form.seoDesc());
        return article;
    }

    /**
     * 发布状态的文章触发 AI 摘要异步生成。
     *
     * @param article 已保存的文章实体
     */
    private void triggerAi(Article article) {
        if ("1".equals(article.getPublishStatus())) {
            articleAiService.generateSummary(article.getAid());
        }
    }

    /**
     * 获取友链申请（不存在时报业务异常）。
     *
     * @param id 申请 ID
     * @return 申请实体
     */
    private LinkApply requireApply(Integer id) {
        LinkApply apply = linkApplyMapper.selectById(id);
        if (apply == null) {
            throw new BizException("友链申请不存在");
        }
        return apply;
    }

    /**
     * 当前登录管理员主体。
     *
     * @return 登录主体
     */
    private com.blog.security.LoginUser currentUser() {
        com.blog.security.LoginUser user = Auths.currentUser();
        if (user == null) {
            throw new com.blog.common.BizException(com.blog.common.ResultCode.UNAUTHORIZED.code(), "未登录");
        }
        return user;
    }

    /**
     * 当前管理员的用户实体（文章落库需要作者外键）。
     *
     * @return 用户实体
     */
    private User author() {
        User user = userMapper.selectById(currentUser().uid());
        if (user == null) {
            throw new com.blog.common.BizException(com.blog.common.ResultCode.UNAUTHORIZED.code(), "账号不存在");
        }
        return user;
    }
}
