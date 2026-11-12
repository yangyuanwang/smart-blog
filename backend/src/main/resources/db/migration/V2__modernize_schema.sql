-- V2 现代化：时间列 CHAR -> DATETIME（MySQL 对 'yyyy-MM-dd[ HH:mm:ss]' 文本自动转换），
-- 密码列扩容适配 BCrypt，新增邮箱列，补齐查询索引，写入默认站点设置。

ALTER TABLE t_user
  MODIFY createTime DATETIME NULL COMMENT '创建日期',
  MODIFY lastLoginTime DATETIME NULL COMMENT '最近登录时间',
  MODIFY password VARCHAR(72) NOT NULL COMMENT '密码（BCrypt，历史 MD5 登录时自动升级）',
  ADD COLUMN email VARCHAR(128) NULL COMMENT '邮箱（找回密码用）' AFTER phone;

ALTER TABLE t_article
  MODIFY create_time DATETIME NULL COMMENT '发布时间',
  MODIFY update_time DATETIME NULL COMMENT '更新时间';

ALTER TABLE t_comment MODIFY create_time DATETIME NOT NULL;
ALTER TABLE t_message MODIFY create_time DATETIME NOT NULL;
ALTER TABLE t_diary MODIFY create_time DATETIME NOT NULL;
ALTER TABLE t_notice MODIFY create_time DATETIME NOT NULL;
ALTER TABLE t_login_log MODIFY login_time DATETIME NOT NULL;

ALTER TABLE t_article
  ADD INDEX idx_article_status_time (publish_status, is_top, create_time),
  ADD INDEX idx_article_cid (cid),
  ADD INDEX idx_article_open (isOpen);

ALTER TABLE t_comment ADD INDEX idx_comment_status (aid, status);
ALTER TABLE t_message ADD INDEX idx_message_status (status, parent_id);
ALTER TABLE t_message ADD INDEX idx_message_ip (ip);
ALTER TABLE t_login_log ADD INDEX idx_login_log_time (login_time);

INSERT IGNORE INTO t_setting (skey, svalue) VALUES
  ('site_name', '智序 · Smart Workspace'),
  ('page_size', '6'),
  ('allow_comment', '1'),
  ('comment_audit', '1'),
  ('allow_register', '1'),
  ('admin_message_pass', '1'),
  ('captcha_enabled', '0');
