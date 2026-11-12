package com.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.BizException;
import com.blog.common.Fmt;
import com.blog.common.Texts;
import com.blog.entity.Article;
import com.blog.entity.Category;
import com.blog.entity.Tag;
import com.blog.entity.User;
import com.blog.mapper.ArticleMapper;
import com.blog.mapper.CategoryMapper;
import com.blog.mapper.TagMapper;
import com.blog.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ArticleService {

    private final ArticleMapper articleMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final UserMapper userMapper;
    private final SmartService smartService;
    private final com.blog.mapper.ArticleTagMapper articleTagMapper;
    private final Fmt fmt;

    /**
     * 构造文章服务。
     *
     * @param articleMapper    文章 Mapper
     * @param categoryMapper   栏目 Mapper
     * @param tagMapper        标签 Mapper
     * @param userMapper       用户 Mapper
     * @param smartService     Markdown 工具
     * @param articleTagMapper 文章-标签关联 Mapper
     * @param fmt              模板辅助组件
     */
    public ArticleService(ArticleMapper articleMapper, CategoryMapper categoryMapper, TagMapper tagMapper,
                          UserMapper userMapper, SmartService smartService,
                          com.blog.mapper.ArticleTagMapper articleTagMapper, Fmt fmt) {
        this.articleMapper = articleMapper;
        this.categoryMapper = categoryMapper;
        this.tagMapper = tagMapper;
        this.userMapper = userMapper;
        this.smartService = smartService;
        this.articleTagMapper = articleTagMapper;
        this.fmt = fmt;
    }

    public Page<Article> pagePublic(long page, long size, Integer cid, String q, String tag) {
        QueryWrapper<Article> wrapper = publicWrapper(cid, q, tag);
        Page<Article> result = articleMapper.selectPage(new Page<Article>(page, size), wrapper);
        fill(result.getRecords());
        return result;
    }

    public Page<Article> pageAdmin(long page, long size, String title, String publishStatus) {
        QueryWrapper<Article> wrapper = new QueryWrapper<Article>().orderByDesc("aid");
        if (!Texts.blank(title)) {
            wrapper.like("title", title.trim());
        }
        if (isStatus(publishStatus)) {
            wrapper.eq("publish_status", publishStatus);
        }
        Page<Article> result = articleMapper.selectPage(new Page<Article>(page, size), wrapper);
        fill(result.getRecords());
        return result;
    }

    public List<Article> latest(int limit) {
        QueryWrapper<Article> wrapper = new QueryWrapper<Article>().eq("isOpen", "1");
        orderPublic(wrapper);
        List<Article> list = articleMapper.selectList(wrapper.last("limit " + limit));
        fill(list);
        return list;
    }

    public List<Article> hot(int limit) {
        List<Article> list = articleMapper.selectList(new QueryWrapper<Article>()
                .eq("isOpen", "1")
                .orderByDesc("is_top")
                .orderByDesc("visit_count")
                .orderByDesc("aid")
                .last("limit " + limit));
        fill(list);
        return list;
    }

    public Article detail(Integer id, boolean allowPrivate) {
        Article article = articleMapper.selectById(id);
        if (article == null) {
            throw new BizException("文章不存在");
        }
        if (!"1".equals(article.getIsOpen()) && !allowPrivate) {
            throw new BizException("这篇文章还没有公开");
        }
        if ("1".equals(article.getIsOpen())) {
            articleMapper.update(null, new UpdateWrapper<Article>()
                    .eq("aid", id)
                    .setSql("visit_count = IFNULL(visit_count,0) + 1"));
            article.setVisitCount(article.getVisitCount() == null ? 1 : article.getVisitCount() + 1);
        }
        fill(Collections.singletonList(article));
        return article;
    }

    public Article get(Integer id) {
        Article article = articleMapper.selectById(id);
        if (article == null) {
            throw new BizException("文章不存在");
        }
        fill(Collections.singletonList(article));
        return article;
    }

    public List<Article> related(Article current, int limit) {
        List<Article> pool = articleMapper.selectList(new QueryWrapper<Article>()
                .eq("isOpen", "1")
                .ne("aid", current.getAid())
                .orderByDesc("visit_count")
                .last("limit 30"));
        final Set<String> tags = new HashSet<String>(fmt.tags(current.getTagNames()));
        Collections.sort(pool, new Comparator<Article>() {
            @Override
            public int compare(Article a, Article b) {
                int scoreA = score(a, current, tags);
                int scoreB = score(b, current, tags);
                if (scoreA != scoreB) {
                    return scoreB - scoreA;
                }
                int visitA = a.getVisitCount() == null ? 0 : a.getVisitCount();
                int visitB = b.getVisitCount() == null ? 0 : b.getVisitCount();
                return visitB - visitA;
            }
        });
        if (pool.size() > limit) {
            pool = new ArrayList<Article>(pool.subList(0, limit));
        }
        fill(pool);
        return pool;
    }

    /**
     * 保存文章（新建或更新）：状态机校验、标签规范化并同步关联表；返回文章 ID。
     *
     * @param form   表单载体（新建后回填 aid）
     * @param user   作者
     * @param logo   新封面地址（可空表示不变）
     * @return 文章 ID
     */
    public Integer save(Article form, User user, String logo) {
        if (Texts.blank(form.getTitle())) {
            throw new BizException("请填写标题");
        }
        if (Texts.blank(form.getContent())) {
            throw new BizException("请填写正文");
        }
        if (form.getCid() == null) {
            throw new BizException("请选择栏目");
        }
        if (categoryMapper.selectById(form.getCid()) == null) {
            throw new BizException("栏目不存在");
        }
        Article article = form.getAid() == null ? new Article() : articleMapper.selectById(form.getAid());
        if (form.getAid() != null && article == null) {
            throw new BizException("文章不存在");
        }
        article.setTitle(Texts.cut(form.getTitle(), 255));
        article.setContent(form.getContent());
        article.setCid(form.getCid());
        article.setTagNames(Texts.cut(normalizeTags(form.getTagNames()), 255));
        applyStatus(article, form.getPublishStatus());
        article.setIsHot(form.getIsHot() != null && form.getIsHot() == 1 ? 1 : 0);
        article.setIsTop(form.getIsTop() != null && form.getIsTop() == 1 ? 1 : 0);
        article.setSeoTitle(Texts.cut(form.getSeoTitle(), 255));
        article.setSeoKeywords(Texts.cut(form.getSeoKeywords(), 255));
        article.setSeoDesc(Texts.cut(form.getSeoDesc(), 500));
        if (!Texts.blank(logo)) {
            article.setLogo(logo);
        }
        if (Texts.blank(form.getDigest())) {
            article.setDigest(smartService.digest(form.getContent()));
        } else {
            article.setDigest(form.getDigest());
        }
        if (form.getAid() == null) {
            article.setUid(user.getUid());
            article.setVisitCount(0);
            article.setThumbsUp(0L);
            article.setIsCommented("0");
            article.setCreateTime(Texts.now());
            articleMapper.insert(article);
            form.setAid(article.getAid());
        } else {
            article.setUpdateTime(Texts.now());
            articleMapper.updateById(article);
        }
        // 标签关联表与冗余文本同步：按名字补齐 t_tag 行（挂在本文章栏目下），重建关联
        syncTagRelations(article);
        return article.getAid();
    }

    /**
     * 重建文章-标签关联：先按名字确保标签存在，再以关联表全量替换。
     *
     * @param article 已落库的文章
     */
    private void syncTagRelations(Article article) {
        articleTagMapper.delete(new QueryWrapper<com.blog.entity.ArticleTag>().eq("article_id", article.getAid()));
        for (String name : fmt.tags(article.getTagNames())) {
            Tag tag = tagMapper.selectOne(new QueryWrapper<Tag>().eq("tname", name).last("limit 1"));
            if (tag == null) {
                tag = new Tag();
                tag.setTname(name);
                tag.setCid(article.getCid());
                tagMapper.insert(tag);
            }
            com.blog.entity.ArticleTag relation = new com.blog.entity.ArticleTag();
            relation.setArticleId(article.getAid());
            relation.setTagId(tag.getTid());
            try {
                articleTagMapper.insert(relation);
            } catch (org.springframework.dao.DuplicateKeyException ignored) {
                // 同名标签命中多行时忽略重复关联
            }
        }
    }

    public void delete(Integer id) {
        if (articleMapper.deleteById(id) == 0) {
            throw new BizException("文章不存在");
        }
    }

    public void applyStatus(Article article, String publishStatus) {
        if (article == null) {
            throw new BizException("文章不存在");
        }
        if ("0".equals(publishStatus)) {
            article.setPublishStatus("0");
            article.setIsOpen("0");
        } else if ("1".equals(publishStatus)) {
            article.setPublishStatus("1");
            article.setIsOpen("1");
        } else if ("2".equals(publishStatus)) {
            article.setPublishStatus("2");
            article.setIsOpen("0");
        } else {
            throw new BizException("文章状态不正确");
        }
    }

    public String changeStatus(Integer id, String publishStatus) {
        if (!isStatus(publishStatus)) {
            throw new BizException("文章状态不正确");
        }
        Article article = articleMapper.selectById(id);
        if (article == null) {
            throw new BizException("文章不存在");
        }
        if (publishStatus.equals(article.getPublishStatus())) {
            throw new BizException("这篇文章已经是" + statusLabel(publishStatus));
        }
        applyStatus(article, publishStatus);
        int rows = articleMapper.update(null, new UpdateWrapper<Article>()
                .eq("aid", id)
                .set("publish_status", article.getPublishStatus())
                .set("isOpen", article.getIsOpen()));
        if (rows == 0) {
            throw new BizException("文章不存在");
        }
        return statusDone(publishStatus);
    }

    public Article previousPublished(Article current) {
        return adjacent(current, false);
    }

    public Article nextPublished(Article current) {
        return adjacent(current, true);
    }

    public void updateHot(Integer id, Integer isHot) {
        articleMapper.update(null, new UpdateWrapper<Article>()
                .eq("aid", id)
                .set("is_hot", isHot != null && isHot == 1 ? 1 : 0));
    }

    public long like(Integer id) {
        Article article = articleMapper.selectById(id);
        if (article == null || !"1".equals(article.getIsOpen())) {
            throw new BizException("文章不存在");
        }
        articleMapper.update(null, new UpdateWrapper<Article>()
                .eq("aid", id)
                .setSql("thumbsUp = IFNULL(thumbsUp,0) + 1"));
        Article fresh = articleMapper.selectById(id);
        return fresh.getThumbsUp() == null ? 0L : fresh.getThumbsUp();
    }

    public void markCommented(Integer aid) {
        articleMapper.update(null, new UpdateWrapper<Article>().eq("aid", aid).set("isCommented", "1"));
    }

    public long count() {
        Long count = articleMapper.selectCount(null);
        return count == null ? 0 : count;
    }

    public long publicCount() {
        Long count = articleMapper.selectCount(new QueryWrapper<Article>().eq("isOpen", "1"));
        return count == null ? 0 : count;
    }

    public long visits() {
        Long sum = articleMapper.sumVisits();
        return sum == null ? 0 : sum;
    }

    public Map<Integer, Long> countByCategory() {
        Map<Integer, Long> map = new HashMap<Integer, Long>();
        // 聚合下推到 SQL，替代旧的全表内存统计
        for (Map<String, Object> row : articleMapper.countByCategory()) {
            Object cid = row.get("cid");
            Object cnt = row.get("cnt");
            if (cid instanceof Number id && cnt instanceof Number count) {
                map.put(id.intValue(), count.longValue());
            }
        }
        return map;
    }

    /**
     * 公开文章按标签 ID 计数（SQL 聚合，供标签列表展示）。
     *
     * @return tagId -> 文章数
     */
    public List<Map<String, Object>> countByTagId() {
        return articleMapper.countByTag();
    }

    /**
     * 读取点赞数（不递增）。
     *
     * @param id 文章 ID
     * @return 点赞数；文章不存在返回 0
     */
    public long thumbsUp(Integer id) {
        Article article = articleMapper.selectById(id);
        return article == null || article.getThumbsUp() == null ? 0L : article.getThumbsUp();
    }

    private QueryWrapper<Article> publicWrapper(Integer cid, String q, String tag) {
        QueryWrapper<Article> wrapper = new QueryWrapper<Article>().eq("isOpen", "1");
        if (cid != null) {
            wrapper.eq("cid", cid);
        }
        if (!Texts.blank(tag)) {
            wrapper.like("tagNames", tag.trim());
        }
        if (!Texts.blank(q)) {
            String keyword = q.trim();
            wrapper.and(w -> w.like("title", keyword).or().like("digest", keyword).or().like("content", keyword));
        }
        orderPublic(wrapper);
        return wrapper;
    }

    private void orderPublic(QueryWrapper<Article> wrapper) {
        wrapper.orderByDesc("is_top").orderByDesc("is_hot").orderByDesc("create_time").orderByDesc("aid");
    }

    private Article adjacent(Article current, boolean newer) {
        if (current == null || current.getAid() == null) {
            return null;
        }
        String time = current.getCreateTime() == null ? "" : current.getCreateTime();
        QueryWrapper<Article> wrapper = new QueryWrapper<Article>()
                .eq("isOpen", "1")
                .eq("publish_status", "1")
                .ne("aid", current.getAid());
        if (newer) {
            wrapper.and(w -> w.gt("create_time", time).or().eq("create_time", time).gt("aid", current.getAid()));
            wrapper.orderByAsc("create_time").orderByAsc("aid");
        } else {
            wrapper.and(w -> w.lt("create_time", time).or().eq("create_time", time).lt("aid", current.getAid()));
            wrapper.orderByDesc("create_time").orderByDesc("aid");
        }
        wrapper.last("limit 1");
        List<Article> list = articleMapper.selectList(wrapper);
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    private boolean isStatus(String publishStatus) {
        return "0".equals(publishStatus) || "1".equals(publishStatus) || "2".equals(publishStatus);
    }

    private String statusLabel(String publishStatus) {
        if ("0".equals(publishStatus)) {
            return "草稿";
        }
        if ("1".equals(publishStatus)) {
            return "已发布";
        }
        return "已下线";
    }

    private String statusDone(String publishStatus) {
        if ("0".equals(publishStatus)) {
            return "已存为草稿";
        }
        if ("1".equals(publishStatus)) {
            return "文章已发布";
        }
        return "文章已下线";
    }

    private int score(Article article, Article current, Set<String> tags) {
        int score = smartService.overlap(article.getTagNames(), tags) * 5;
        if (current.getCid() != null && current.getCid().equals(article.getCid())) {
            score += 3;
        }
        if (article.getIsHot() != null && article.getIsHot() == 1) {
            score += 1;
        }
        return score;
    }

    private String normalizeTags(String tagNames) {
        List<String> tags = fmt.tags(tagNames);
        if (tags.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String tag : tags) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(tag);
        }
        return builder.toString();
    }

    private void fill(List<Article> articles) {
        if (articles == null || articles.isEmpty()) {
            return;
        }
        Map<Integer, String> categories = new HashMap<Integer, String>();
        for (Category category : categoryMapper.selectList(null)) {
            categories.put(category.getCid(), category.getCname());
        }
        Map<Integer, String> authors = new HashMap<Integer, String>();
        for (User user : userMapper.selectList(null)) {
            authors.put(user.getUid(), Texts.blank(user.getNickname()) ? user.getUsername() : user.getNickname());
        }
        List<String> dictionary = new ArrayList<String>();
        for (Tag tag : tagMapper.selectList(null)) {
            if (!Texts.blank(tag.getTname())) {
                dictionary.add(tag.getTname());
            }
        }
        for (Article article : articles) {
            article.setCategoryName(categories.containsKey(article.getCid()) ? categories.get(article.getCid()) : "未分类");
            article.setAuthorName(authors.containsKey(article.getUid()) ? authors.get(article.getUid()) : "佚名");
            article.setReadingMinutes(smartService.minutes(article.getContent()));
            article.setKeywords(smartService.keywords(article.getTitle(), article.getContent(), article.getTagNames(), dictionary));
        }
    }
}
