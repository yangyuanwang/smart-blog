# 智序博客系统 V1.1 数据库增量计划

本文件只描述变更，不执行 SQL，也不修改 `src/main/resources/schema.sql`。

【复核】与实施计划同一轮收紧：状态列与 `isOpen` 必须成对；标签改名不回写 `tagNames`；友链审核用状态条件且不覆盖正式友链；不建 `t_file`；`t_nav` 空表回退、有行但全部停用时不回退。下面若与更早句子冲突，以标了【复核】的段落为准。

当前库名 `blog`。表名保持 `t_*`。已有时间字段是 `CHAR(19)` 或 `CHAR(10)`，新时间列沿用 `CHAR(19)`，由应用写入 `Texts.now()` 那种格式，不改成 `DATETIME`。

`application.yml` 中 `spring.sql.init.mode=always`，启动只会跑 `schema.sql` 里的 `CREATE TABLE IF NOT EXISTS`。**已有表加列不会自动发生。** 发版时要手工执行下面的增量脚本。新环境若只跑旧的 `schema.sql`，也会缺列。实施阶段应同时把等价列补进 `schema.sql` 的 `CREATE TABLE`，供空库使用；那一步不在本轮做。

执行前备份相关表。建议：

```sql
CREATE TABLE t_article_bak_v11 AS SELECT * FROM t_article;
CREATE TABLE t_category_bak_v11 AS SELECT * FROM t_category;
CREATE TABLE t_tag_bak_v11 AS SELECT * FROM t_tag;
CREATE TABLE t_link_bak_v11 AS SELECT * FROM t_link;
```

备份表确认无用后再删，不和回滚脚本绑在一起自动删除。

---

## 第一批：`t_article`

### 现有相关列

| 列 | 现含义 | 本批 |
|---|---|---|
| `isOpen` CHAR | `1` 前台可见，`0` 不可见 | 保留，不改语义 |
| `is_hot` INT | `1` 热门 | 保留 |
| `tagNames` VARCHAR(255) | 逗号分隔标签文字 | 本批不改 |
| `title` / `digest` | 标题、摘要 | `h1` 与空 SEO 标题只用 `title`。`digest` 不代替 `seo_desc` |

### 新增列

| 列 | 类型 | 空 | 说明 |
|---|---|---|---|
| `publish_status` | CHAR(1) | 先允许 NULL，回填后改为 NOT NULL | `0` 草稿，`1` 已发布，`2` 已下线 |
| `seo_title` | VARCHAR(255) | NULL | 文章详情 `<title>` 的页名部分。空则用 `title` |
| `seo_keywords` | VARCHAR(255) | NULL | 文章详情 `<meta name="keywords">`。空则不输出标签 |
| `seo_desc` | VARCHAR(500) | NULL | 文章详情 `<meta name="description">`。空则不输出标签 |
| `is_top` | INT NOT NULL DEFAULT 0 | 否 | `1` 置顶。与 `is_hot` 并存 |

实体 `Article` 使用驼峰，`map-underscore-to-camel-case=true`，因此 `publishStatus`、`seoTitle`、`seoKeywords`、`seoDesc`、`isTop` 能对上，不必再写 `@TableField`。`isOpen`、`tagNames` 已有 `@TableField`，不要改。

### 增量 SQL

```sql
ALTER TABLE t_article
  ADD COLUMN publish_status CHAR(1) NULL COMMENT '0草稿 1已发布 2已下线' AFTER isOpen,
  ADD COLUMN seo_title VARCHAR(255) NULL COMMENT 'SEO标题' AFTER publish_status,
  ADD COLUMN seo_keywords VARCHAR(255) NULL COMMENT 'SEO关键词' AFTER seo_title,
  ADD COLUMN seo_desc VARCHAR(500) NULL COMMENT 'SEO描述' AFTER seo_keywords,
  ADD COLUMN is_top INT NOT NULL DEFAULT 0 COMMENT '0否 1置顶' AFTER is_hot;
```

`AFTER` 只影响列顺序，不影响应用。若线上 MySQL 不支持在同一条 `ALTER` 里多次 `ADD`，拆成五条即可。

### 回填

**未确认前不要执行 `isOpen='0'` 的分支。** 建议把历史私密文章标成已下线，而不是草稿：

```sql
UPDATE t_article
SET publish_status = CASE WHEN isOpen = '1' THEN '1' ELSE '2' END
WHERE publish_status IS NULL;

ALTER TABLE t_article
  MODIFY COLUMN publish_status CHAR(1) NOT NULL DEFAULT '1' COMMENT '0草稿 1已发布 2已下线';
```

核对分组：

```sql
SELECT isOpen, publish_status, COUNT(*) FROM t_article GROUP BY isOpen, publish_status;
```

【复核】一致性检查。下面这条必须返回 0 行，才能把 `publish_status` 改成 NOT NULL，也才能发布新代码：

```sql
SELECT aid, title, isOpen, publish_status
FROM t_article
WHERE publish_status IS NULL
   OR publish_status NOT IN ('0', '1', '2')
   OR (publish_status = '1' AND isOpen <> '1')
   OR (publish_status IN ('0', '2') AND isOpen <> '0');
```

允许的组合只有：`1/1`（已发布）、`0/0`（草稿）、`2/0`（已下线）。历史 `isOpen=0` 回填成 `2` 还是 `0`，仍待确认；未确认前不要执行上面的 `UPDATE`。`MODIFY` 与 `UPDATE` 分开，不要在同一窗口里先改成 NOT NULL 再让旧程序继续写文章。

【复核】`MODIFY ... NOT NULL DEFAULT '1'` 只能在新代码即将接管写入时做。旧程序的 `insert` 不带 `publish_status`，一旦默认值是 `'1'`，新建私密文章会变成「状态已发布、isOpen 为 0」。`is_top` 默认 0 对旧插入无害。SEO 列保持 NULL，旧代码不写它们。新建文章必须由应用显式写入 `publish_status` 与 `isOpen`，不能只靠这个默认值。新增列一节里的实体映射不变。

### 索引

现有前台查询继续用 `isOpen`。文章量不大，本批不必加索引。若以后列表按状态筛选变慢，再加：

```sql
ALTER TABLE t_article ADD INDEX idx_article_publish (publish_status, aid);
```

不在本批默认执行。

### 回滚

```sql
ALTER TABLE t_article
  DROP COLUMN publish_status,
  DROP COLUMN seo_title,
  DROP COLUMN seo_keywords,
  DROP COLUMN seo_desc,
  DROP COLUMN is_top;
```

回滚会丢掉这几列里的新数据。`isOpen` 不动，前台可见性回到只看 `isOpen`。应用代码必须同时退回，否则会查不存在的列。

---

## 第二批：`t_category`、`t_tag`

### 现有列

`t_category`：`cid`，`cname`。  
`t_tag`：`tid`，`tname`，`cid`。

### 新增列

| 表 | 列 | 类型 | 说明 |
|---|---|---|---|
| t_category | cdesc | VARCHAR(255) NULL | 描述，可空 |
| t_category | cicon | VARCHAR(64) NULL | 短文本图标，不是文件路径。若改为图片，长度不够，要重定 |
| t_category | sort | INT NOT NULL DEFAULT 0 | 小的靠前 |
| t_tag | sort | INT NOT NULL DEFAULT 0 | 小的靠前 |

`Category.cdesc`、`cicon`、`sort` 与下划线映射无关（列名无下划线）。`Tag.sort` 同理。

### 增量 SQL

```sql
ALTER TABLE t_category
  ADD COLUMN cdesc VARCHAR(255) NULL COMMENT '栏目描述' AFTER cname,
  ADD COLUMN cicon VARCHAR(64) NULL COMMENT '栏目标识' AFTER cdesc,
  ADD COLUMN sort INT NOT NULL DEFAULT 0 COMMENT '排序，小的靠前' AFTER cicon;

ALTER TABLE t_tag
  ADD COLUMN sort INT NOT NULL DEFAULT 0 COMMENT '排序，小的靠前' AFTER cid;
```

现有行的 `sort` 为 0。查询时 `ORDER BY sort, cid` / `ORDER BY sort, tid`，同排序值仍保持原来的 id 顺序。

### 标签改名与 `tagNames`

【复核】只改 `t_tag.tname`，不执行任何更新 `t_article.tagNames` 的 SQL。不准备方案 B 的替换脚本。

禁止用 `REPLACE` 或 `LIKE` 改标签文字，`Java` 会误伤 `JavaScript`：

```sql
-- 禁止
UPDATE t_article SET tagNames = REPLACE(tagNames, 'Java', 'JVM');
```

文章保存时如何保留「标签表里已经没有的文字」，见实施计划第二批，那是应用逻辑，不是本文件的 UPDATE。

### 索引

栏目、标签数量很少，不新增索引。`cname` 唯一约束现在由应用检查，不加数据库唯一索引，避免历史重名（若有）导致 `ALTER` 失败。实施前可查：

```sql
SELECT cname, COUNT(*) FROM t_category GROUP BY cname HAVING COUNT(*) > 1;
SELECT cid, tname, COUNT(*) FROM t_tag GROUP BY cid, tname HAVING COUNT(*) > 1;
```

### 回滚

```sql
ALTER TABLE t_category DROP COLUMN cdesc, DROP COLUMN cicon, DROP COLUMN sort;
ALTER TABLE t_tag DROP COLUMN sort;
```

不回滚 `tname`/`cname` 上已经保存的改名。那些是业务数据，不是结构。

---

## 第三批：`t_link` 与 `t_link_apply`

### `t_link` 现有列

`id`，`site_name` VARCHAR(15)，`site_url` VARCHAR(45)，`site_desc` VARCHAR(45)，`sort` INT。

长度限制保持不变。

### `t_link` 新增列

| 列 | 类型 | 说明 |
|---|---|---|
| site_logo | VARCHAR(255) NULL | 站内路径如 `/uploads/...`，或空 |
| enabled | CHAR(1) NOT NULL DEFAULT '1' | `1` 启用，`0` 停用 |

默认 `'1'`，已有友链迁移后仍启用。

```sql
ALTER TABLE t_link
  ADD COLUMN site_logo VARCHAR(255) NULL COMMENT 'Logo 路径' AFTER site_desc,
  ADD COLUMN enabled CHAR(1) NOT NULL DEFAULT '1' COMMENT '1启用 0停用' AFTER sort;
```

实体 `Link` 增加 `siteLogo`、`enabled`。下划线映射已开启。

### 新表 `t_link_apply`

```sql
CREATE TABLE IF NOT EXISTS t_link_apply (
  id INT NOT NULL AUTO_INCREMENT,
  site_name VARCHAR(15) NOT NULL COMMENT '站点名',
  site_url VARCHAR(45) NOT NULL COMMENT '站点地址',
  site_desc VARCHAR(45) NOT NULL COMMENT '站点描述',
  site_logo VARCHAR(255) NULL COMMENT 'Logo 路径',
  applicant VARCHAR(32) NOT NULL COMMENT '申请人显示名',
  uid INT NULL COMMENT '登录用户，可空',
  status CHAR(1) NOT NULL DEFAULT '0' COMMENT '0待审 1通过 2拒绝',
  create_time CHAR(19) NOT NULL,
  audit_time CHAR(19) NULL,
  remark VARCHAR(200) NULL COMMENT '审核说明',
  PRIMARY KEY (id),
  KEY idx_link_apply_status (status),
  KEY idx_link_apply_url (site_url)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

不对 `site_url` 做唯一约束。拒绝后允许同一地址再次申请。待审重复由应用按 `status='0'` 检查。

### 通过审核时的数据

【复核】同一事务，顺序固定：

1. `UPDATE t_link_apply SET ... WHERE id=? AND status='0'`。影响行数不是 1，则回滚并视为已处理，不插入友链。
2. 按 `site_url` 查 `t_link`。已有行则整单回滚，申请仍是待审，不更新该行的名称、描述、Logo、排序。
3. 否则 `INSERT` 一条 `enabled='1'`、`sort=0` 的 `t_link`。
4. 只有插入成功，申请才变为通过并写 `audit_time`。拒绝同样使用 `WHERE id=? AND status='0'`，影响行数不是 1 则失败。

任一步失败则整单回滚。地址长度仍是 VARCHAR(45)，与现有 `t_link.site_url` 一致；更长的校验放在应用里，和 `saveLink` 同一套，不靠放宽列长度。

### 回滚

```sql
ALTER TABLE t_link DROP COLUMN site_logo, DROP COLUMN enabled;
DROP TABLE t_link_apply;
```

先确认没有只存在于申请表、尚未写入 `t_link` 的待审数据需要保留。`DROP TABLE` 会清掉申请记录。已插入 `t_link` 的正式友链还在，只是失去 Logo 和启用列；停用状态会丢失，回滚后应用若不再过滤 `enabled`，这些友链会重新全部显示。

---

## 第四批

无表结构变更。

待审口径（只读，不改数据）：

- 留言：`t_message.status = '0'`
- 评论：`t_comment.status = '0'`（含被隐藏后写回 0 的记录）
- 友链申请：`t_link_apply.status = '0'`，且仅在第三批表存在之后查询

不要为了图表新增 `blog_visit_log`。

---

## 第五批

### 新表 `t_operate_log`

```sql
CREATE TABLE IF NOT EXISTS t_operate_log (
  id INT NOT NULL AUTO_INCREMENT,
  uid INT NULL,
  username VARCHAR(32) NULL,
  module VARCHAR(32) NOT NULL COMMENT '模块',
  action VARCHAR(32) NOT NULL COMMENT '动作',
  detail VARCHAR(500) NULL COMMENT '摘要，禁止写入密码和验证码',
  success CHAR(1) NOT NULL COMMENT '1成功 0失败',
  create_time CHAR(19) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_operate_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

不记录请求体、密码、验证码。`success`：`1` 业务成功，`0` 业务失败或系统异常。`detail` 最长 500，系统异常只放异常类名。不改 `t_login_log`。

### 新表 `t_nav`

```sql
CREATE TABLE IF NOT EXISTS t_nav (
  id INT NOT NULL AUTO_INCREMENT,
  name VARCHAR(20) NOT NULL,
  path VARCHAR(64) NOT NULL COMMENT '站内路径',
  sort INT NOT NULL DEFAULT 0,
  enabled CHAR(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (id),
  KEY idx_nav_sort (sort, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

【复核】不预插数据。

- 表没有任何行：前台继续用 `fragments/front.html` 里的固定菜单。
- 已有行但没有 `enabled='1'`：不回退到固定菜单，顶栏不显示这些入口。

路径白名单（应用校验，不靠数据库枚举）：`/`、`/articles`、`/messages`、`/diaries`、`/links`、`/about`。不把白名单做成 CHECK 约束，避免以后加站内路径时还要改表。

### 文件

不建 `t_file`。上传文件仍在 `uploads` 目录。删除前由应用检查路径落在该目录内、且是普通文件，再查：

- `t_article.logo`
- `t_user.img`
- 若第三批已执行：`t_link.site_logo`、`t_link_apply.site_logo`

比较时用完整路径前缀 `/uploads/文件名`。文件不存在、被引用、路径非法都由应用返回明确结果，不靠数据库触发器。

### 回滚

```sql
DROP TABLE t_operate_log;
DROP TABLE t_nav;
```

丢弃日志和导航配置。前台因表不存在必须同时退回只读模板里的菜单，不能在代码仍查询 `t_nav` 时删表。

---

## 发版顺序

【复核】每一批都按这个顺序，不能先发读新列的程序：

1. 备份该批相关表。
2. 执行 `ALTER` / `CREATE`。第一批的 `publish_status` 先保持可空，不要在同一步改成 NOT NULL。
3. 执行已确认的回填。历史私密文章回填成下线还是草稿未确认前，跳过第一批 `UPDATE`。
4. 跑核对 SQL。第一批一致性检查必须是 0 行。
5. 停掉仍在写这些表的旧进程，避免旧代码只改 `isOpen`。
6. 第一批再执行 `MODIFY publish_status NOT NULL`。
7. 立刻发布会同时写 `publish_status` 和 `isOpen` 的新程序。
8. 发布后再跑一次一致性检查。

【复核】新旧代码重叠时的风险：回填之后、新程序之前，旧的 `ArticleService.save` 和 `updateOpen` 只写 `isOpen`。这时 `publish_status` 会过期。若已经执行 `DEFAULT '1'`，旧程序新建的私密文章还会出现「状态已发布、前台不可见」。处理办法是缩短窗口：一致性检查通过后马上换程序，中间不要再用旧后台改文章。发版前检查若不为 0 行，停止发布，按 `aid` 人工核对，不要再跑一条把所有 `isOpen=0` 改成下线或草稿的 `UPDATE`，以免盖掉已经标成另一种状态的行。

先发应用、后改表，会查询未知列。先改表、后发应用，旧代码不读新列，可以运行；第三批旧的 `links()` 不读 `enabled`，新列不影响旧版本显示。第一批不能把可见性只放在 `publish_status` 上。

## 明确不做的结构变更

- 不重命名现有表和列。
- 不把 `isOpen` 改成三态。
- 不建 `blog_article_tag`。
- 不建 `t_file`。
- 不建访问日志表。
- 不把密码列从 MD5 的 `VARCHAR(32)` 加长（那是换哈希算法时的事）。
- 不用 `REPLACE` 更新 `tagNames`。

## 仍待确认（不在脚本里代为决定）

- 历史 `isOpen='0'` 回填为已下线（`2`）还是草稿（`0`）。
- 其余产品项见实施计划各批「待确认」，包括草稿能否空正文、前台错误文案、栏目图标、友链验证码、评论待办文案、操作日志是否提前。
