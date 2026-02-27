# JaguarClaw

[English](#english) | [中文](#中文)

---

## English

**Desktop AI Assistant for Developers**

JaguarClaw is a production-ready AI assistant that runs on your desktop. It helps developers and knowledge workers complete tasks through natural language interaction.

### What can it do

- **File Operations** - Read, create, modify files in your workspace
- **Shell Execution** - Run commands with intelligent safety checks
- **Web Search** - Search the internet for up-to-date information
- **Long-term Memory** - Remember preferences and context across sessions
- **Task Scheduling** - Automate recurring tasks with cron expressions
- **Parallel Execution** - Run multiple subtasks simultaneously
- **MCP Protocol** - Connect to external tools and services

### How it works

JaguarClaw uses the ReAct (Reasoning + Acting) pattern:

```
Think → What should I do next?
  ↓
Act → Execute a tool (read file, run command, etc.)
  ↓
Observe → What was the result?
  ↓
Repeat until task is complete
```

### Quick Start

**Requirements**

- Java 24+
- Node.js 20+
- PostgreSQL 16+
- LLM API key (OpenAI / DeepSeek / Qwen / Ollama)

**Installation**

```bash
# 1. Clone the repository
git clone https://github.com/jaguarliuu/jaguarclaw.git
cd jaguarclaw

# 2. Start PostgreSQL with Docker
docker-compose up -d

# 3. Configure your LLM
cat > src/main/resources/application-local.yml << EOF
llm:
  endpoint: https://api.deepseek.com
  api-key: your-api-key-here
  model: deepseek-chat
EOF

# 4. Build and run the backend
mvn clean package -DskipTests
java -jar target/jaguarclaw-*.jar --spring.profiles.active=local

# 5. Run the frontend
cd jaguarclaw-ui
npm install
npm run dev
```

Open http://localhost:5173 and start chatting.

### Architecture

```
┌─────────────────────────────────────────┐
│  Frontend (Vue 3)                       │
│  Chat UI • Settings • Artifact Preview  │
└─────────────────────────────────────────┘
                    │
                    ▼ WebSocket
┌─────────────────────────────────────────┐
│  Gateway                                │
│  RPC Router • EventBus • WebSocket      │
└─────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────┐
│  Runtime Engine                         │
│  ReAct Loop • HITL • Context Builder    │
└─────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────┐
│  Extensions                             │
│  Tools • Skills • Memory • Subagents    │
└─────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────┐
│  Storage                                │
│  PostgreSQL • pgvector • Workspace      │
└─────────────────────────────────────────┘
```

### Features

**ReAct Loop Engine**

AI doesn't respond once - it iterates until the task is done. It thinks about what to do, executes tools, observes results, and repeats.

**Tool System**

| Tool | Description | Safety |
|------|-------------|--------|
| read_file | Read file contents | Safe |
| write_file | Create or modify files | Safe |
| shell | Execute shell commands | Smart detection |
| web_search | Search the web | Safe |
| memory_search | Search past conversations | Safe |
| sessions_spawn | Spawn parallel subagents | Safe |

Dangerous commands (rm -rf, git push --force) require confirmation.

**Skill System**

Skills are modular capabilities that activate based on context. Compatible with Claude Skills format.

**Memory System**

- Markdown-based storage in `workspace/memory/`
- Semantic search with pgvector
- Time-decay ranking for relevance

**Scheduling**

Cron-based task automation with delivery via:
- Email (SMTP)
- Webhook (HTTP POST)

**Subagents**

Main agent can spawn child agents for parallel task execution. Results are aggregated automatically.

**MCP Protocol**

Connect to MCP servers for extended capabilities:
- Official: filesystem, fetch, git, postgres
- Third-party: GitHub, AWS, Kubernetes
- Custom: Build your own in Python/Node.js

### Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Java 24, Spring Boot 3.4, WebFlux |
| Frontend | Vue 3, Vite, TypeScript |
| Database | PostgreSQL 16, pgvector |
| Desktop | Electron |
| AI | OpenAI-compatible API |

### Project Structure

```
jaguarclaw/
├── src/main/java/com/jaguarliu/ai/
│   ├── runtime/       # ReAct engine, HITL, context
│   ├── tools/         # Built-in tools
│   ├── skills/        # Skill management
│   ├── memory/        # Long-term memory
│   ├── subagent/      # Subagent orchestration
│   ├── llm/           # LLM client
│   ├── mcp/           # MCP protocol client
│   ├── schedule/      # Cron scheduling
│   └── channel/       # Email/webhook delivery
├── jaguarclaw-ui/     # Vue 3 frontend
├── electron/          # Desktop application
├── workspace/         # Working directory
│   ├── .jaguarclaw/   # Skills directory
│   └── memory/        # Memory files
└── docs/              # Documentation
```

### Roadmap

**v1.0 (Current)**
- [x] ReAct loop engine
- [x] Tool system (10+ tools)
- [x] Skill system (Claude compatible)
- [x] Global memory with semantic search
- [x] Subagent parallel execution
- [x] Scheduled tasks with delivery
- [x] Remote node management (SSH/K8s)
- [x] MCP protocol support
- [x] Desktop app (Electron)

**v1.1 (Planned)**
- [ ] Sandbox code execution
- [ ] External verification (Ralph Loop)
- [ ] Plugin system
- [ ] Multi-language UI

### Contributing

Contributions welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

### License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 中文

**面向开发者的桌面 AI 助手**

JaguarClaw 是一款生产就绪的 AI 助手，运行在你的桌面上。它帮助开发者和知识工作者通过自然语言完成各种任务。

### 能做什么

- **文件操作** - 读取、创建、修改工作区中的文件
- **命令执行** - 运行 shell 命令，带智能安全检测
- **网络搜索** - 搜索互联网获取最新信息
- **长期记忆** - 跨会话记住偏好和上下文
- **任务调度** - 用 cron 表达式自动化周期性任务
- **并行执行** - 同时运行多个子任务
- **MCP 协议** - 连接外部工具和服务

### 工作原理

JaguarClaw 使用 ReAct（推理 + 行动）模式：

```
思考 → 下一步该做什么？
  ↓
行动 → 执行工具（读文件、运行命令等）
  ↓
观察 → 结果是什么？
  ↓
重复直到任务完成
```

### 快速开始

**环境要求**

- Java 24+
- Node.js 20+
- PostgreSQL 16+
- LLM API Key（OpenAI / DeepSeek / 通义千问 / Ollama）

**安装步骤**

```bash
# 1. 克隆仓库
git clone https://github.com/jaguarliuu/jaguarclaw.git
cd jaguarclaw

# 2. 用 Docker 启动 PostgreSQL
docker-compose up -d

# 3. 配置 LLM
cat > src/main/resources/application-local.yml << EOF
llm:
  endpoint: https://api.deepseek.com
  api-key: 你的-api-key
  model: deepseek-chat
EOF

# 4. 构建并运行后端
mvn clean package -DskipTests
java -jar target/jaguarclaw-*.jar --spring.profiles.active=local

# 5. 运行前端
cd jaguarclaw-ui
npm install
npm run dev
```

打开 http://localhost:5173 开始对话。

### 核心功能

**ReAct 循环引擎**

AI 不会一次性回答——它会迭代直到任务完成。思考要做什么，执行工具，观察结果，然后重复。

**工具系统**

| 工具 | 描述 | 安全性 |
|-----|------|--------|
| read_file | 读取文件内容 | 安全 |
| write_file | 创建或修改文件 | 安全 |
| shell | 执行 shell 命令 | 智能检测 |
| web_search | 网络搜索 | 安全 |
| memory_search | 搜索历史对话 | 安全 |
| sessions_spawn | 派生并行子代理 | 安全 |

危险命令（rm -rf、git push --force）需要确认。

**技能系统**

技能是基于上下文激活的模块化能力。兼容 Claude Skills 格式。

**记忆系统**

- 基于 Markdown 存储在 `workspace/memory/`
- 使用 pgvector 进行语义搜索
- 时间衰减排序提高相关性

**任务调度**

基于 cron 的任务自动化，支持以下方式推送结果：
- 邮件（SMTP）
- Webhook（HTTP POST）

**子代理**

主代理可以派生子代理并行执行任务。结果自动汇总。

**MCP 协议**

连接 MCP 服务器获取扩展能力：
- 官方：filesystem、fetch、git、postgres
- 第三方：GitHub、AWS、Kubernetes
- 自定义：用 Python/Node.js 构建你自己的

### 技术栈

| 层级 | 技术 |
|-----|------|
| 后端 | Java 24, Spring Boot 3.4, WebFlux |
| 前端 | Vue 3, Vite, TypeScript |
| 数据库 | PostgreSQL 16, pgvector |
| 桌面 | Electron |
| AI | OpenAI 兼容 API |

### 路线图

**v1.0（当前版本）**
- [x] ReAct 循环引擎
- [x] 工具系统（10+ 工具）
- [x] 技能系统（兼容 Claude）
- [x] 全局记忆与语义搜索
- [x] 子代理并行执行
- [x] 定时任务与推送
- [x] 远程节点管理（SSH/K8s）
- [x] MCP 协议支持
- [x] 桌面应用（Electron）

**v1.1（计划中）**
- [ ] 沙箱代码执行
- [ ] 外部验证（Ralph Loop）
- [ ] 插件系统
- [ ] 多语言界面

### 贡献

欢迎贡献。请阅读 [CONTRIBUTING.md](CONTRIBUTING.md) 了解指南。

### 许可证

本项目采用 MIT 许可证 - 详情见 [LICENSE](LICENSE) 文件。

---

<p align="center">
Made with coffee and code.
</p>
