<div align="center">

# MiniClaw

**强大的桌面 AI 助手**

*让 AI 成为你的超级工作伙伴*

[![Java](https://img.shields.io/badge/Java-24-ED8B00?style=flat-square&logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![Vue 3](https://img.shields.io/badge/Vue-3.5-4FC08D?style=flat-square&logo=vuedotjs)](https://vuejs.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat-square&logo=postgresql)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](LICENSE)

[English](#) | [中文文档](#) | [快速开始](#-快速开始) | [功能特性](#-核心特性)

</div>

---

## ✨ MiniClaw 是什么？

MiniClaw 是一款**生产就绪**的桌面 AI 助手，专为开发者和知识工作者打造。

它不只是聊天机器人，而是真正的**工作伙伴**：
- 📁 读取、创建、修改你的文件
- 🖥️ 执行命令行操作
- 🌐 搜索网络获取最新信息
- 🧠 记住你的偏好和历史对话
- 🎯 自动选择合适的技能完成任务
- ⏰ 定时执行任务并推送结果

**核心优势**：
- ⚡ **高性能**：Java 24 + Virtual Threads，流畅响应
- 🔒 **安全可控**：智能危险操作检测，敏感操作需确认
- 🎨 **极简美学**：黑白极简设计，专注内容
- 🔌 **开放生态**：支持 MCP 协议，无限扩展

---

## 🎬 30 秒演示

```
你: 帮我分析这个项目的依赖关系，生成一个 Mermaid 图

AI: 好的，我来分析项目依赖...

   🔧 [执行] 读取 package.json
   🔧 [执行] 分析依赖树
   🔧 [执行] 生成 Mermaid 代码
   📄 [创建] dependency-graph.md

   我已经生成了依赖关系图，右侧面板可以预览渲染效果。
   
   [右侧面板实时显示 Mermaid 图表]
```

---

## 🚀 核心特性

### 🔄 智能循环执行 (ReAct)

AI 不会一次性回答，而是**循环执行**直到任务完成：

```
🧠 Think  → 分析当前状态，决定下一步
🔧 Act    → 执行工具（读文件、运行命令、写代码...）
👀 Observe → 获取结果，更新认知
```

### 🛠️ 丰富的工具集

| 工具 | 功能 | 示例 |
|-----|------|------|
| `read_file` | 读取文件 | 读取代码、文档、配置 |
| `write_file` | 写入文件 | 创建代码、生成文档 |
| `shell` | 执行命令 | npm install、git status |
| `web_search` | 网络搜索 | 获取最新信息、查文档 |
| `memory_search` | 搜索记忆 | "我上次问过什么来着？" |
| `sessions_spawn` | 派生子代理 | 并行执行多个任务 |

**智能确认**：危险操作（`rm -rf`、`git push --force`）会弹窗让你确认。

### 🎯 技能系统

兼容 Claude Skills 格式，按需加载：

```markdown
# skills/git-helper/SKILL.md
---
name: git-helper
description: Git 操作助手
allowed-tools: [shell, read_file]
---

你是一个 Git 专家，帮助用户管理代码仓库。
```

技能会根据你的请求**自动激活**，也可以手动 `/skill-name` 触发。

### 🧠 长期记忆

跨会话记住你的偏好和重要信息：

- 📝 **Markdown 存储**：`workspace/memory/` 目录
- 🔍 **语义检索**：向量搜索 + 全文检索
- ⏰ **时间衰减**：近期记忆权重更高

### ⏰ 定时任务

让 AI 定期帮你干活：

```yaml
# 每天早上 8 点汇总 Git 提交
name: daily-git-report
cron: "0 8 * * *"
prompt: "帮我汇总昨天的 Git 提交记录"
```

结果可以推送到邮件或 Webhook。

### 🔀 并行子代理

主 Agent 可以派生子任务并行执行：

```
你: 同时 ping baidu.com 和 google.com

AI: 
   🚀 [子代理 A] ping baidu.com
   🚀 [子代理 B] ping google.com
   ⏳ 等待完成...
   ✅ 汇总结果：baidu 12ms, google 45ms
```

### 🔌 MCP 协议支持

连接 MCP 服务器，扩展无限可能：

- **官方服务器**：filesystem、fetch、git、postgres...
- **第三方服务器**：GitHub、AWS、Kubernetes...
- **自定义服务器**：用 Python/Node.js 开发

### 🖥️ 远程节点管理

通过 SSH / Kubernetes 连接远程服务器：

- 安全执行远程命令
- 完整审计日志
- 凭据加密存储

---

## 📦 快速开始

### 环境要求

- Java 24+
- Node.js 20+
- PostgreSQL 16+
- LLM API Key（OpenAI / DeepSeek / 通义千问 / Ollama...）

### 1️⃣ 克隆项目

```bash
git clone https://github.com/jaguarliuu/miniclaw.git
cd miniclaw
```

### 2️⃣ 启动数据库

```bash
docker-compose up -d
```

### 3️⃣ 配置 LLM

创建 `src/main/resources/application-local.yml`：

```yaml
llm:
  endpoint: https://api.deepseek.com
  api-key: sk-your-api-key
  model: deepseek-chat
```

### 4️⃣ 启动后端

```bash
mvn clean package -DskipTests
java -jar target/miniclaw-*.jar --spring.profiles.active=local
```

### 5️⃣ 启动前端

```bash
cd miniclaw-ui
npm install
npm run dev
```

打开 http://localhost:5173，开始对话！

---

## 📚 文档

- [架构设计](docs/architecture.md)
- [工具开发指南](docs/tool-development.md)
- [技能编写指南](docs/skill-development.md)
- [MCP 集成](docs/mcp-integration.md)
- [API 文档](docs/api.md)

---

## 🏗️ 项目结构

```
miniclaw/
├── src/main/java/com/jaguarliu/ai/
│   ├── runtime/          # ReAct 循环引擎
│   ├── tools/            # 工具系统
│   ├── skills/           # 技能系统
│   ├── memory/           # 记忆系统
│   ├── subagent/         # 子代理系统
│   ├── llm/              # LLM 客户端
│   ├── mcp/              # MCP 协议
│   └── ...               # 更多模块
├── miniclaw-ui/          # Vue 3 前端
├── electron/             # 桌面应用
├── workspace/            # 工作目录
│   ├── .miniclaw/skills/ # 技能文件
│   └── memory/           # 记忆文件
└── docs/                 # 文档
```

---

## 🗺️ Roadmap

### v1.0 (当前版本)

- [x] ReAct 循环引擎
- [x] 工具系统（10+ 内置工具）
- [x] 技能系统（Claude Skills 兼容）
- [x] 全局记忆（向量检索）
- [x] 子代理并行执行
- [x] 定时任务 + 渠道推送
- [x] 远程节点管理
- [x] MCP 协议支持
- [x] 桌面应用（Electron）

### v1.1 (计划中)

- [ ] Sandbox 安全代码执行
- [ ] Ralph Loop（外部验证）
- [ ] 更多内置技能
- [ ] 插件系统

---

## 🤝 贡献

欢迎贡献！查看 [贡献指南](CONTRIBUTING.md)

---

## 📄 License

[MIT](LICENSE)

---

<div align="center">

**[⬆ 回到顶部](#miniclaw)**

Made with ☕ and ❤️

</div>
