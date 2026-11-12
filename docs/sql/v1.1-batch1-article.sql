-- 智序 V1.1 第一批：文章状态、SEO、置顶
-- 本文件不会被应用启动时自动执行。请在维护窗口按下面的顺序手工执行。
-- 不要跳步。一致性检查不是 0 行时，停止发版，不要继续改成 NOT NULL，也不要部署新程序。

-- 1. 备份。确认备份行数与 t_article 一致后再继续。
CREATE TABLE t_article_bak_v11 AS SELECT * FROM t_article;

-- 2. 先加可空的 publish_status。此时不要改成 NOT NULL，也不要给它默认值。
--    is_top 默认 0，已有文章不会被当成置顶。
ALTER TABLE t_article
  ADD COLUMN publish_status CHAR(1) NULL COMMENT '0草稿 1已发布 2已下线' AFTER isOpen,
  ADD COLUMN seo_title VARCHAR(255) NULL COMMENT 'SEO标题' AFTER publish_status,
  ADD COLUMN seo_keywords VARCHAR(255) NULL COMMENT 'SEO关键词' AFTER seo_title,
  ADD COLUMN seo_desc VARCHAR(500) NULL COMMENT 'SEO描述' AFTER seo_keywords,
  ADD COLUMN is_top INT NOT NULL DEFAULT 0 COMMENT '0否 1置顶' AFTER is_hot;

-- 3. 停掉仍在写 t_article 的旧程序，再做回填。旧程序只会改 isOpen，不会写 publish_status。

-- 4. 历史回填。公开保持已发布，私密记为已下线，不回填为草稿。
--    isOpen 不是 0 或 1 的行保持 NULL，留给下一步检查拦住。
UPDATE t_article
SET publish_status = '1'
WHERE publish_status IS NULL AND isOpen = '1';

UPDATE t_article
SET publish_status = '2'
WHERE publish_status IS NULL AND isOpen = '0';

-- 5. 一致性检查。必须返回 0 行。
SELECT aid, title, isOpen, publish_status
FROM t_article
WHERE publish_status IS NULL
   OR publish_status NOT IN ('0', '1', '2')
   OR (publish_status = '1' AND isOpen <> '1')
   OR (publish_status IN ('0', '2') AND isOpen <> '0');

-- 6. 只有上一条是 0 行时，才执行下面这句。不设置 DEFAULT。
ALTER TABLE t_article
  MODIFY COLUMN publish_status CHAR(1) NOT NULL COMMENT '0草稿 1已发布 2已下线';

-- 7. 立刻部署会同时写入 publish_status 和 isOpen 的新程序。
-- 8. 启动后再执行一次第 5 步的检查，仍然必须是 0 行。
-- 9. 再验证新建、编辑、存草稿、发布、下线。

-- 回滚（会丢失 publish_status、SEO 和 is_top 里的数据；isOpen 保持原样）
-- 回滚数据库的同时必须退回旧程序，否则新程序会查询已经删除的列。
-- ALTER TABLE t_article
--   DROP COLUMN publish_status,
--   DROP COLUMN seo_title,
--   DROP COLUMN seo_keywords,
--   DROP COLUMN seo_desc,
--   DROP COLUMN is_top;
-- 备份表确认不再需要后再手工删除：DROP TABLE t_article_bak_v11;
