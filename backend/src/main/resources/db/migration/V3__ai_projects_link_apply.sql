-- V3 内容增强：AI 摘要字段、作品集、友链申请、文章-标签关联表及数据回填。

ALTER TABLE t_article
  ADD COLUMN ai_summary VARCHAR(300) NULL COMMENT 'AI 摘要' AFTER seo_desc,
  ADD COLUMN ai_generated_at DATETIME NULL COMMENT 'AI 摘要生成时间' AFTER ai_summary;

CREATE TABLE t_project (
  id INT NOT NULL AUTO_INCREMENT COMMENT '主键',
  name VARCHAR(64) NOT NULL COMMENT '项目名',
  summary VARCHAR(200) NOT NULL COMMENT '一句话简介',
  description TEXT NULL COMMENT '详细介绍（Markdown）',
  tech_stack VARCHAR(255) NULL COMMENT '技术栈，逗号分隔',
  cover VARCHAR(255) NULL COMMENT '封面图',
  repo_url VARCHAR(255) NULL COMMENT '仓库地址',
  demo_url VARCHAR(255) NULL COMMENT '演示地址',
  featured CHAR(1) NOT NULL DEFAULT '0' COMMENT '1精选（进首页）',
  sort INT NOT NULL DEFAULT 0 COMMENT '排序，小在前',
  status CHAR(1) NOT NULL DEFAULT '1' COMMENT '1展示 0隐藏',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NULL,
  PRIMARY KEY (id),
  KEY idx_project_sort (status, featured, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作品集';

CREATE TABLE t_link_apply (
  id INT NOT NULL AUTO_INCREMENT COMMENT '主键',
  site_name VARCHAR(30) NOT NULL COMMENT '站点名',
  site_url VARCHAR(120) NOT NULL COMMENT '站点地址',
  site_desc VARCHAR(100) NOT NULL COMMENT '站点描述',
  contact VARCHAR(100) NULL COMMENT '联系方式（选填）',
  status CHAR(1) NOT NULL DEFAULT '0' COMMENT '0待审 1通过 2拒绝',
  remark VARCHAR(200) NULL COMMENT '审核备注（拒绝理由）',
  ip VARCHAR(64) NULL COMMENT '申请来源IP',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_link_apply_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='友链申请';

CREATE TABLE t_article_tag (
  article_id INT NOT NULL COMMENT '文章',
  tag_id INT NOT NULL COMMENT '标签',
  PRIMARY KEY (article_id, tag_id),
  KEY idx_article_tag_tag (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章-标签关联';

-- 回填关联表：先规范化 tagNames 分隔符（去空格），再按名字匹配建立关联
UPDATE t_article
  SET tagNames = REPLACE(REPLACE(tagNames, ', ', ','), '，', ',')
  WHERE tagNames IS NOT NULL AND tagNames <> '';

INSERT IGNORE INTO t_article_tag (article_id, tag_id)
SELECT a.aid, t.tid
  FROM t_article a
  JOIN t_tag t
    ON t.tname IS NOT NULL AND t.tname <> ''
   AND FIND_IN_SET(t.tname, IFNULL(a.tagNames, ''));
