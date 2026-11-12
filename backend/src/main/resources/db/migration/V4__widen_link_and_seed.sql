-- V4 收尾：友链列扩容（申请转正写入更长的站点信息）、补默认设置、示例日记/公告。

ALTER TABLE t_link
  MODIFY site_name VARCHAR(30) NOT NULL COMMENT '站点名',
  MODIFY site_url VARCHAR(200) NOT NULL COMMENT '站点地址',
  MODIFY site_desc VARCHAR(100) NOT NULL COMMENT '站点描述';

INSERT IGNORE INTO t_setting (skey, svalue) VALUES
  ('site_subtitle', '只要朝着一个方向努力，一切都会变得得心应手'),
  ('about', '这里沿用资料里的栏目、文章和友链。\n公开文章会出现在前台，正文按 Markdown 显示。\n相关阅读会按栏目、标签和访问量推荐，阅读时间按篇幅估算。\n评论可以改成先审后发。'),
  ('footer', 'Spring Boot 3 · Nuxt 3 · MySQL · Redis'),
  ('show_visit', '1'),
  ('icp', '');

INSERT INTO t_diary (title, content, mood, create_time)
  SELECT '把原来的博客接了过来', '栏目、文章、标签和友链还在原来的库里。新的前台和后台只是换了一种跑法，没有另起一份数据。', '开工', NOW()
  WHERE NOT EXISTS (SELECT 1 FROM t_diary);

INSERT INTO t_notice (title, content, is_top, create_time)
  SELECT '站点已经用 Spring Boot 3 跑起来了', '前台可以看文章、留言、日记和友链。后台从右上角进入，管理员账号没有改。', '1', NOW()
  WHERE NOT EXISTS (SELECT 1 FROM t_notice);
