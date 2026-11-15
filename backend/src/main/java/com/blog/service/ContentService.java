package com.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.BizException;
import com.blog.common.Fmt;
import com.blog.common.MessageText;
import com.blog.common.Texts;
import com.blog.entity.Article;
import com.blog.entity.Category;
import com.blog.entity.Comment;
import com.blog.entity.Diary;
import com.blog.entity.Link;
import com.blog.entity.Message;
import com.blog.entity.Notice;
import com.blog.entity.Tag;
import com.blog.entity.User;
import com.blog.mapper.ArticleMapper;
import com.blog.mapper.CategoryMapper;
import com.blog.mapper.CommentMapper;
import com.blog.mapper.DiaryMapper;
import com.blog.mapper.LinkMapper;
import com.blog.mapper.MessageMapper;
import com.blog.mapper.NoticeMapper;
import com.blog.mapper.TagMapper;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ContentService {

    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final LinkMapper linkMapper;
    private final CommentMapper commentMapper;
    private final MessageMapper messageMapper;
    private final DiaryMapper diaryMapper;
    private final NoticeMapper noticeMapper;
    private final ArticleMapper articleMapper;
    private final com.blog.mapper.ArticleTagMapper articleTagMapper;
    private final ArticleService articleService;
    private final Fmt fmt;

    public ContentService(CategoryMapper categoryMapper, TagMapper tagMapper, LinkMapper linkMapper,
                          CommentMapper commentMapper, MessageMapper messageMapper, DiaryMapper diaryMapper,
                          NoticeMapper noticeMapper, ArticleMapper articleMapper,
                          com.blog.mapper.ArticleTagMapper articleTagMapper, ArticleService articleService, Fmt fmt) {
        this.categoryMapper = categoryMapper;
        this.tagMapper = tagMapper;
        this.linkMapper = linkMapper;
        this.commentMapper = commentMapper;
        this.messageMapper = messageMapper;
        this.diaryMapper = diaryMapper;
        this.noticeMapper = noticeMapper;
        this.articleMapper = articleMapper;
        this.articleTagMapper = articleTagMapper;
        this.articleService = articleService;
        this.fmt = fmt;
    }

    public List<Category> categories() {
        List<Category> categories = categoryMapper.selectList(new QueryWrapper<Category>().orderByAsc("cid"));
        Map<Integer, Long> counts = articleService.countByCategory();
        for (Category category : categories) {
            Long count = counts.get(category.getCid());
            category.setArticleCount(count == null ? 0L : count);
        }
        return categories;
    }

    public List<Tag> tags() {
        return tagMapper.selectList(new QueryWrapper<Tag>().orderByAsc("cid").orderByAsc("tid"));
    }

    public Map<Integer, Integer> countArticlesByTag(List<Tag> tags) {
        Map<Integer, Integer> counts = new LinkedHashMap<Integer, Integer>();
        if (tags == null) {
            return counts;
        }
        // 聚合下推 SQL（基于 V3 关联表），替代旧的全表载入内存统计
        Map<Integer, Long> sqlCounts = new LinkedHashMap<Integer, Long>();
        for (Map<String, Object> row : articleService.countByTagId()) {
            Object tagId = row.get("tagId");
            Object cnt = row.get("cnt");
            if (tagId instanceof Number id && cnt instanceof Number count) {
                sqlCounts.put(id.intValue(), count.longValue());
            }
        }
        for (Tag tag : tags) {
            if (tag.getTid() == null) {
                continue;
            }
            Long count = sqlCounts.get(tag.getTid());
            counts.put(tag.getTid(), count == null ? 0 : count.intValue());
        }
        return counts;
    }

    public void saveCategory(String name) {
        String cname = Texts.trim(name);
        if (cname.isEmpty() || cname.length() > 30) {
            throw new BizException("栏目名称需要 1 到 30 个字符");
        }
        Long exists = categoryMapper.selectCount(new QueryWrapper<Category>().eq("cname", cname));
        if (exists != null && exists > 0) {
            throw new BizException("栏目已存在");
        }
        Category category = new Category();
        category.setCname(cname);
        categoryMapper.insert(category);
    }

    /**
     * 栏目重命名（V1.1 第二批补齐的能力）。
     *
     * @param cid  栏目 ID
     * @param name 新名称
     */
    public void renameCategory(Integer cid, String name) {
        Category category = categoryMapper.selectById(cid);
        if (category == null) {
            throw new BizException("栏目不存在");
        }
        String cname = Texts.trim(name);
        if (cname.isEmpty() || cname.length() > 30) {
            throw new BizException("栏目名称需要 1 到 30 个字符");
        }
        Long exists = categoryMapper.selectCount(new QueryWrapper<Category>().eq("cname", cname).ne("cid", cid));
        if (exists != null && exists > 0) {
            throw new BizException("栏目已存在");
        }
        categoryMapper.update(null, new UpdateWrapper<Category>().eq("cid", cid).set("cname", cname));
    }

    public void deleteCategory(Integer cid) {
        Long articles = articleMapper.selectCount(new QueryWrapper<Article>().eq("cid", cid));
        if (articles != null && articles > 0) {
            throw new BizException("栏目下还有文章，不能删除");
        }
        tagMapper.delete(new QueryWrapper<Tag>().eq("cid", cid));
        categoryMapper.deleteById(cid);
    }

    public void saveTag(Integer cid, String name) {
        if (cid == null || categoryMapper.selectById(cid) == null) {
            throw new BizException("请选择栏目");
        }
        String tname = Texts.trim(name);
        if (tname.isEmpty() || tname.length() > 30) {
            throw new BizException("标签名称需要 1 到 30 个字符");
        }
        Tag tag = new Tag();
        tag.setCid(cid);
        tag.setTname(tname);
        tagMapper.insert(tag);
    }

    /**
     * 编辑标签（改名或换栏目，V1.1 第二批补齐的能力）。
     *
     * @param tid  标签 ID
     * @param name 新名称（可空表示不变）
     * @param cid  新栏目（可空表示不变）
     */
    public void updateTag(Integer tid, String name, Integer cid) {
        Tag tag = tagMapper.selectById(tid);
        if (tag == null) {
            throw new BizException("标签不存在");
        }
        UpdateWrapper<Tag> wrapper = new UpdateWrapper<Tag>().eq("tid", tid);
        if (!Texts.blank(name)) {
            String tname = Texts.trim(name);
            if (tname.isEmpty() || tname.length() > 30) {
                throw new BizException("标签名称需要 1 到 30 个字符");
            }
            wrapper.set("tname", tname);
        }
        if (cid != null) {
            if (categoryMapper.selectById(cid) == null) {
                throw new BizException("栏目不存在");
            }
            wrapper.set("cid", cid);
        }
        tagMapper.update(null, wrapper);
    }

    public void deleteTag(Integer tid) {
        // 关联表同步清理，避免残留孤儿关联
        articleTagMapper.delete(new QueryWrapper<com.blog.entity.ArticleTag>().eq("tag_id", tid));
        tagMapper.deleteById(tid);
    }

    public List<Link> links() {
        return linkMapper.selectList(new QueryWrapper<Link>().orderByAsc("sort").orderByAsc("id"));
    }

    public void saveLink(Link link) {
        String name = Texts.trim(link.getSiteName());
        String url = Texts.trim(link.getSiteUrl());
        String desc = Texts.trim(link.getSiteDesc());
        if (name.isEmpty() || name.length() > 30) {
            throw new BizException("站点名不超过 30 个字符");
        }
        if (url.isEmpty() || url.length() > 200) {
            throw new BizException("站点地址不超过 200 个字符");
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new BizException("站点地址需要以 http:// 或 https:// 开头");
        }
        if (desc.isEmpty() || desc.length() > 100) {
            throw new BizException("站点描述不超过 100 个字符");
        }
        link.setSiteName(name);
        link.setSiteUrl(url);
        link.setSiteDesc(desc);
        if (link.getSort() == null) {
            link.setSort(0);
        }
        if (link.getId() == null) {
            linkMapper.insert(link);
        } else {
            linkMapper.updateById(link);
        }
    }

    public void deleteLink(Integer id) {
        linkMapper.deleteById(id);
    }

    public List<Comment> commentsOf(Integer aid) {
        return commentMapper.selectList(new QueryWrapper<Comment>()
                .eq("aid", aid)
                .eq("status", "1")
                .orderByAsc("id"));
    }

    public void addComment(Integer aid, User user, String nickname, String content, boolean audit) {
        Article article = articleMapper.selectById(aid);
        if (article == null || !"1".equals(article.getIsOpen())) {
            throw new BizException("文章不存在");
        }
        String text = Texts.trim(content);
        if (text.isEmpty() || text.length() > 1000) {
            throw new BizException("评论需要 1 到 1000 个字符");
        }
        String name = user != null && !Texts.blank(user.getNickname()) ? user.getNickname() : Texts.trim(nickname);
        if (user != null && Texts.blank(name)) {
            name = user.getUsername();
        }
        if (name.isEmpty() || name.length() > 32) {
            throw new BizException("请填写昵称");
        }
        Comment comment = new Comment();
        comment.setAid(aid);
        comment.setUid(user == null ? null : user.getUid());
        comment.setNickname(name);
        comment.setContent(text);
        comment.setCreateTime(Texts.now());
        comment.setStatus(audit ? "0" : "1");
        commentMapper.insert(comment);
        if (!audit) {
            articleService.markCommented(aid);
        }
    }

    public Page<Comment> pageComments(long page, long size, String status) {
        QueryWrapper<Comment> wrapper = new QueryWrapper<Comment>().orderByDesc("id");
        if (!Texts.blank(status)) {
            wrapper.eq("status", status);
        }
        Page<Comment> result = commentMapper.selectPage(new Page<Comment>(page, size), wrapper);
        // 批量补文章标题，消除逐条 selectById 的 N+1
        List<Integer> aids = new ArrayList<Integer>();
        for (Comment comment : result.getRecords()) {
            if (comment.getAid() != null) {
                aids.add(comment.getAid());
            }
        }
        Map<Integer, String> titles = new LinkedHashMap<Integer, String>();
        if (!aids.isEmpty()) {
            for (Article article : articleMapper.selectBatchIds(aids)) {
                titles.put(article.getAid(), article.getTitle());
            }
        }
        for (Comment comment : result.getRecords()) {
            comment.setArticleTitle(titles.getOrDefault(comment.getAid(), "文章已删除"));
        }
        return result;
    }

    public void updateCommentStatus(Integer id, String status) {
        Comment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new BizException("评论不存在");
        }
        commentMapper.update(null, new UpdateWrapper<Comment>().eq("id", id).set("status", "1".equals(status) ? "1" : "0"));
        if ("1".equals(status)) {
            articleService.markCommented(comment.getAid());
        }
    }

    public void deleteComment(Integer id) {
        commentMapper.deleteById(id);
    }

    public long commentCount() {
        Long count = commentMapper.selectCount(null);
        return count == null ? 0 : count;
    }

    public Page<Message> frontMessages(long page, User viewer) {
        long current = page < 1 ? 1 : page;
        Page<Message> result = messageMapper.selectPage(new Page<Message>(current, 8), visible(viewer)
                .isNull("parent_id")
                .orderByDesc("id"));
        if (result.getPages() > 0 && current > result.getPages()) {
            return frontMessages(result.getPages(), viewer);
        }
        attachReplies(result.getRecords(), viewer);
        return result;
    }

    public void addMessage(User user, String nickname, String content, String ip, Integer parentId, boolean skipAudit) {
        String name = user != null
                ? MessageText.nickname(!Texts.blank(user.getNickname()) ? user.getNickname() : user.getUsername())
                : MessageText.nickname(nickname);
        String text = MessageText.content(content);
        String clientIp = Texts.cut(ip, 64);
        if (!clientIp.isEmpty()) {
            String since = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(System.currentTimeMillis() - 30000));
            Long recent = messageMapper.selectCount(new QueryWrapper<Message>().eq("ip", clientIp).ge("create_time", since));
            if (recent != null && recent > 0) {
                throw new BizException("提交太频繁，请稍后再试");
            }
        }
        Message message = new Message();
        message.setNickname(name);
        message.setContent(text);
        message.setCreateTime(Texts.now());
        message.setStatus(skipAudit ? "1" : "0");
        message.setUid(user == null ? null : user.getUid());
        message.setIp(clientIp.isEmpty() ? null : clientIp);
        message.setParentId(resolveParent(parentId));
        messageMapper.insert(message);
    }

    public Page<Message> pageMessages(long page, long size, String status) {
        QueryWrapper<Message> query = new QueryWrapper<Message>().orderByDesc("id");
        if ("0".equals(status) || "1".equals(status) || "2".equals(status)) {
            query.eq("status", status);
        }
        return messageMapper.selectPage(new Page<Message>(page < 1 ? 1 : page, size), query);
    }

    public void updateMessageStatus(Integer id, String status) {
        if (messageMapper.selectById(id) == null) {
            throw new BizException("留言不存在");
        }
        String next = "1".equals(status) ? "1" : "2";
        messageMapper.update(null, new UpdateWrapper<Message>().eq("id", id).set("status", next));
    }

    public void replyMessage(Integer id, String reply) {
        messageMapper.update(null, new UpdateWrapper<Message>()
                .eq("id", id)
                .set("reply", Texts.trim(reply))
                .set("status", "1"));
    }

    public void deleteMessage(Integer id) {
        messageMapper.delete(new QueryWrapper<Message>().eq("parent_id", id));
        messageMapper.deleteById(id);
    }

    public void deleteOwnMessage(Integer id, User actor) {
        Message message = messageMapper.selectById(id);
        if (message == null) {
            throw new BizException("留言不存在");
        }
        boolean owner = actor != null && message.getUid() != null && message.getUid().equals(actor.getUid());
        if (!UserService.isAdmin(actor) && !owner) {
            throw new BizException("只能删除自己的留言");
        }
        deleteMessage(id);
    }

    public long messageCount() {
        Long count = messageMapper.selectCount(new QueryWrapper<Message>().eq("status", "1").isNull("parent_id"));
        return count == null ? 0 : count;
    }

    public long pendingMessageCount() {
        Long count = messageMapper.selectCount(new QueryWrapper<Message>().eq("status", "0"));
        return count == null ? 0 : count;
    }

    private QueryWrapper<Message> visible(User viewer) {
        QueryWrapper<Message> query = new QueryWrapper<Message>();
        query.and(wrapper -> {
            wrapper.eq("status", "1");
            if (viewer != null && UserService.isAdmin(viewer)) {
                wrapper.or().eq("status", "0");
            } else if (viewer != null && viewer.getUid() != null) {
                wrapper.or(own -> own.eq("status", "0").eq("uid", viewer.getUid()));
            }
        });
        return query;
    }

    private void attachReplies(List<Message> parents, User viewer) {
        if (parents == null || parents.isEmpty()) {
            return;
        }
        List<Integer> ids = new ArrayList<Integer>();
        for (Message parent : parents) {
            ids.add(parent.getId());
            decorate(parent, viewer);
        }
        List<Message> replies = messageMapper.selectList(visible(viewer).in("parent_id", ids).orderByAsc("id"));
        Map<Integer, List<Message>> grouped = new LinkedHashMap<Integer, List<Message>>();
        for (Message reply : replies) {
            decorate(reply, viewer);
            List<Message> list = grouped.get(reply.getParentId());
            if (list == null) {
                list = new ArrayList<Message>();
                grouped.put(reply.getParentId(), list);
            }
            list.add(reply);
        }
        for (Message parent : parents) {
            List<Message> list = grouped.get(parent.getId());
            if (list != null) {
                parent.setReplies(list);
            }
        }
    }

    private void decorate(Message message, User viewer) {
        String name = message.getNickname() == null ? "友" : message.getNickname();
        message.setInitial(name.isEmpty() ? "友" : name.substring(0, 1));
        message.setAvatarColor(avatarColor(name));
        boolean owner = viewer != null && message.getUid() != null && message.getUid().equals(viewer.getUid());
        message.setCanDelete(UserService.isAdmin(viewer) || owner);
    }

    private Integer resolveParent(Integer parentId) {
        if (parentId == null) {
            return null;
        }
        Message parent = messageMapper.selectById(parentId);
        if (parent == null || "2".equals(parent.getStatus())) {
            throw new BizException("要回复的留言不存在");
        }
        return parent.getParentId() == null ? parent.getId() : parent.getParentId();
    }

    private String avatarColor(String name) {
        String[] colors = {"#6f8f86", "#8d7b68", "#6d7d8c", "#8a7568", "#6f7f72", "#7a6e86"};
        int hash = 0;
        for (int i = 0; i < name.length(); i++) {
            hash = hash * 31 + name.charAt(i);
        }
        return colors[(hash & 0x7fffffff) % colors.length];
    }

    public List<Diary> diaries() {
        return diaryMapper.selectList(new QueryWrapper<Diary>().orderByDesc("create_time").orderByDesc("id"));
    }

    public List<Diary> recentDiaries(int limit) {
        int size = limit < 1 ? 1 : Math.min(limit, 8);
        return diaryMapper.selectList(new QueryWrapper<Diary>()
                .orderByDesc("create_time")
                .orderByDesc("id")
                .last("limit " + size));
    }

    public void saveDiary(Diary diary) {
        if (Texts.blank(diary.getTitle()) || Texts.blank(diary.getContent())) {
            throw new BizException("请填写日记标题和内容");
        }
        diary.setTitle(Texts.cut(diary.getTitle(), 100));
        diary.setContent(Texts.cut(diary.getContent(), 2000));
        diary.setMood(Texts.cut(diary.getMood(), 20));
        if (diary.getId() == null) {
            diary.setCreateTime(Texts.now());
            diaryMapper.insert(diary);
        } else {
            diaryMapper.updateById(diary);
        }
    }

    public Diary getDiary(Integer id) {
        return diaryMapper.selectById(id);
    }

    public void deleteDiary(Integer id) {
        diaryMapper.deleteById(id);
    }

    public List<Notice> notices() {
        return noticeMapper.selectList(new QueryWrapper<Notice>().orderByDesc("is_top").orderByDesc("id"));
    }

    public List<Notice> topNotices(int limit) {
        // 按 title+content 去重：同一内容只返回一次，避免首页公告重复渲染
        List<Notice> all = noticeMapper.selectList(new QueryWrapper<Notice>()
                .orderByDesc("is_top")
                .orderByDesc("id"));
        List<Notice> result = new ArrayList<Notice>();
        Set<String> seen = new HashSet<String>();
        for (Notice notice : all) {
            if (!seen.add(notice.getTitle() + "|" + notice.getContent())) {
                continue;
            }
            result.add(notice);
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    public void saveNotice(Notice notice) {
        if (Texts.blank(notice.getTitle()) || Texts.blank(notice.getContent())) {
            throw new BizException("请填写公告标题和内容");
        }
        notice.setTitle(Texts.cut(notice.getTitle(), 100));
        notice.setContent(Texts.cut(notice.getContent(), 2000));
        notice.setIsTop("1".equals(notice.getIsTop()) ? "1" : "0");
        if (notice.getId() == null) {
            notice.setCreateTime(Texts.now());
            noticeMapper.insert(notice);
        } else {
            noticeMapper.updateById(notice);
        }
    }

    public Notice getNotice(Integer id) {
        return noticeMapper.selectById(id);
    }

    public void deleteNotice(Integer id) {
        noticeMapper.deleteById(id);
    }

    public long linkCount() {
        Long count = linkMapper.selectCount(null);
        return count == null ? 0 : count;
    }
}
