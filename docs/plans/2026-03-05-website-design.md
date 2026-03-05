# JaguarClaw Official Website Design (V1)

## 1. 目标与范围

**目标**
- 在当前仓库内先完成一个独立官网项目原型，后续可整体迁移到外层目录单独维护。
- 网站同时覆盖三个核心场景：企业官网、使用文档、博客。
- 以企业级品牌质感和长期可演进架构为优先。

**范围（V1）**
- 官网首页（价值主张、能力、快速开始、设计理念导流）
- 文档区（结构化导航、目录锚点、代码内容渲染）
- 博客区（列表、详情、标签）
- 问题反馈页（GitHub Issues 主入口 + 站内表单预留）
- SEO 基础设施（sitemap/rss/og）

## 2. 技术决策

**选型**
- `Next.js (App Router) + TypeScript`
- `MDX + Contentlayer` 作为本地内容源
- 后续可叠加 ISR/SSR 能力，V1 先以稳定静态内容为主

**决策理由**
- 官网展示与文档/博客共存时，Next.js 在路由、SEO、性能和可扩展性上更平衡。
- Contentlayer 提供类型安全内容索引，便于持续沉淀文档与设计博客。

## 3. 信息架构

- `/` 官网首页
- `/docs` 与 `/docs/*` 文档区
- `/blog` 与 `/blog/[slug]` 博客区
- `/feedback` 反馈页
- `/404` 定制未找到页面

全局导航：`Product` `Docs` `Blog` `Feedback` `GitHub`

## 4. 视觉系统（Precision Industrial）

**设计方向**
- 关键词：克制、可信、工程化、可读性。
- 风格：精密工业感，不做花哨营销页风格。

**颜色（初版）**
- `Graphite #0E1116`
- `Steel #1E2633`
- `Signal Cyan #00A3A3`
- `Paper #F7F9FC`

**字体**
- 标题：`Sora`
- 正文/UI：`IBM Plex Sans`
- 等宽：`JetBrains Mono`
- 中文 fallback：`Noto Sans SC`

**动效策略**
- 首屏分层入场
- 卡片 hover 状态
- 文档 TOC 高亮
- 时长控制在 `160ms-320ms`

## 5. 内容模型

### Doc
- `title`
- `slug`
- `summary`
- `category`
- `order`
- `updatedAt`
- `body`

### BlogPost
- `title`
- `slug`
- `summary`
- `publishedAt`
- `updatedAt`
- `tags`
- `author`
- `cover`
- `draft`
- `body`

### 约束
- `slug` 全局唯一（各自集合内）
- `publishedAt` 必填
- `draft=true` 不进入生产输出

## 6. 数据流与渲染策略

**构建期**
- MDX 内容 -> Contentlayer typed data -> Next.js 路由静态生成

**运行期**
- 首页读取最新文档入口与博客摘要
- 文档/博客按 slug 解析，不存在时进入 `not-found`
- 反馈页以外链 GitHub Issues 为主路径

## 7. 错误处理与质量门槛

**错误处理**
- 未知 slug -> `404`
- API 异常（反馈扩展时）-> 结构化错误 `code/message/requestId`
- Frontmatter 不合法 -> 构建失败（fail fast）

**质量门槛（V1）**
- 路由可用：`/` `/docs` `/blog` `/feedback`
- 移动端可访问与导航可用
- Lighthouse 桌面端建议值：Performance >= 90，SEO >= 95

## 8. 里程碑

- M1：项目脚手架 + 设计系统 + 基础页面骨架
- M2：文档区/博客区 MDX 内容通路 + 列表/详情
- M3：SEO、反馈、可访问性、验收与迁移说明

