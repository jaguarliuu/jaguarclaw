<div align="center">

# JaguarClaw

**企业级桌面 AI 助手**

一个开源、可自托管的 AI Agent 平台 —— 将任何大语言模型变成强大的桌面助手，支持工具执行、长期记忆、定时自动化和完整的 MCP 协议。

[English](README.md) | [中文](README.zh-CN.md)

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-24-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-green.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3-brightgreen.svg)](https://vuejs.org/)

</div>

---

## 为什么选择 JaguarClaw

大多数 AI 助手只是聊天框。JaguarClaw 是一个 **Agent** —— 它不仅回答问题，还能**做事**。

给它一个任务，它会推理步骤、执行工具、观察结果、反复迭代直到任务完成。它能读写文件、运行命令、搜索网页、记住你上周说的话，甚至在你休息时自动执行定时任务。

它完全运行在你的机器上。数据不会离开你的网络。你可以使用任何大模型 —— DeepSeek、通义千问、OpenAI、Ollama，或任何 OpenAI 兼容的服务商。

---

## 功能特性

### ReAct Agent 引擎

JaguarClaw 实现了 ReAct（推理 + 行动）循环，支持全流式输出：

```
用户："分析今天的错误日志，生成一份汇总报告"

  思考  → 我需要先找到今天的日志文件
  行动  → read_file("/var/log/app/2026-02-27.log")
  观察  → [2546 行日志内容...]
  思考  → 我看到 3 类反复出现的错误，让我分类整理
  行动  → write_file("report.md", "# 错误汇总\n...")
  完成  → "我已生成 report.md，包含 3 类错误的详细分析..."
```

- 多步推理，可配置最大步数和超时
- 基于 WebSocket 的流式 token 输出
- 危险操作需人工确认（Human-in-the-Loop）
- 任意步骤可取消

### 内置工具

| 工具 | 描述 | 安全性 |
|------|------|--------|
| `read_file` | 读取工作区文件内容 | 安全 |
| `write_file` | 创建或修改文件 | 安全 |
| `shell` | 执行 Shell 命令 | 危险命令需确认 |
| `web_search` | 网络搜索，支持多引擎 | 安全 |
| `memory_search` | 对长期记忆进行语义检索 | 安全 |
| `memory_save` | 保存重要信息供日后召回 | 安全 |
| `sessions_spawn` | 派生并行子代理处理复杂任务 | 安全 |
| `data_query` | 自然语言转 SQL 查询数据库 | 安全 |
| `use_skill` | 按名称激活指定技能 | 安全 |

Shell 安全检测是智能的 —— `ls`、`git status`、`npm install` 直接运行，而 `rm -rf`、`git push --force`、`DROP TABLE` 需要用户确认。

### 技能系统

技能是以 Markdown 文件定义的模块化专家行为。当任务匹配某个技能时，Agent 会加载专业指令和工具限制。

```yaml
---
name: code-review
description: 审查代码的 Bug、安全问题和代码风格
allowed-tools:
  - read_file
  - web_search
metadata:
  jaguarclaw:
    requires:
      bins: [git]
---

# 代码审查指引
审查代码时，重点关注...
```

- 兼容 Claude Skills 格式
- 热更新 —— 添加技能文件即刻生效
- 每个技能可独立限制工具调用范围
- 根据任务上下文自动选择技能

### 长期记忆

JaguarClaw 跨会话记忆。以 Markdown 文件为真相源，自动构建可搜索索引。

- **Markdown 存储** —— 人类可读、可版本管理
- **全文搜索** —— PostgreSQL tsvector
- **语义搜索** —— pgvector 向量嵌入（可选）
- **时间衰减排序** —— 近期记忆优先
- **预压缩刷写** —— 在 token 上限前保存重要上下文

### 子代理并行执行

面对复杂任务，主代理可以派生子代理并行工作：

```
用户："对比一下我们 3 个 API 接口的性能"

  主代理 → 派生 3 个子代理，每个负责一个接口
    子代理 1 → 压测 /api/users
    子代理 2 → 压测 /api/orders
    子代理 3 → 压测 /api/products
  主代理 → 汇总结果，生成对比表格
```

- 可配置并发上限（主 lane + 子代理 lane）
- 自动结果汇总
- 每个子代理独立会话隔离

### MCP 协议支持

连接任意 [Model Context Protocol](https://modelcontextprotocol.io/) 服务器以扩展能力：

- **传输方式**：STDIO、SSE、Streamable HTTP
- **官方服务器**：filesystem、fetch、git、postgres 等
- **第三方**：GitHub、Slack、AWS、Kubernetes
- **自定义**：用 Python 或 Node.js 构建你自己的 MCP 服务器
- **UI 管理**：在设置面板中添加、配置和监控服务器

### 定时自动化

用 cron 表达式设置周期性任务：

- **Cron 调度**，任务持久化存储
- **推送渠道**：邮件（SMTP）、Webhook（HTTP POST）
- **补偿策略**：错过的任务可配置补跑
- **隔离会话**：每次定时运行有独立的会话上下文

### 桌面应用

JaguarClaw 以 Electron 桌面应用发布，内置 JRE —— 无需安装 Java。

- 支持 **Windows**（.exe 安装包）和 **macOS**（.dmg）
- 通过 GitHub Releases 自动更新
- 内嵌后端 —— 安装即用
- SQLite 模式，零配置本地运行

---

## 快速开始

### 方式一：桌面应用（推荐）

从 [GitHub Releases](https://github.com/jaguarliuu/jaguarclaw/releases) 下载最新版本，安装后在设置中配置 LLM API Key，即可开始使用。

### 方式二：Docker Compose

```bash
git clone https://github.com/jaguarliuu/jaguarclaw.git
cd jaguarclaw

# 配置大模型
cp .env.example .env
# 编辑 .env —— 设置 LLM_ENDPOINT、LLM_API_KEY、LLM_MODEL

docker-compose up -d
```

打开 http://localhost 访问界面。

### 方式三：开发环境

**环境要求**：Java 24+、Node.js 20+、Maven 3.9+

```bash
git clone https://github.com/jaguarliuu/jaguarclaw.git
cd jaguarclaw

# 后端（默认使用内嵌 SQLite）
cat > src/main/resources/application-local.yml << 'EOF'
llm:
  endpoint: https://api.deepseek.com
  api-key: 你的-api-key
  model: deepseek-chat
EOF

mvn clean package -DskipTests
java -jar target/jaguarclaw-*.jar --spring.profiles.active=local

# 前端（另一个终端）
cd jaguarclaw-ui
npm install
npm run dev
```

打开 http://localhost:5173 开始对话。

### 支持的大模型

| 服务商 | Endpoint | 推荐模型 |
|--------|----------|----------|
| DeepSeek | `https://api.deepseek.com` | deepseek-chat、deepseek-reasoner |
| 通义千问 | `https://dashscope.aliyuncs.com/compatible-mode` | qwen-plus、qwen-max |
| OpenAI | `https://api.openai.com` | gpt-4o、gpt-4o-mini |
| Ollama（本地） | `http://localhost:11434` | llama3、qwen2.5、deepseek-r1 |
| 智谱 GLM | `https://open.bigmodel.cn/api/paas/v4` | glm-4-plus |

任何兼容 OpenAI 接口的服务商均可直接使用。

---

## 架构

```
┌──────────────────────────────────────────────────────┐
│  客户端层                                             │
│  桌面应用 (Electron) / Web 界面 (Vue 3)               │
└──────────────────────────┬───────────────────────────┘
                           │ WebSocket
┌──────────────────────────▼───────────────────────────┐
│  控制平面                                             │
│  RPC 路由 · 事件总线 · 连接管理                        │
└──────────────────────────┬───────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────┐
│  执行平面                                             │
│  ReAct 循环 · HITL 门控 · 上下文构建                   │
│  Lane 队列（会话串行 + 并发控制）                       │
│  子代理编排                                           │
└──────────┬───────────────────────────┬───────────────┘
           │                           │
┌──────────▼──────────┐  ┌─────────────▼───────────────┐
│  扩展平面            │  │  自动化平面                   │
│  工具 · 技能         │  │  Cron 调度器                  │
│  记忆 · MCP          │  │  邮件 / Webhook 推送          │
└──────────┬──────────┘  └─────────────┬───────────────┘
           │                           │
┌──────────▼───────────────────────────▼───────────────┐
│  状态平面                                             │
│  PostgreSQL / SQLite · pgvector · 工作区文件系统        │
└──────────────────────────────────────────────────────┘
```

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 24、Spring Boot 3.4、WebFlux、Spring Data JPA |
| 前端 | Vue 3、Vite、TypeScript |
| 数据库 | PostgreSQL 16 + pgvector / SQLite（内嵌） |
| 桌面 | Electron、electron-builder |
| 迁移 | Flyway |
| AI | OpenAI 兼容 API（任何服务商） |
| 协议 | Model Context Protocol（MCP） |

## 项目结构

```
jaguarclaw/
├── src/main/java/com/jaguarliu/ai/
│   ├── gateway/         # WebSocket、RPC 路由、事件总线
│   ├── runtime/         # ReAct 循环、HITL、上下文构建
│   ├── agents/          # Agent 配置、Lane 管理
│   ├── llm/             # LLM 客户端（OpenAI 兼容）
│   ├── tools/           # 内置工具、工具注册中心
│   ├── skills/          # 技能解析、注册、热更新
│   ├── memory/          # 长期记忆、搜索、刷写
│   ├── subagent/        # 子代理编排
│   ├── mcp/             # MCP 客户端（STDIO/SSE/HTTP）
│   ├── schedule/        # Cron 调度
│   ├── channel/         # 邮件 & Webhook 推送
│   ├── soul/            # 系统提示词配置
│   └── datasource/      # 数据源 & 自然语言查询
├── jaguarclaw-ui/       # Vue 3 前端
├── electron/            # 桌面应用
├── .jaguarclaw/skills/  # 内置技能
├── docs/                # 设计文档
└── data/                # 运行时数据（已 gitignore）
```

---

## 路线图

### v1.0 — 当前版本

- [x] ReAct Agent 循环，流式输出
- [x] 10+ 内置工具，安全策略完备
- [x] 技能系统（兼容 Claude Skills，热更新）
- [x] 长期记忆与语义搜索
- [x] 子代理并行执行
- [x] 定时任务与邮件/Webhook 推送
- [x] MCP 协议支持（STDIO/SSE/HTTP）
- [x] 桌面应用（Windows & macOS）
- [x] 多 Agent 配置与沙箱隔离
- [x] 自然语言数据查询（NL-to-SQL）
- [x] 双语界面（中文 & 英文）

### v1.1 — 计划中

- [ ] 沙箱代码执行环境
- [ ] 外部验证循环（Ralph Loop）
- [ ] 插件市场
- [ ] 上传文档 RAG 检索
- [ ] 语音输入/输出

---

## 贡献

欢迎贡献！无论是 Bug 修复、新工具、新技能还是文档改进 —— 所有 PR 都受欢迎。

1. Fork 本仓库
2. 创建功能分支（`git checkout -b feature/amazing-feature`）
3. 提交改动
4. 推送到分支（`git push origin feature/amazing-feature`）
5. 创建 Pull Request

---

## 许可证

本项目基于 MIT 许可证发布 —— 详见 [LICENSE](LICENSE) 文件。

---

<p align="center">
  <sub>Built with Java, Vue, and a lot of coffee.</sub>
</p>
