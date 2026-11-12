package com.blog.web.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.BizException;
import com.blog.common.Ip;
import com.blog.common.PageResult;
import com.blog.common.Result;
import com.blog.common.Texts;
import com.blog.entity.Article;
import com.blog.entity.Comment;
import com.blog.entity.Message;
import com.blog.entity.User;
import com.blog.mapper.LinkApplyMapper;
import com.blog.entity.LinkApply;
import com.blog.mapper.UserMapper;
import com.blog.security.Auths;
import com.blog.security.LoginUser;
import com.blog.service.ArticleService;
import com.blog.service.ContentService;
import com.blog.service.RateLimiterService;
import com.blog.service.SettingService;
import com.blog.service.UserService;
import com.blog.service.ViewCounterService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 互动 API：浏览量上报、点赞、统计、评论、留言板、友链申请。匿名可用但全部限流。
 */
@Tag(name = "interaction", description = "互动")
@RestController
@RequestMapping("/api/v1")
public class InteractionApiController {

    /** 点赞去重 Redis key 前缀（同一浏览器会话每文章仅一次，长期有效） */
    private static final String LIKED_KEY = "blog:liked:";

    private final ArticleService articleService;
    private final ContentService contentService;
    private final SettingService settingService;
    private final UserService userService;
    private final UserMapper userMapper;
    private final ViewCounterService viewCounterService;
    private final RateLimiterService rateLimiter;
    private final LinkApplyMapper linkApplyMapper;
    private final StringRedisTemplate redis;
    private final Ip ip;

    /**
     * 构造互动控制器。
     *
     * @param articleService     文章服务
     * @param contentService     内容服务
     * @param settingService     站点设置服务
     * @param userService        用户服务
     * @param userMapper         用户 Mapper（匿名身份解析）
     * @param viewCounterService 浏览量计数服务
     * @param rateLimiter        限流服务
     * @param linkApplyMapper    友链申请 Mapper
     * @param redis              Redis 客户端（点赞去重）
     * @param ip                 客户端 IP 提取
     */
    public InteractionApiController(ArticleService articleService, ContentService contentService,
                                    SettingService settingService, UserService userService, UserMapper userMapper,
                                    ViewCounterService viewCounterService, RateLimiterService rateLimiter,
                                    LinkApplyMapper linkApplyMapper, StringRedisTemplate redis, Ip ip) {
        this.articleService = articleService;
        this.contentService = contentService;
        this.settingService = settingService;
        this.userService = userService;
        this.userMapper = userMapper;
        this.viewCounterService = viewCounterService;
        this.rateLimiter = rateLimiter;
        this.linkApplyMapper = linkApplyMapper;
        this.redis = redis;
        this.ip = ip;
    }

    /**
     * 浏览上报请求体。
     *
     * @param sessionId 浏览器会话标识（前端生成并本地持久化）
     */
    public record ViewRequest(@NotBlank @Size(max = 64) String sessionId) {
    }

    /**
     * 评论请求体。
     *
     * @param nickname 昵称（匿名时必填）
     * @param content  评论内容
     */
    public record CommentRequest(@Size(max = 32) String nickname, @NotBlank @Size(max = 1000) String content) {
    }

    /**
     * 留言请求体。
     *
     * @param nickname 昵称（匿名时必填）
     * @param content  留言内容
     * @param parentId 回复目标（楼中楼，可选）
     * @param captchaId 验证码 ID（匿名且开关开启时必填）
     * @param captchaCode 验证码输入
     */
    public record MessageRequest(@Size(max = 32) String nickname, @NotBlank @Size(max = 1000) String content,
                                 Integer parentId, String captchaId, String captchaCode) {
    }

    /**
     * 友链申请请求体。
     *
     * @param siteName 站点名
     * @param siteUrl  站点地址
     * @param siteDesc 站点描述
     * @param contact  联系方式（选填）
     */
    public record LinkApplyRequest(@NotBlank @Size(max = 30) String siteName,
                                   @NotBlank @Size(max = 120) String siteUrl,
                                   @NotBlank @Size(max = 100) String siteDesc,
                                   @Size(max = 100) String contact) {
    }

    /**
     * 上报浏览量：会话去重（6 小时内同一会话只计一次），写入 Redis 缓冲。
     *
     * @param id      文章 ID
     * @param request 会话标识
     * @param httpRequest 原始请求
     * @return counted 表示本次是否计入
     */
    @Operation(summary = "上报浏览")
    @PostMapping("/articles/{id}/view")
    public Result<Map<String, Object>> view(@PathVariable Integer id, @RequestBody ViewRequest request,
                                            HttpServletRequest httpRequest) {
        rateLimiter.check("view", ip.of(httpRequest), 120, java.time.Duration.ofMinutes(1));
        boolean counted = viewCounterService.record(id, request.sessionId());
        return Result.ok(Map.of("counted", counted));
    }

    /**
     * 点赞：同一浏览器会话对同一文章仅生效一次，返回最新点赞数。
     *
     * @param id      文章 ID
     * @param request 会话标识
     * @param httpRequest 原始请求
     * @return 最新点赞数与是否首次点赞
     */
    @Operation(summary = "点赞")
    @PostMapping("/articles/{id}/like")
    public Result<Map<String, Object>> like(@PathVariable Integer id, @RequestBody ViewRequest request,
                                            HttpServletRequest httpRequest) {
        rateLimiter.check("like", ip.of(httpRequest), 60, java.time.Duration.ofMinutes(1));
        String likedKey = LIKED_KEY + id + ":" + Texts.trim(request.sessionId());
        Boolean first = redis.opsForValue().setIfAbsent(likedKey, "1");
        if (!Boolean.TRUE.equals(first)) {
            long total = articleService.thumbsUp(id);
            return Result.ok(Map.of("likes", total, "first", false));
        }
        long total = articleService.like(id);
        return Result.ok(Map.of("likes", total, "first", true));
    }

    /**
     * 批量查询文章动态数据（浏览/点赞），供列表页徽标使用。
     *
     * @param slugs 逗号分隔的文章 ID
     * @return id -> {views, likes}
     */
    @Operation(summary = "文章动态统计")
    @GetMapping("/articles/stats")
    public Result<Map<String, Map<String, Long>>> stats(@RequestParam String slugs) {
        Map<String, Map<String, Long>> data = new HashMap<String, Map<String, Long>>();
        for (String part : Texts.trim(slugs).split(",")) {
            if (Texts.blank(part)) {
                continue;
            }
            try {
                Integer aid = Integer.valueOf(part.trim());
                Article article = articleService.get(aid);
                long views = (article.getVisitCount() == null ? 0 : article.getVisitCount())
                        + viewCounterService.buffered(aid);
                data.put(String.valueOf(aid), Map.of(
                        "views", views,
                        "likes", article.getThumbsUp() == null ? 0L : article.getThumbsUp()));
            } catch (com.blog.common.BizException ignored) {
                // 已删除或不存在的文章跳过，不影响整批查询
            } catch (NumberFormatException ignored) {
                // 忽略非法片段，不中断整批查询
            }
        }
        return Result.ok(data);
    }

    /**
     * 发表评论：登录用户绑定账号身份，匿名需昵称；先审后发开关生效。
     *
     * @param aid     文章 ID（query）
     * @param request 评论请求
     * @param httpRequest 原始请求
     * @return 提交结果提示
     */
    @Operation(summary = "发表评论")
    @PostMapping("/comments")
    public Result<Map<String, Object>> comment(@RequestParam Integer aid, @Valid @RequestBody CommentRequest request,
                                               HttpServletRequest httpRequest) {
        if (!settingService.enabled("allow_comment", true)) {
            throw new BizException("评论已关闭");
        }
        rateLimiter.check("comment", ip.of(httpRequest), 10, java.time.Duration.ofMinutes(1));
        LoginUser principal = Auths.currentUser();
        User user = principal == null ? null : userMapper.selectById(principal.uid());
        boolean audit = settingService.enabled("comment_audit", false);
        contentService.addComment(aid, user, request.nickname(), request.content(), audit);
        return Result.ok(Map.of("audited", audit,
                "message", audit ? "评论已提交，等待审核" : "评论已发布"));
    }

    /**
     * 留言板发布留言（楼中楼）。匿名走验证码与 30 秒限流；管理员留言可选直接过审。
     *
     * @param request 留言请求
     * @param httpRequest 原始请求
     * @return 提交结果提示
     */
    @Operation(summary = "发布留言")
    @PostMapping("/messages")
    public Result<Map<String, Object>> message(@Valid @RequestBody MessageRequest request,
                                               HttpServletRequest httpRequest) {
        if (!settingService.enabled("allow_comment", true)) {
            throw new BizException("留言已关闭");
        }
        String clientIp = ip.of(httpRequest);
        rateLimiter.check("message", clientIp, 1, java.time.Duration.ofSeconds(30));
        LoginUser principal = Auths.currentUser();
        User user = principal == null ? null : userMapper.selectById(principal.uid());
        if (user == null && settingService.enabled("captcha_enabled", false)) {
            // 匿名留言校验 Redis 验证码
            verifyMessageCaptcha(request.captchaId(), request.captchaCode());
        }
        boolean skipAudit = user != null && user.getRole() != null && "0".equals(user.getRole())
                && settingService.enabled("admin_message_pass", true);
        contentService.addMessage(user, request.nickname(), request.content(), clientIp,
                request.parentId(), skipAudit);
        return Result.ok(Map.of("audited", !skipAudit,
                "message", skipAudit ? "留言已发布" : "留言提交成功，等待管理员审核"));
    }

    /**
     * 留言板分页（登录用户可见自己的待审留言）。
     *
     * @param page 页码
     * @return 分页留言（含楼中楼）
     */
    @Operation(summary = "留言板分页")
    @GetMapping("/messages")
    public Result<PageResult<Message>> messages(@RequestParam(defaultValue = "1") long page) {
        LoginUser principal = Auths.currentUser();
        User user = principal == null ? null : userMapper.selectById(principal.uid());
        Page<Message> result = contentService.frontMessages(page, user);
        return Result.ok(PageResult.of(result));
    }

    /**
     * 删除留言（本人或管理员）。
     *
     * @param id 留言 ID
     * @return 成功响应
     */
    @Operation(summary = "删除留言")
    @DeleteMapping("/messages/{id}")
    public Result<Void> deleteMessage(@PathVariable Integer id) {
        LoginUser principal = Auths.currentUser();
        User user = principal == null ? null : userMapper.selectById(principal.uid());
        contentService.deleteOwnMessage(id, user);
        return Result.ok();
    }

    /**
     * 提交友链申请（IP 每小时一条），进入待审池。
     *
     * @param request 申请内容
     * @param httpRequest 原始请求
     * @return 成功响应
     */
    @Operation(summary = "友链申请")
    @PostMapping("/link-applies")
    public Result<Void> linkApply(@Valid @RequestBody LinkApplyRequest request, HttpServletRequest httpRequest) {
        String clientIp = ip.of(httpRequest);
        rateLimiter.check("link-apply", clientIp, 1, java.time.Duration.ofHours(1));
        String url = Texts.trim(request.siteUrl());
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new BizException("站点地址需要以 http:// 或 https:// 开头");
        }
        LinkApply apply = new LinkApply();
        apply.setSiteName(Texts.cut(request.siteName(), 30));
        apply.setSiteUrl(Texts.cut(url, 120));
        apply.setSiteDesc(Texts.cut(request.siteDesc(), 100));
        apply.setContact(Texts.cut(request.contact(), 100));
        apply.setStatus("0");
        apply.setIp(clientIp);
        linkApplyMapper.insert(apply);
        return Result.ok();
    }

    /**
     * 校验匿名留言验证码（Redis 一次性）。
     *
     * @param captchaId   验证码 ID
     * @param captchaCode 输入
     */
    private void verifyMessageCaptcha(String captchaId, String captchaCode) {
        if (Texts.blank(captchaId) || Texts.blank(captchaCode)) {
            throw new BizException("请输入验证码");
        }
        String key = "blog:captcha:" + Texts.trim(captchaId);
        String saved = redis.opsForValue().get(key);
        redis.delete(key);
        if (saved == null || !saved.equalsIgnoreCase(Texts.trim(captchaCode))) {
            throw new BizException("验证码错误或已过期");
        }
    }
}
