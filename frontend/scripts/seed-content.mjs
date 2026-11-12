// seed-content.mjs —— 通过管理端 API 播种示例内容（可重复执行，用户可自行替换删除）
// 用法: node scripts/seed-content.mjs [baseUrl] [username] [password]
const base = process.argv[2] || 'http://localhost:8080'
const username = process.argv[3] || '\u5c0f\u5f3a' // admin nickname stored as XiaoQiang role=0
const password = process.argv[4] || '123'

async function api (path, options = {}) {
  const res = await fetch(base + path, options)
  return res.json()
}

const login = await api('/api/v1/auth/admin-login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username, password })
})
if (login.code !== 0) {
  console.error('login failed:', login.message)
  process.exit(1)
}
const token = login.data.accessToken
console.log('logged in as', login.data.user.username)

const headers = {
  'Content-Type': 'application/json',
  Authorization: 'Bearer ' + token
}

const articles = [
  {
    title: 'smart-blog v2 改造记：从课程设计到全栈工程',
    tagNames: '改造记, 全栈',
    publishStatus: '1',
    content: [
      '# 为什么要改造',
      '',
      '旧版 smart-blog 是一套 Spring Boot 2.7 + Java 8 + Thymeleaf 的课程设计级项目，功能闭环但技术栈已经 EOL。这次改造的目标很直接：**把它变成一个可以拿出来讲十分钟的全栈工程**。',
      '',
      '# 架构变化',
      '',
      '- 前端从 27 个 Thymeleaf 模板迁移到 **Nuxt 3 SSR**（TypeScript + Tailwind），文章页服务端直出，SEO 与首屏兼得',
      '- 后端升级到 **Spring Boot 3.5 / Java 21**，新增 REST API 层 `/api/v1`，Spring Security + JWT 双令牌',
      '- 数据库引入 **Flyway** 版本化迁移：CHAR(19) 时间列转 DATETIME、标签逗号文本转关联表、补索引',
      '- 新增 **Redis**：登录限流、留言频控、浏览量缓冲落库（每 5 分钟批量 GETDEL）',
      '- AI 摘要：文章发布后异步调用 OpenAI 兼容接口生成 100~150 字摘要，未配置 Key 时优雅降级',
      '',
      '# 过程中最有价值的三个决定',
      '',
      '## 1. 旧数据不丢',
      '',
      '改造不是重写。旧库的文章、标签、用户通过 dump + Flyway 增量迁移原样保留，历史 MD5 口令在用户首次登录时自动升级为 BCrypt —— 平滑过渡，无感升级。',
      '',
      '## 2. 测试抓出真漏洞',
      '',
      '集成测试（Testcontainers 真实 MySQL + Redis）写了一条权限边界用例，结果抓出 `GET /api/v1/**` 的宽放行规则覆盖了管理端接口的顺序问题。Spring Security 的规则是**先匹配先生效**，`/api/v1/admin/**` 必须声明在宽泛放行之前。这类细节靠 review 很难发现，靠测试可以。',
      '',
      '## 3. 读写分离的边界',
      '',
      '文章内容走 Git + 静态化思路的动态版本：读路径构建期/缓存层消化，写路径只留最小的事务。浏览量先写 Redis 定时落库，把高频 UPDATE 从主链路上摘掉。',
      '',
      '# 后续计划',
      '',
      '- Docker Compose 一键起全栈（已就绪）',
      '- GitHub Actions CI/CD 与服务器部署（本地验收后）',
      '- Lighthouse 优化到 95+'
    ].join('\n')
  },
  {
    title: 'Spring Boot 3 升级实战：javax 到 jakarta 的连锁反应',
    tagNames: 'Java, Spring Boot',
    publishStatus: '1',
    content: [
      '# 升级清单比想象中长',
      '',
      '把 Spring Boot 2.7.18 / Java 8 升到 3.5.7 / Java 21，`pom.xml` 十分钟就改完了，真正的坑在下游：',
      '',
      '- `javax.servlet.*` -> `jakarta.servlet.*`：拦截器、全局异常处理、上传工具全部要换 import',
      '- **MyBatis-Plus 3.5.9+ 把分页插件拆包**：`PaginationInnerInterceptor` 需要额外引入 `mybatis-plus-jsqlparser`，否则启动报找不到类',
      '- Lombok 注解处理：显式 `annotationProcessorPaths` 在部分版本组合下反而失效，靠 classpath 自动发现更稳',
      '- validation 异常体系换了包名，全局处理器要跟着改',
      '',
      '# Spring Security 6 的规则顺序',
      '',
      '`authorizeHttpRequests` 是**先匹配先生效**。把宽泛的 `GET /api/v1/**` 放行写在 `/api/v1/admin/**` 前面，管理端只读接口就会对匿名开放 —— 而且启动不报错、运行无告警。修法很朴素：窄规则在前，宽规则在后，再加一条集成测试锁死这个顺序。',
      '',
      '# 值得做的一次升级',
      '',
      'Java 21 的虚拟线程对这种 IO 密集的博客服务是免费的红利；SB3 的 RestClient 也比手拼 RestTemplate 干净得多。升级不是目的，但升级过程中被迫重读了一遍安全、缓存和事务代码，本身就是收益。'
    ].join('\n')
  },
  {
    title: 'MySQL 里把时间存成 CHAR(19) 的教训',
    tagNames: 'MySQL, 数据库',
    publishStatus: '1',
    content: [
      '# 现象',
      '',
      '旧库里所有时间字段都是 `CHAR(19)`（形如 `2026-11-12 23:38:43`）。排序、比较、按天统计全靠字符串字典序 —— 恰好 ISO 格式的字典序等于时间序，所以一直没出事，直到要按月聚合和加索引优化。',
      '',
      '# 修复',
      '',
      'Flyway 迁移里一条 `MODIFY` 就完成了转换：',
      '',
      '```sql',
      'ALTER TABLE t_article',
      '  MODIFY create_time DATETIME NULL;',
      '```',
      '',
      'MySQL 会把合法的 `yyyy-MM-dd[ HH:mm:ss]` 文本自动转成 DATETIME，存量数据无损。',
      '',
      '# 教训',
      '',
      '1. **类型是契约**。字典序碰巧等于时间序是运气不是设计，一旦有人存了 `2026-1-3`（没有前导零）就直接崩',
      '2. 迁移要选窗口：先加列、回填、校验行数、再切读、最后删旧列，直接 MODIFY 在小表可以接受',
      '3. 每张表的默认值统一 `CURRENT_TIMESTAMP`，别让应用层再自己拼时间字符串'
    ].join('\n')
  },
  {
    title: '给博客接入 AI 摘要：一次异步与降级的设计',
    tagNames: 'AI, 架构',
    publishStatus: '1',
    content: [
      '# 需求',
      '',
      '文章发布后自动生成 100~150 字的摘要卡，展示在正文前面。看起来是加一个 API 调用，真正要设计的是**失败路径**。',
      '',
      '# 三个设计决定',
      '',
      '## 1. 异步，且不阻塞发布',
      '',
      'AI 生成挂在 `@Async` 任务里，发布接口立即返回。生成失败只记日志，文章照常上线 —— 没有摘要的文章不是残缺的文章。',
      '',
      '## 2. 未配置 Key 是常态，不是异常',
      '',
      '`AiProperties.enabled()` 判空：base-url 和 api-key 任一缺失，直接跳过并打 INFO 日志。本地开发、CI 环境、新机器克隆下来第一秒就能跑，不需要先去申请 Key。',
      '',
      '## 3. 提示词压住输出',
      '',
      '系统提示词里写死：100~150 字、不带"本文"字样、不用 Markdown。超长输出在落库前再截断到 300 字符，双保险。',
      '',
      '# 结果',
      '',
      '发布一篇 3000 字文章，摘要约 3 秒生成；LLM 超时设 10 秒，超时即放弃。后台可以手动点"重新生成"。所有失败路径都不会把脏数据写进 `ai_summary`。'
    ].join('\n')
  }
]

const created = []
for (const article of articles) {
  const result = await api('/api/v1/admin/articles', {
    method: 'POST',
    headers,
    body: JSON.stringify({ ...article, cid: null })
  })
  if (result.code !== 0) {
    // 栏目必填：取第一个栏目 id
    const categories = await api('/api/v1/categories')
    const cid = categories.data?.[0]?.cid
    const retry = await api('/api/v1/admin/articles', {
      method: 'POST',
      headers,
      body: JSON.stringify({ ...article, cid })
    })
    if (retry.code !== 0) {
      console.error('article failed:', article.title, retry.message)
      continue
    }
    created.push(retry.data.aid)
    continue
  }
  created.push(result.data.aid)
}
console.log('articles created:', created.join(', '))

const project = {
  name: 'smart-blog',
  summary: '本站：Nuxt3 SSR + Spring Boot 3 + Redis + Docker 的个人博客全栈工程',
  techStack: 'Nuxt3, TypeScript, Tailwind, Spring Boot 3, Java 21, MyBatis-Plus, Redis, Flyway, Docker',
  repoUrl: '',
  demoUrl: '',
  featured: '1',
  sort: 0,
  status: '1',
  description: '从课程设计级 Thymeleaf 博客改造而来的全栈项目：JWT 双令牌认证、Flyway 数据迁移、Redis 缓冲与限流、AI 摘要异步生成、Testcontainers 集成测试。'
}
const projectResult = await api('/api/v1/admin/projects', {
  method: 'POST',
  headers,
  body: JSON.stringify(project)
})
console.log('project created:', projectResult.code === 0)
