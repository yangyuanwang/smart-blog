# 智序博客系统 V1.1 增量开发实施计划

本计划只分析方案，不修改业务代码、数据库和页面。依据是当前仓库代码，以及 `docs/SMART_BLOG_DEVELOPMENT_REQUIREMENTS.md`、`docs/PROJECT_STATUS_ANALYSIS.md`。

【复核】以下规则覆盖文中较早的含糊写法：文章状态只走一个转换方法；SEO 只作用于文章详情的 `title` / `meta`，不改 `h1`；标签改名采用方案 A，并保留标签表里已经没有的历史文字；友链审核带状态条件且不覆盖正式友链；不建 `t_file`；导航空表才回退硬编码，全部停用时不回退。仍待产品决定的事项集中在各批「待确认」和文末。

约束：保留 `t_*` 表名和现有字段；保留 `AuthInterceptor`、登录和现有 URL；继续表单 POST + 重定向；只扩展已有 Controller / Service / Mapper；不新建第二套业务 Controller；不新增 DTO/VO 包（当前项目没有这层，表单继续绑定实体）。

项目里没有独立的 DTO、VO 类。下表里的「Entity」指已有或拟新增的实体。Mapper 除 `ArticleMapper.sumVisits` 外都是 MyBatis-Plus `BaseMapper`，加字段不需要新 SQL，加表才需要新 Mapper。

权限沿用 `src/main/java/com/blog/config/AuthInterceptor.java`：`/admin/**` 除 `/admin/login` 外必须是管理员。前台新页面不加后台拦截。

## 批次依赖与复杂度

| 批次 | 依赖 | 复杂度 |
|---|---|---|
| 第一批 文章 | 无 | 中高。状态必须和 `isOpen` 并存 |
| 第二批 栏目与标签 | 无。与第一批可分开做 | 中。改名不回写文章文本，保存时要保住历史标签文字 |
| 第三批 友链 | 无 | 中。新增一张申请表 |
| 第四批 控制台 | 留言、评论计数不依赖前三批。友链申请计数依赖第三批 | 低 |
| 第五批 系统管理 | 文件引用若包含友链 Logo，依赖第三批字段。操作日志建议最后做，避免每一批都埋点 | 中高 |

建议顺序：第一批 → 第二批 → 第三批 → 第四批 → 第五批。不要并行改 `ArticleService` 和 `ContentService`。

`src/main/resources/schema.sql` 只有 `CREATE TABLE IF NOT EXISTS`，并且 `application.yml` 里 `spring.sql.init.mode=always`。它不会给已有表加列。增量必须用单独的 `ALTER`，执行方式见数据库计划，不要假设重启应用就会改表。

---

## 第一批：文章管理完善

### 1. 功能目标

在不改掉现有公开/私密行为的前提下，增加草稿、已发布、已下线，以及 SEO、置顶。详情页补目录、上一篇、下一篇、分享、代码复制和图片预览。相关文章已经存在，只复用。

### 2. 当前实现情况

- 前台是否可见，只看 `t_article.isOpen`。`1` 公开，`0` 不公开。
- 写入点：`ArticleService.save` 把非 `"0"` 都存成 `"1"`；`updateOpen` 同样只写 `0`/`1`。
- 前台查询都带 `.eq("isOpen", "1")`：`pagePublic`、`latest`、`hot`、`related`、`publicCount`、`countByCategory`，以及 `like`、`addComment` 的存在性检查。
- `ArticleService.detail`：非公开文章对普通访客抛出「这篇文章还没有公开」；管理员 `allowPrivate=true`，可以打开。见 `FrontController.article`。
- 热门是 `is_hot`，列表排序是热门、发布时间、`aid`。没有置顶，没有 SEO 列。
- 正文经 `SmartService.render` 转 HTML，已 `escapeHtml(true)`、`sanitizeUrls(true)`。
- 相关文章已实现：`ArticleService.related`，模板 `templates/front/article.html` 的「接着读」。没有上一篇、下一篇、目录、分享、代码复制、图片预览。
- 后台页面：`templates/admin/articles.html`、`templates/admin/article-form.html`。表单只有公开/私密和热门。

### 3. 数据库

新增列，不改 `isOpen`、`is_hot` 的含义。字段说明、回填和回滚见 `SMART_BLOG_V1.1_DATABASE_CHANGE_PLAN.md`。

- `publish_status`：`0` 草稿，`1` 已发布，`2` 已下线
- `seo_title`、`seo_keywords`、`seo_desc`
- `is_top`：`0`/`1`，与 `is_hot` 并存

不新增表。

### 4. 状态怎么和 `isOpen` 并存

前台过滤继续只认 `isOpen='1'`，这样现有查询不用改条件。

【复核】三种状态只允许下面这一组映射。没有第四种组合。

| 状态 | publish_status | isOpen | 前台 | 管理员打开详情 |
|---|---|---|---|---|
| 草稿 | 0 | 0 | 不可见 | 可见 |
| 已发布 | 1 | 1 | 可见 | 可见 |
| 已下线 | 2 | 0 | 不可见 | 可见 |

【复核】全库写入 `isOpen` 的入口只有这两处，都在 `ArticleService`：

1. `save` 第 161 行：`setIsOpen("0".equals(form.getIsOpen()) ? "0" : "1")`，由 `AdminArticleController.save` 调用。
2. `updateOpen` 第 191–194 行：`.set("isOpen", ...)`，由 `POST /admin/articles/open` 调用。列表按钮在 `templates/admin/articles.html`（「设为公开 / 设为私密」）。

`AdminArticleController.edit` 第 47–48 行在 `isOpen == null` 时 `setIsOpen("1")`，只影响新建表单的默认勾选，不写数据库。全仓库没有第三处 `.set("isOpen"` 或 `setIsOpen`。

【复核】发布、下线、存草稿收成一个方法，例如 `ArticleService.applyStatus(Article target, String publishStatus)`。它是唯一根据状态写 `publish_status` 和 `isOpen` 的地方。`save` 与列表上的状态按钮都调用它，禁止再直接 `setIsOpen` / `updateOpen`。

映射写死在这个方法里：`1 → isOpen=1`，`0` 和 `2 → isOpen=0`。传入其他值抛 `BizException`，两个字段都不改。

【复核】新建文章必须在 `insert` 前显式写入 `publish_status` 和 `isOpen`，不能只靠列默认值。编辑页用三个单选提交 `publishStatus`。新建页可以预选「已发布」（对应现在 `edit()` 把空 `isOpen` 显示成公开），但请求里必须带上该字段；缺失则拒绝保存。

【复核】去掉 `articles.html` 里对 `/admin/articles/open` 的公开/私密切换，改成「发布 / 下线 / 存草稿」，三者都调用 `applyStatus`。`updateOpen` 删除或改为私有且仅被 `applyStatus` 使用，避免旧入口只改 `isOpen`。`article-form.html` 的 `name="isOpen"` 单选一并去掉，避免表单再提交裸的公开/私密。

### 5. 代码与页面

| 类型 | 路径 | 动作 |
|---|---|---|
| Controller | `src/main/java/com/blog/web/AdminArticleController.java` | 修改。保存参数增加状态和 SEO；列表筛选状态 |
| Controller | `src/main/java/com/blog/web/FrontController.java` | 修改。详情增加上一篇、下一篇、目录；按下面的 SEO 规则传入 `head` |
| Service | `src/main/java/com/blog/service/ArticleService.java` | 修改。状态双写、置顶排序、相邻文章 |
| Service | `src/main/java/com/blog/service/SmartService.java` | 修改。从 Markdown 标题抽出目录，不改转义策略 |
| Mapper | `src/main/java/com/blog/mapper/ArticleMapper.java` | 复用。不需要新方法 |
| Entity | `src/main/java/com/blog/entity/Article.java` | 修改。新字段。目录、上一篇、下一篇用非表字段 |
| 页面 | `templates/admin/article-form.html` | 修改。去掉公开/私密单选，改为三种 `publishStatus`；增加 SEO 与置顶 |
| 页面 | `templates/admin/articles.html` | 修改。状态筛选和状态操作 |
| 页面 | `templates/front/article.html` | 修改。目录、上下篇、分享、代码复制、图片预览。相关文章复用 |
| 样式 | `src/main/resources/static/css/site.css` | 修改。只加详情页样式 |

不新建 Controller。

### 6. 页面交互

编辑页：

- 状态：草稿 / 已发布 / 已下线，提交 `publishStatus`。新建页预选已发布，但保存时缺这个字段就失败。
- 置顶复选框，独立于热门。
- 【复核】SEO 只加在文章详情，不改首页、列表、关于等页面的标题。现有 `fragments/front.html` 的 `head(pageTitle)` 始终输出 `<title>{pageTitle} · 智序</title>`，没有 keywords/description。文章页按下面规则传入，其他页面不传这些值，因此不会多出标签。
- 保存、取消沿用现有按钮。失败用现有 flash，不引入新的提示组件。
- 正文仍是 Markdown 文本框。本批不做左右分栏实时预览，避免无意义的新编辑器。

【复核】文章详情 SEO 与正文标题：

| 字段 | 输出 | 为空时 |
|---|---|---|
| `seo_title` | 传给 `head` 的 `pageTitle`，最终是 `<title>{seo_title} · 智序</title>` | 用文章 `title`，即现在的 `<title>{title} · 智序</title>` |
| `seo_keywords` | `<meta name="keywords" content="...">` | 不输出该标签。不用 `SmartService.keywords` 顶上，那些是文末搜索词，不是 SEO |
| `seo_desc` | `<meta name="description" content="...">` | 不输出该标签。不用摘要顶上，避免和正文摘要混成一套规则 |

`templates/front/article.html` 的 `<h1>` 继续绑定 `article.title`。三份 SEO 字段都不替换它。全站 SEO（站点关键词、默认描述）不在本批，设置页现有字段里也没有这些项。

列表页：

- 增加状态下拉：全部 / 草稿 / 已发布 / 已下线。
- 【复核】去掉「设为公开 / 设为私密」。行内改为「发布」「下线」「存草稿」，都进 `applyStatus`，确认文案写清文章标题。
- 已发布再点发布、已下线再点下线，提示当前已是该状态，不重复写库。

详情页：

- 目录来自正文里的 Markdown 标题，没有标题则不显示目录。
- 上一篇、下一篇只在已发布文章之间按 `create_time`、`aid` 取相邻。没有则不显示该链接。
- 「接着读」继续用 `related`。
- 分享只复制当前文章 URL，不接第三方分享脚本。
- 代码块加「复制」；图片点击在页内预览，关闭不离开文章。

### 7. 业务规则与异常

- 发布仍校验标题、正文、栏目，沿用 `ArticleService.save`。
- 草稿也要有标题和正文，避免出现无法再编辑的空记录。若希望草稿允许空正文，见待确认项。
- 下线和草稿不增加阅读量，沿用现在 `isOpen!='1'` 不计数的逻辑。
- 评论和点赞仍要求 `isOpen='1'`。下线文章不能新评论。
- 管理员看草稿/下线详情时，页面上要标出状态，避免误以为前台也能看见。

### 8. 权限

状态变更、SEO、置顶都在 `/admin/articles/**`，已有拦截器覆盖。前台读接口不扩大权限。

### 9. 数据兼容风险

- 最大风险是用新状态覆盖 `isOpen`，导致现在公开的文章从前台消失。本方案禁止这样做。
- 加列后、回填前，若默认值把私密文章写成「已发布」，会和 `isOpen=0` 不一致。回填脚本必须先于业务发版，顺序见数据库计划。
- 【复核】若 `POST /admin/articles/open` 或 `updateOpen` 还在，并且只写 `isOpen`，会和 `publish_status` 拆开。发版时这两处必须已经改走 `applyStatus`。

### 10. 测试用例

1. 现有 `isOpen=1` 的文章，迁移后仍出现在首页和 `/articles`。
2. 现有 `isOpen=0` 的文章，迁移后前台仍 404/业务错误，管理员仍能打开。
3. 新草稿不出现在列表、最新、热门、相关文章。
4. 草稿改为已发布后，前台可见，阅读量开始增加。
5. 已发布改为下线后，前台不可见，评论和点赞被拒绝。
6. 置顶文章排在同列表未置顶之前，热门排序仍然有效。
7. 填写 SEO 标题后，浏览器标题变化，`h1` 仍是原标题。SEO 关键词、描述为空时页面上没有对应 meta；填写后才有。首页和其他列表页的 `<title>` 不变。
8. 无标题的正文不显示目录；有标题的正文目录能跳到对应位置。
9. 第一篇没有上一篇，最后一篇没有下一篇。
10. 代码复制和图片预览不执行文章里的脚本（渲染器保持转义）。

### 11. 验收标准

- 【复核】三种状态和 `isOpen` 成对写入；新建文章不靠列默认值补状态；列表不再保留只改 `isOpen` 的公开/私密按钮。
- 旧文章前台可见性与迁移前一致。
- 目录、上下篇、分享、复制、预览可用。
- 相关文章仍来自 `ArticleService.related`，没有另一套推荐数据。

### 12. 待确认

1. 现有 `isOpen=0` 回填成「已下线」还是「草稿」。建议已下线：这些文章已经存在，管理员现在就能打开，更像撤下而不是未写完。未确认前不要执行回填。
2. 草稿是否允许空正文。建议不允许，与现有 `save` 一致。
3. 前台打开草稿和下线时，是否仍共用「这篇文章还没有公开」。建议先共用，避免新文案暴露内部状态。

---

## 第二批：分类与标签完善

### 1. 功能目标

栏目可改名、改描述、改排序、改图标。标签可改名、改排序。删除确认继续说明真实影响。不建立文章—标签 ID 表。

### 2. 当前实现情况

- 表：`t_category(cid, cname)`，`t_tag(tid, tname, cid)`。
- `ContentService.saveCategory`、`saveTag` 只插入。同名栏目拒绝。标签不检查同栏目重名。
- `deleteCategory`：栏目下有文章则拒绝，否则先删该栏目的标签再删栏目。
- `deleteTag`：只按 `tid` 删除，不改 `t_article.tagNames`。
- 文章里的标签是逗号文本。保存时 `ArticleService.normalizeTags` 用 `Fmt.tags` 拆分后再拼回 `", "`。
- 编辑页按栏目显示标签，勾选值是 `tname` 不是 `tid`（`article-form.html`）。其他栏目的勾选会被禁用，避免提交。
- 前台筛选是 `tagNames LIKE`，不是 ID 关联（`publicWrapper`）。
- 页面：`templates/admin/categories.html`。没有编辑弹窗。

### 3. 数据库

`t_category` 增加 `cdesc`、`cicon`、`sort`。`t_tag` 增加 `sort`。不新增表，不新增 `blog_article_tag`。

图标建议用短文本（一个字或短词），不上传图片。图片要等第五批文件管理，避免栏目图标变成无人引用的文件。

### 4. 代码与页面

| 类型 | 路径 | 动作 |
|---|---|---|
| Controller | `src/main/java/com/blog/web/AdminContentController.java` | 修改。增加栏目更新、标签更新。仍用 POST |
| Service | `src/main/java/com/blog/service/ContentService.java` | 修改。更新、重名、排序 |
| Mapper | `CategoryMapper`、`TagMapper` | 复用 |
| Entity | `Category.java`、`Tag.java` | 修改 |
| 页面 | `templates/admin/categories.html` | 修改 |
| 样式 | `src/main/resources/static/css/admin.css` | 修改。只加本页需要的样式 |

【复核】文章编辑页仍按 `tname` 勾选当前栏目、且仍在 `t_tag` 里的标签。保存时不能只用勾选结果覆盖 `tagNames`。见下一节。

### 5. 页面交互

左栏栏目：

- 行上增加「编辑」。弹窗含名称、描述、图标、排序。
- 删除仍用现有确认。有文章时服务端继续拒绝，文案保持「栏目下还有文章，不能删除」。
- 列表按 `sort`、`cid` 排序。

右栏标签：

- 增加「编辑」。可改名称和排序。本批不提供把标签挪到别的栏目，避免和文章文本栏目不一致。
- 删除确认必须写明：标签名称、所属栏目、删除不可恢复、文章里已经保存的标签文字不会删除。
- 提交中禁用按钮，防止重复 POST。

### 6. 业务规则与异常

- 栏目名、标签名仍限制 1–30 字。同名栏目拒绝。
- 同一栏目下标签名不能重复。不同栏目允许同名，因为筛选是按文字，本来就会一起命中。
- 排序为空时按 0。
- 描述、图标可空。

### 7. 标签改名与历史文本

【复核】采用方案 A：只改 `t_tag.tname`，不更新任何文章的 `tagNames`。不提供方案 B。删除标签同样不改文章文本。不要用 `LIKE` 做替换，`Java` 会误伤 `JavaScript`。

改名弹窗写明：文章里已经保存的标签文字不会自动更换。

【复核】编辑回显与保存。当前 `article-form.html` 只渲染 `t_tag` 里的名称。`AdminArticleController.save` 只用本次勾选的 `tags` 拼 `tagNames`；一个都没勾时甚至不调用 `setTagNames`，随后 `ArticleService.save` 仍执行 `setTagNames(normalizeTags(...))`，空值会被写成空字符串。因此，文章里有、标签表里已经没有的名称（改名后的旧文字、已删除的标签）会在只改标题等其他字段时被清掉。

本批保存规则：

- 可编辑集合：当前所选栏目下、`t_tag` 里现存的名称。勾选变化只影响这一集合。
- 保留集合：该文章原 `tagNames` 经 `Fmt.tags` 拆开后，不在全表 `t_tag.tname` 中的片段。
- 新的 `tagNames` = 本次勾选 ∩ 可编辑集合，再加上保留集合。更新时以数据库里的原文为准取保留集合，不能以空表单为准。
- 编辑页把保留集合显示出来，标明这些文字不在当前标签表，取消勾选也不会删它们。本批不提供「从文章里删掉历史文字」的按钮，以免扩大范围。
- 仍在 `t_tag` 中、但不属于当前栏目的名称，继续沿用现在的禁用勾选、不随本次提交。它们不在「标签表已不存在」的范围内，本批不额外保留。这是已知限制。

【复核】改名后的已知限制：前台 `publicWrapper` 的 `tagNames LIKE`、以及 `ArticleService.related` 里的 `overlap`，都按文章里的旧文字匹配。旧文章不会出现在新标签名的筛选和相关结果里，直到有人在编辑页勾选新名称并保存。相关文章不会自动改挂到新名字上。

### 8. 权限

沿用 `/admin/categories` 与 `/admin/tags` 的管理员拦截。

### 9. 数据兼容风险

- 现有栏目、标签没有 `sort`。默认 0 后，顺序应仍按 `cid`/`tid`，不能打乱现有顺序。
- 【复核】改名不回写 `tagNames`。若保存文章时漏做「保留集合」，历史标签会被清空。这是本批要防的回归。
- 栏目改名不影响文章，文章只存 `cid`。

### 10. 测试用例

1. 编辑栏目名称后，文章列表栏目名变化，文章 `cid` 不变。
2. 重名栏目保存失败，原记录不变。
3. 有文章的栏目不能删除，标签还在。
4. 无文章的栏目删除后，其标签一并删除。
5. 【复核】改名后旧文章 `tagNames` 不变。用新名称筛选和相关文章都匹配不到这段旧文字；用旧名称仍能匹配。
6. 删除标签后，文章 `tagNames` 原文还在，前台仍可能按旧文字搜到。
7. 【复核】文章含有标签表里已经没有的文字时，只改标题再保存，这段文字还在；同时取消一个仍存在于当前栏目的标签，则那个标签被去掉。
8. 编辑文章时，当前栏目里仍存在的标签才作为可勾选项提交。

### 11. 验收标准

- 栏目、标签可以改，不靠再插入一条冒充编辑。
- 删除确认文字与真实行为一致。
- 【复核】改名和删除都不批量改写 `tagNames`。保存文章不会清掉标签表中已不存在的历史文字。

### 12. 待确认

栏目图标是短文本，还是图片。建议短文本。标签改名已定为方案 A，不再待选。

---

## 第三批：友链完善

### 1. 功能目标

正式友链增加 Logo 和启用状态。前台只显示启用的。访客可以申请，管理员审核，通过后生成一条正式友链。

### 2. 当前实现情况

- `t_link`：`id`、`site_name`(15)、`site_url`(45)、`site_desc`(45)、`sort`。没有 Logo，没有状态。
- `ContentService.links` 返回全部，按 `sort`、`id`。`saveLink` 校验 http(s) 和长度。
- 前台 `templates/front/links.html` 用地址去拉对方 `/favicon.ico`，失败则显示首字。这不是库存 Logo。
- 后台 `templates/admin/links.html` 可新增、编辑、删除。没有申请。

### 3. 数据库

`t_link` 增加 `site_logo`、`enabled`。现有行 `enabled` 回填为启用，避免前台突然变空。

新增 `t_link_apply`。不使用文档里的 `blog_link_apply` 这个名字。

`site_url` 维持 45。超长地址现在就会被拒绝，本批不放宽，避免悄悄改校验。

### 4. 代码与页面

| 类型 | 路径 | 动作 |
|---|---|---|
| Controller | `AdminContentController.java` | 修改。友链保存增加 Logo 和启用；新增申请列表、通过、拒绝、删除 |
| Controller | `FrontController.java` | 修改。`/links` 只查启用；新增 `GET/POST /links/apply` |
| Service | `ContentService.java` | 修改 |
| Mapper | `LinkMapper.java` | 复用 |
| Mapper | 新建 `src/main/java/com/blog/mapper/LinkApplyMapper.java` | 新增 |
| Entity | `Link.java` | 修改 |
| Entity | 新建 `src/main/java/com/blog/entity/LinkApply.java` | 新增 |
| 页面 | `templates/admin/links.html` | 修改 |
| 页面 | 新建 `templates/admin/link-applies.html` | 新增。仍走 `AdminContentController` |
| 页面 | `templates/front/links.html` | 修改。有 Logo 用 Logo，没有再退回 favicon / 首字 |
| 页面 | 新建 `templates/front/link-apply.html` | 新增 |
| 菜单 | `templates/fragments/admin.html` | 修改。增加「友链申请」入口 |

### 5. 页面交互

后台友链表：名称、Logo、地址、描述、排序、状态、操作。启用/停用是 POST，成功后 flash。删除确认写明站点名。

后台申请表：名称、地址、Logo、描述、申请人、时间、状态。通过、拒绝、删除都要确认。通过后面显示「已生成友链」或失败原因。

前台列表：只显示 `enabled=1`。页上有「申请友链」，说明需要审核后才会出现。

申请表：名称、地址、描述必填；Logo 可选，沿用 `FileStorage.save`（jpg/jpeg/png/gif/webp，5MB）。未登录用填写的申请人名称；已登录同时记下 `uid` 和昵称。

### 6. 业务规则与异常

- 地址规则与 `saveLink` 相同。申请提交和后台正式保存必须调用同一个校验方法：只接受 `http://` 或 `https://`，名称、地址、描述长度与现有 `saveLink` 一致（15 / 45 / 45）。不能在申请入口放宽。
- 申请状态：`0` 待审，`1` 通过，`2` 拒绝。
- 同一地址已有待审申请时，拒绝再提交。
- 【复核】通过前先读申请。状态不是 `0` 则直接失败，文案说明已经处理，不插入友链。
- 【复核】更新申请必须带状态条件：`WHERE id=? AND status='0'`。影响行数不是 1 就回滚并提示「申请已处理」。拒绝同样使用这个条件。两个管理员同时点通过时，只有一行更新成功。
- 【复核】通过且更新成功后，在同一事务里插入 `t_link`（`enabled=1`，`sort=0`）。若该地址已经有正式友链，不插入、不更新旧友链的名称、描述、Logo 和排序，申请保持待审（事务回滚），提示「友链已存在」。
- 拒绝不创建友链。
- 停用的友链前台不可见，后台仍在。

### 7. 权限

审核只在 `/admin/**`。前台申请对访客开放，和留言类似，要做长度校验。本批不加新的频率限制框架；若要和留言一样防刷，见待确认。

### 8. 数据兼容风险

- `enabled` 默认必须是启用，否则现有淘宝、百度这些友链会从前台消失。
- Logo 空时保留现在的 favicon 逻辑，避免全部变成首字。
- 申请通过和插入友链要在同一事务。插入失败或地址已存在时，申请状态条件更新一并回滚，申请仍为待审。
- 【复核】并发下第二次审核读到的影响行数为 0，不能再插一条友链。

### 9. 测试用例

1. 迁移后原友链仍显示。
2. 停用后前台消失，后台还在，再启用后恢复。
3. 有 Logo 的显示 Logo；没有的仍能显示 favicon 或首字。
4. 非法地址、超长名称不能提交。
5. 重复待审申请被拒绝。
6. 审核通过出现在前台；拒绝不出现。
7. 【复核】地址已存在时不产生第二条，也不改旧友链内容；申请仍是待审，页面提示已存在。
8. 【复核】同一条待审申请被连续通过两次，第二次失败，正式友链只有一条。
9. 申请页提交超长地址，与后台保存友链得到同样的拒绝。
10. 非管理员不能打开申请审核页。

### 10. 验收标准

- 【复核】审核更新带 `status='0'` 条件并检查影响行数；已有正式友链不被覆盖。
- 申请到正式友链的路径可走通，失败有 flash，没有半截数据。

### 11. 待确认

申请是否要验证码。建议要，复用现有 `/captcha`，避免被刷。地址已存在时不覆盖正式友链，这项已定。

---

## 第四批：控制台完善

### 1. 功能目标

总览增加真实待办和快捷入口，以及按栏目的公开文章数。没有访问日志，不做访问趋势图。

### 2. 当前实现情况

`AdminSystemController.dashboard` 已输出文章数、公开文章数、评论数、留言数、友链数、访问量（`ArticleMapper.sumVisits`，是阅读次数合计）、Java 与操作系统。

模板：`templates/admin/index.html`。

待审留言计数方法已在 `ContentService`（`status=0`）。评论待审是 `t_comment.status=0`。评论没有单独的「拒绝」状态，隐藏也是写回 `0`（`updateCommentStatus`）。因此「待审核评论」目前等于状态为 0 的评论，包含从未审核和被隐藏的。

栏目文章数：`ArticleService.countByCategory` 已按公开文章统计。`ContentService.categories` 也会填 `articleCount`。

友链申请数依赖第三批的表。第三批未做时，总览不显示这一项，不写死 0 冒充。

### 3. 数据库

无。不建访问日志表，不画 7 日、30 日图。

### 4. 代码与页面

| 类型 | 路径 | 动作 |
|---|---|---|
| Controller | `AdminSystemController.java` | 修改 |
| Service | `ContentService.java`、`ArticleService.java` | 复用现有计数。评论待审若没有公开方法，只在 `ContentService` 加一个 count |
| Mapper | 复用 | |
| Entity | 无 | |
| 页面 | `templates/admin/index.html` | 修改 |
| 样式 | `admin.css` | 修改。沿用现有 `.stat`、`.panel` |

快捷入口只链到已有地址：

- `/admin/articles/edit` 写文章
- `/admin/notices` 公告
- `/admin/diaries` 日记
- `/admin/messages?status=0` 待审留言
- `/admin/comments?status=0` 待审评论

### 5. 页面交互

统计卡片下增加「待处理」：待审留言、待审评论、待审友链申请（第三批完成后）。数字为 0 也显示 0。点击进入对应筛选列表。

快捷操作是四个链接，不是新功能按钮。

栏目分布用现有公开文章计数，按栏目名和数量列出。没有文章的栏目显示 0。不用图表库。

### 6. 业务规则与异常

- 计数查询失败时，总览沿用首页的做法：该项显示失败提示，不用 0 伪装成功。
- 分布只统计 `isOpen=1`。私密、草稿、下线不进分布，除非第一批后的产品要求改口径。见待确认。

### 7. 权限

仅管理员可打开 `/admin`。

### 8. 数据兼容风险

低。只读。不要把 `sumVisits` 改名为「独立访客」。

### 9. 测试用例

1. 待审留言数与 `/admin/messages?status=0` 的总数一致。
2. 评论 `status=0` 的条数与总览一致。
3. 第三批未上线时，页面没有友链申请数。
4. 上线后，待审申请数与申请列表一致。
5. 栏目数量之和等于公开文章数（每篇文章一个栏目；`cid` 为空的不计入栏目，若有则单独可见）。
6. 快捷链接能打开，不会 404。

### 10. 验收标准

- 没有假曲线、假访问量。
- 待办数字能点进现有列表。

### 11. 待确认

评论「隐藏」和「待审核」共用 `status=0`。总览要不要把它们都算进待审核。建议算进去，并在卡片上写「待处理评论」，不要写成「新提交」。若要分开，需要给评论加拒绝状态，那是额外改动，不属于本批默认范围。

---

## 第五批：系统管理扩展

### 1. 功能目标

记录关键写操作；列出已上传图片并在删除前检查引用；导航可配置，但只能指向系统允许的站内路径。

### 2. 当前实现情况

操作日志：没有表，没有写入点。登录日志已在 `t_login_log` 和 `templates/admin/logs.html`，本批不改登录日志的字段含义。

文件：`FileStorage` 把图片存到 `blog.upload-dir`（相对运行目录的 `uploads`），返回 `/uploads/文件名`。`WebConfig` 映射 `/uploads/**`。限制 jpg/jpeg/png/gif/webp。大小来自 `spring.servlet.multipart` 5MB / 6MB。没有文件表。已知引用：

- `t_article.logo`
- `t_user.img`
- 第三批若做了，还有 `t_link.site_logo`、`t_link_apply.site_logo`

日记表没有图片列。设置页没有站点 Logo 文件。

导航：写死在 `templates/fragments/front.html` 的 `navbar`。后台侧栏是另一套，不交给前台导航配置。

### 3. 数据库

新增 `t_operate_log`、`t_nav`。

文件管理建议先不建 `t_file`。磁盘目录已经是事实来源，再建表会有两套名单。删除前用 SQL 查引用列。本版不新增 `t_file`。

### 4. 代码与页面

| 类型 | 路径 | 动作 |
|---|---|---|
| Controller | `AdminSystemController.java` | 修改。日志列表、文件列表与删除、导航保存 |
| Service | 新建 `src/main/java/com/blog/service/OperateLogService.java` | 新增。只负责写入和分页，不复制业务 |
| Service | `FileStorage.java` | 修改。列出文件、按名删除；删除前由 Controller/现有 Service 做引用检查 |
| Service | 各现有写操作所在的 Service | 修改。成功后调用日志。失败不记密码、验证码 |
| Mapper | 新建 `OperateLogMapper.java`、`NavMapper.java` | 新增 |
| Entity | 新建 `OperateLog.java`、`Nav.java` | 新增 |
| 页面 | 新建 `templates/admin/operate-logs.html`、`files.html`、`nav.html` | 新增 |
| 菜单 | `templates/fragments/admin.html` | 修改 |
| 前台 | `templates/fragments/front.html` | 修改。`t_nav` 没有任何行时用现在的硬编码菜单；有行时只渲染 `enabled=1`，全部停用则顶栏没有这些入口 |

不把导航配置做成可填任意 URL。

### 5. 页面交互

操作日志：时间、用户、模块、动作、结果、摘要。只读。摘要不出现密码、验证码。

文件：文件名、大小、时间、是否被引用、引用位置（文章封面或头像等）。删除前确认。被引用则拒绝，并写明哪篇文章或哪个用户。

导航：名称、站内路径、排序、启用。路径用下拉，而不是自由文本。候选项只包括现在的前台路径：`/`、`/articles`、`/messages`、`/diaries`、`/links`、`/about`。保存后前台顶栏按排序显示启用项。

【复核】`t_nav` 零行时，前台继续用 `navbar` 里现有的六个链接。表里已经有行、但全部 `enabled` 不是启用时，只显示空的导航位，不自动换回硬编码菜单。允许把已有项全部停用；不要为了「至少一项」去拦截停用。

### 6. 业务规则与异常

- 【复核】删除前把用户提交的路径规范成绝对路径，必须落在 `blog.upload-dir`（默认 `uploads`）目录之内。含 `..`、绝对路径跳出该目录、或解析后不在该目录下的，拒绝并提示路径不合法，不删除任何文件。
- 【复核】只删除该目录内的普通文件。目录、符号链接以及目录外对象都拒绝，并提示不能删除。
- 【复核】文件不存在时提示文件不存在，不把它当成删除成功。
- 【复核】删除前检查已知引用。清单固定为：`t_article.logo`、`t_user.img`；第三批已上线时再加 `t_link.site_logo`、`t_link_apply.site_logo`。任一列等于该 `/uploads/文件名` 则拒绝，提示引用位置（文章标题或 id、用户名或 uid、友链名称）。日记和设置没有图片列，不纳入清单。不建 `t_file`。
- 【复核】操作日志按结果分开记，且都在业务事务成功或失败已经确定之后写，日志失败不能把业务改回失败或成功：
  - 成功：业务方法正常返回后记一条，`success=1`，摘要是模块和动作。
  - 业务失败：捕获现有 `BizException` 后记一条，`success=0`，摘要只用异常文案，不记堆栈。
  - 系统异常：未预期异常记一条，`success=0`，摘要只记异常类名，不记堆栈和 SQL。然后继续把异常抛出，不吞掉。
- 【复核】日志禁止写入：密码、验证码、完整请求体、`multipart` 文件内容，以及参数名包含 password、captcha 的值。改密码只记成功或失败。
- 操作日志记录范围：文章保存/删除/状态、栏目与标签增删改、评论和留言审核删除、友链及申请审核、用户状态与角色、设置、密码修改、文件删除、导航保存。
- 【复核】`t_login_log` 和 `templates/admin/logs.html` 保持现在的登录用途，不改字段，不把登录记录并进 `t_operate_log`。
- 导航路径必须命中上面的白名单，否则拒绝。空表回退硬编码；有数据但全部停用时不回退。

### 7. 权限

全部在 `/admin/**`。

### 8. 数据兼容风险

- 【复核】导航表没有任何行时必须回退到硬编码。表里有行但全部停用时，不能再回退，否则停用会失效。
- 文件列表如果漏查某个引用列，会删掉仍在使用的封面。实施时把引用列写成明确清单，第三批未上线就不查 `site_logo`。
- 磁盘上已有文件没有「上传人」。列表里上传人留空，不要编造。不新增 `t_file` 来补这个信息。

### 9. 测试用例

1. 文章保存成功产生一条成功日志。校验失败产生一条业务失败日志。未预期异常产生一条只含异常类名的失败日志。三条都不含密码、验证码和请求体。
2. 改密码失败或成功都不记录密码明文。登录仍只进 `t_login_log`。
3. 被文章封面使用的文件不能删，提示文章标题或 id。文件不存在、路径含 `..` 时各有明确提示，磁盘无变化。
4. 未被引用的普通图片可以删，`/uploads/` 下文件消失。目录和目录外路径删不掉。
5. 导航只保存白名单路径。提交站外地址被拒绝。
6. 【复核】表中已有导航时，全部停用后前台不再显示这几项，也不回到硬编码六个入口。
7. 【复核】导航表无数据时，前台仍是现在的六个入口。
8. 非管理员不能打开这三个页面。

### 10. 验收标准

- 日志、文件、导航都有真实数据来源。
- 删文件不会误删仍被引用的图片。
- 导航不能配置成任意跳转。

### 11. 待确认

操作日志从第五批才开始记，第一到第四批上线期间的操作没有日志。若希望更早有日志，需要把日志表提前，再在前几批里调用。不建文件表、不允许站外导航、全部停用时不回退硬编码，这三项已定。

---

## 实施时不要做的事

- 不把表改名为 `sys_*` / `blog_*`。
- 不把密码从 MD5 换成新算法（会影响全部账号，单独立项）。
- 不把评论隐藏和待审核拆成新状态，除非第四批待确认项明确要求。
- 不引入图表库、Markdown 编辑器框架和权限框架。
- 不用假数据填统计。

## 仍待产品决定

复核后下面几项仍不代为决定：

1. 现有 `isOpen=0` 回填成已下线还是草稿。建议已下线。未确认前不执行回填。
2. 草稿是否允许空正文。建议不允许。
3. 前台打开草稿和下线时，是否仍共用「这篇文章还没有公开」。建议先共用。
4. 栏目图标用短文本还是图片。建议短文本。
5. 友链申请是否要验证码。建议复用现有 `/captcha`。
6. 控制台把评论 `status=0` 标成「待处理评论」还是「新提交」。建议前者。不在本批拆隐藏和待审核。
7. 栏目文章分布是否继续只统计 `isOpen=1`。建议继续只统计公开。
8. 操作日志是否从第五批才开始。建议仍从第五批开始；提前则要把建表挪到第一批之前。
