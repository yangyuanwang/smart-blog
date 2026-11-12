# smart-blog 工程约束（AI 与维护者必读）

> 本文件是仓库级工程规则入口。完整规范源头在 `D:\Project\knowin-workspace\harness\rules\`（只借鉴其规范，不套用其治理）；本文件提炼适用于本仓库的强制项。

## 首要约定

1. 任何任务先读本文件；延续会话或规则变化时重新读取，不依赖旧聊天记忆。
2. 设计、架构、接口、数据模型、页面清单发生变化时，**同一次任务内**同步 `docs/需求规格说明书-v2.md` 与 `docs/系统设计文档.md` 对应章节，最后核对「实现变化 → 需求 → 设计 → 验证证据」。未同步不得宣称完成。
3. 只做当前任务授权范围内的变更；构建通过 ≠ 验收，界面变更必须浏览器实测窄屏与明暗主题后才算完成；不凭空填写通过结果。
4. 未经用户要求：不 git push、不创建远程仓库、不部署服务器、不发布。本地 commit 遵循 Conventional Commits（feat/fix/refactor/docs/test/chore(scope): 中文描述）。

## 目录与职责

```
backend/    Java 工程（Spring Boot 3.5 / Java 21）。源码+测试+Flyway 迁移。
frontend/   Nuxt3 + TypeScript 工程。页面/组件/布局。
docker/     compose 与 Dockerfile（dev 起依赖，full 起全栈）。
docs/       需求规格说明书-v2.md、系统设计文档.md、历史文档归档。不放代码。
```

- 业务逻辑只在 backend/frontend 源码中；文档只放 docs/。
- 真实密钥永不入库入文档：全部走环境变量，参考 `.env.example`。

## Java 后端编码强制规范

完整基线：knowin-workspace `harness/rules/project-engineering.md` 的「Java 后端编码强制规范」（阿里巴巴《Java 开发手册》黄山版 + 项目补充）。所有新增和修改的 `**/*.java` 必须遵守，历史代码「修改即整理」：

- 4 空格缩进；K&R 大括号（`{` 不另起行，`}` 独占一行，else/catch/finally 另起行）；所有 if 带大括号；每行 ≤120 字符；一行一语句一字段；运算符与关键字后加空格。
- 命名：类大驼峰、方法/变量小驼峰、常量全大写下划线；Controller/Service 统一后缀；禁单字母业务参数、禁拼音。
- **所有 public 方法必须中文 JavaDoc**（功能、逐个 @param、@return、真实 @throws 不虚构）；关键业务分支/权限校验/特殊枚举 case 必须中文单行注释说明原因；禁复述代码式注释。
- 依赖构造器注入，禁字段 `@Autowired`；不可变成员 `private final`。
- 字符串比较常量前置（`"ACTIVE".equals(status)`）；业务魔术字符串提取常量/枚举。
- 业务异常统一 `com.blog.common.BizException`（本项目实际异常，禁裸 RuntimeException，不吞异常）。
- Controller：注解独占一行；返回统一 `Result<T>` 包装禁裸类型；权限/校验抽独立 private 方法并写中文 JavaDoc；内部接口必须鉴权校验白名单。
- import 分组：`java.*` → `jakarta.*` → `org.springframework.*` → 其他第三方 → 本项目；组间空行；禁 `*` 通配；删未使用 import。
- 请求对象用 `jakarta.servlet.http.HttpServletRequest`（SB3 命名空间）。
- 交付前逐项自查上述各项；运行 `mvn -q compile test` 验证。

## 前端工程规则（Nuxt3）

- 沿用既有技术栈与组件体系（Nuxt3 + TS + Tailwind + Pinia + @vueuse/motion + Shiki）。
- 复用已有组件与行为原语；组件间通过明确 props/事件交互，公共组件不隐式承担鉴权或请求。
- **组件七态强制**：键盘可达（Tab/Enter/Esc）、焦点回返、空态、加载骨架、错误反馈、窄屏适配、明暗主题；动效包 `prefers-reduced-motion` 判断。
- 公开客户端变量不得含密钥；API 统一走 `composables/useApi.ts`。
- 验证：`pnpm typecheck`、`pnpm lint`、`pnpm build` 通过 + 浏览器实测相关页面（含窄屏与明暗主题）。

## 数据库

- 结构变更只走 `backend/src/main/db/migration/`（Flyway，V{n}__描述.sql，含回填与校验），禁改 `spring.sql.init`、禁启动期 ALTER、禁手工改已发布迁移文件。
- 命名对齐现状（t_ 前缀、snake_case）；时间一律 DATETIME。

## 常用命令

```bash
# 依赖（首次/日常）
docker compose -f docker/docker-compose.yml --profile dev up -d      # MySQL(33061)+Redis(36379)
# 后端（需 JDK21，JAVA_HOME 指向 D:\dev-tools\jdk-21.0.13+9）
cd backend && mvn spring-boot:run                           # :8080
# 前端
cd frontend && pnpm install && pnpm dev                     # :3000，/api 代理 8080
# 全栈联调（四容器：mysql/redis/backend/frontend）
docker compose -f docker/docker-compose.yml --profile full up -d --build
# 质量检查
cd backend && mvn -q test ; cd ../frontend && pnpm lint && npx vue-tsc --noEmit
```

## 端口约定

| 服务 | 端口 |
|---|---|
| Nuxt（dev 与 SSR 容器） | 3000 |
| Spring Boot API | 8080 |
| MySQL | 33061（容器映射，本机 3306/33060 已被宿主 MySQL 占用） |
| Redis | 36379（容器映射） |
