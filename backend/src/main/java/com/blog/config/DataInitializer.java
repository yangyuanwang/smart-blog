package com.blog.config;

import com.blog.common.Texts;
import com.blog.entity.Diary;
import com.blog.entity.Notice;
import com.blog.mapper.DiaryMapper;
import com.blog.mapper.NoticeMapper;
import com.blog.service.SettingService;
import com.blog.service.UserService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final SettingService settingService;
    private final UserService userService;
    private final DiaryMapper diaryMapper;
    private final NoticeMapper noticeMapper;
    private final JdbcTemplate jdbcTemplate;

    public DataInitializer(SettingService settingService, UserService userService,
                           DiaryMapper diaryMapper, NoticeMapper noticeMapper, JdbcTemplate jdbcTemplate) {
        this.settingService = settingService;
        this.userService = userService;
        this.diaryMapper = diaryMapper;
        this.noticeMapper = noticeMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        settingService.putIfAbsent("site_name", "智能博客");
        settingService.putIfAbsent("site_subtitle", "只要朝着一个方向努力，一切都会变得得心应手");
        settingService.putIfAbsent("about", "这里沿用资料里的栏目、文章和友链。\n公开文章会出现在前台，正文按 Markdown 显示。\n相关阅读会按栏目、标签和访问量推荐，阅读时间按篇幅估算。\n评论可以改成先审后发。");
        settingService.putIfAbsent("footer", "Spring Boot · 本地 MySQL");
        settingService.putIfAbsent("page_size", "6");
        settingService.putIfAbsent("comment_audit", "0");
        settingService.putIfAbsent("admin_message_pass", "1");
        ensureMessageColumn("uid", "INT DEFAULT NULL");
        ensureMessageColumn("ip", "VARCHAR(64) DEFAULT NULL");
        ensureMessageColumn("parent_id", "INT DEFAULT NULL");
        settingService.putIfAbsent("admin_message_pass", "1");
        settingService.putIfAbsent("allow_comment", "1");
        ensureMessageColumn("uid", "INT NULL");
        ensureMessageColumn("ip", "VARCHAR(64) NULL");
        ensureMessageColumn("parent_id", "INT NULL");
        settingService.putIfAbsent("allow_register", "1");
        settingService.putIfAbsent("show_visit", "1");
        userService.repairPlainPasswords();
        if (diaryMapper.selectCount(null) == 0) {
            Diary first = new Diary();
            first.setTitle("把原来的博客接了过来");
            first.setMood("开工");
            first.setContent("栏目、文章、标签和友链还在原来的库里。新的前台和后台只是换了一种跑法，没有另起一份数据。");
            first.setCreateTime(Texts.now());
            diaryMapper.insert(first);
            Diary second = new Diary();
            second.setTitle("云南那篇还想接着写");
            second.setMood("想走");
            second.setContent("资料里有一篇去云南的草稿。路线先记着：古城和洱海，别把一天排满。");
            second.setCreateTime(Texts.now());
            diaryMapper.insert(second);
        }
        if (noticeMapper.selectCount(null) == 0) {
            Notice notice = new Notice();
            notice.setTitle("站点已经用 Spring Boot 跑起来了");
            notice.setContent("前台可以看文章、留言、日记和友链。后台从右上角进入，管理员账号没有改。");
            notice.setIsTop("1");
            notice.setCreateTime(Texts.now());
            noticeMapper.insert(notice);
        }
    }

    private void ensureMessageColumn(String name, String definition) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_message' AND COLUMN_NAME = ?",
                Integer.class, name);
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE t_message ADD COLUMN " + name + " " + definition);
        }
    }
}
