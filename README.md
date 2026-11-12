# 智序 · SMART WORKSPACE

[![Java](https://img.shields.io/badge/Java-21-orange)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green)](https://spring.io/projects/spring-boot)
[![Nuxt](https://img.shields.io/badge/Nuxt-3-00DC82)](https://nuxt.com)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.9-3178C6)](https://www.typescriptlang.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D)](https://redis.io/)
[![Tests](https://img.shields.io/badge/tests-17%20passing-brightgreen)](backend/src/test)
[![License](https://img.shields.io/badge/license-MIT-blue)](#)

个人技术博客全栈工程：**Nuxt 3 SSR 前台 + Spring Boot 3 REST API + MySQL/Redis**，从课程设计级 Thymeleaf 博客逐步现代化改造而来（改造过程见站内文章《smart-blog v2 改造记》）。

## ✨ 亮点（面试可讲点）

| 方面 | 实现 |
|---|---|
| 认证 | Spring Security 6 + **JWT 双令牌**（access 2h / refresh 7d，Redis 白名单可吊销、轮换、改密即失效）；**历史 MD5 口令登录期自动升级 BCrypt** |
| 数据 | **Flyway** 版本化迁移（V1 基线 → V4 增强），存量数据无损转换（CHAR→DATETIME、标签逗号文本→多对多关联表） |
| 性能 | **浏览量先写 Redis 缓冲、定时批量落库**；标签/栏目计数 SQL 聚合替代全表内存计算；N+1 批量化治理 |
| 安全 | 登录失败锁定（5 次/15 分钟）、留言/点赞/友链申请 Redis 限流、XFF 仅信任配置内反代、上传魔数嗅探、secrets 全环境变量 |
| 内容工程 | Markdown 渲染（XSS 转义 + TOC 锚点）+ **Shiki 双主题高亮**；文章 OG 元数据 / JSON-LD 结构化数据 / sitemap / RSS |
| AI | 发布后异步调用 **OpenAI 兼容 LLM** 生成摘要与相关推荐，未配置 Key 时优雅降级，全失败路径不落脏数据 |
| 前端工程 | SSR/SSG 混合、暗色优先设计令牌、⌘K 命令面板（防抖搜索 + 全键盘操作）、组件七态（键盘/焦点/空/加载/错误/窄屏/明暗） |
| 质量 | **Testcontainers 真实 MySQL+Redis 集成测试**（17 个用例全绿）、vue-tsc 类型零错误、ESLint、Conventional Commits（Husky + commitlint） |

## 🏗 架构

```
浏览器 ──▶ Nuxt3 SSR (:3000) ──REST /api/v1 + JWT──▶ Spring Boot 3 (:8080)
              │  SSR 直出 / Shiki / 暗色                    │
              └─ routeRules 代理 /api /uploads              ├─▶ MySQL 8（Flyway 迁移）
                                                            └─▶ Redis 7（缓存/限流/计数缓冲）
```

```
smart-blog/
├── backend/    Java 工程（API/安全/迁移/测试）
├── frontend/   Nuxt3 工程（页面/组件/服务端路由/种子脚本）
├── docker/     compose（dev 只起依赖 / full 全栈）+ Dockerfile×2
└── docs/       需求规格说明书 v2、系统设计文档、历史文档
```

## 🚀 快速开始

**日常开发**（本机跑后端/前端，容器只起 MySQL+Redis）：

```bash
docker compose -f docker/docker-compose.yml --profile dev up -d   # MySQL:33061 Redis:36379
cd backend && JAVA_HOME=<jdk21路径> mvn spring-boot:run            # :8080
cd frontend && pnpm install && pnpm dev                            # :3000
```

**全栈联调**（一键起四个容器）：

```bash
cp docker/.env.example docker/.env
docker compose -f docker/docker-compose.yml --profile full up -d --build
# 打开 http://localhost:3000
```

**质量检查**：

```bash
cd backend && JAVA_HOME=<jdk21> mvn test          # 单测 + Testcontainers 集成测试
cd frontend && pnpm lint && npx vue-tsc --noEmit  # ESLint + 类型检查
```

## 📚 文档

- [需求规格说明书 v2](docs/需求规格说明书-v2.md) —— 模块清单、验收标准
- [系统设计文档](docs/系统设计文档.md) —— 架构、ER 图、API 契约、安全与缓存设计
- [AGENTS.md](AGENTS.md) —— 工程约束（AI 与维护者必读，Java 编码规范引用 knowin-workspace 强制规范）

## 🗺 路线

- [x] 后端现代化 + REST API + 安全重构 + 测试
- [x] Nuxt3 全部前台/后台页面 + SEO 全套
- [x] Docker 化全栈联调
- [ ] GitHub Actions CI/CD 与服务器部署（本地验收后另起）

## License

MIT
