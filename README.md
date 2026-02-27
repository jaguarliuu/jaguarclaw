<div align="center">

# JaguarClaw

**Enterprise-Grade Desktop AI Assistant**

An open-source, self-hosted AI agent platform that turns any LLM into a powerful desktop assistant — with tool execution, long-term memory, scheduled automation, and full MCP protocol support.

[English](README.md) | [中文](README.zh-CN.md)

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-24-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-green.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3-brightgreen.svg)](https://vuejs.org/)

</div>

---

## Why JaguarClaw

Most AI assistants are chat boxes. JaguarClaw is an **agent** — it doesn't just answer questions, it *does* things.

Give it a task, and it will reason through the steps, execute tools, observe results, and iterate until the job is done. It reads and writes files, runs shell commands, searches the web, remembers what you told it last week, and can even schedule recurring tasks to run while you sleep.

It runs entirely on your machine. Your data never leaves your network. You bring your own LLM — DeepSeek, Qwen, OpenAI, Ollama, or any OpenAI-compatible provider.

---

## Features

### ReAct Agent Engine

JaguarClaw implements the ReAct (Reasoning + Acting) loop with full streaming output:

```
User: "Analyze the error logs from today and create a summary report"

  Think  → I need to find today's log files first
  Act    → read_file("/var/log/app/2026-02-27.log")
  Observe → [2546 lines of log content...]
  Think  → I see 3 recurring errors. Let me categorize them
  Act    → write_file("report.md", "# Error Summary\n...")
  Done   → "I've created report.md with 3 error categories..."
```

- Multi-step reasoning with configurable max steps and timeouts
- Streaming token output via WebSocket
- Human-in-the-loop confirmation for dangerous operations
- Cancellation support at any step

### Built-in Tools

| Tool | Description | Safety |
|------|-------------|--------|
| `read_file` | Read file contents from workspace | Safe |
| `write_file` | Create or modify files | Safe |
| `shell` | Execute shell commands | Confirmation required for dangerous commands |
| `web_search` | Search the web with configurable providers | Safe |
| `memory_search` | Semantic search over long-term memory | Safe |
| `memory_save` | Save important information for future recall | Safe |
| `sessions_spawn` | Spawn parallel subagents for complex tasks | Safe |
| `data_query` | Natural language to SQL over connected databases | Safe |
| `use_skill` | Activate a specific skill by name | Safe |

Shell safety is intelligent — `ls`, `git status`, `npm install` run freely, while `rm -rf`, `git push --force`, and `DROP TABLE` require explicit user confirmation.

### Skill System

Skills are modular expert behaviors defined as Markdown files. When a task matches a skill, the agent loads specialized instructions and tool restrictions.

```yaml
---
name: code-review
description: Review code for bugs, security issues, and style
allowed-tools:
  - read_file
  - web_search
metadata:
  jaguarclaw:
    requires:
      bins: [git]
---

# Code Review Instructions
When reviewing code, focus on...
```

- Compatible with Claude Skills format
- Hot-reload — add a skill file, it's available immediately
- Tool restriction per skill for safety
- Automatic skill selection based on task context

### Long-Term Memory

JaguarClaw remembers across sessions. It uses Markdown files as the source of truth and builds searchable indexes automatically.

- **Markdown-based storage** — human-readable, version-controllable
- **Full-text search** with PostgreSQL tsvector
- **Semantic search** with pgvector embeddings (optional)
- **Time-decay ranking** — recent memories surface first
- **Pre-compaction flush** — saves important context before token limits hit

### Subagent Parallel Execution

For complex tasks, the main agent can spawn child agents that work in parallel:

```
User: "Compare the performance of our 3 API endpoints"

  Main Agent → spawns 3 subagents, one per endpoint
    Subagent 1 → benchmarks /api/users
    Subagent 2 → benchmarks /api/orders
    Subagent 3 → benchmarks /api/products
  Main Agent → aggregates results into comparison table
```

- Configurable concurrency limits (main lane + subagent lane)
- Automatic result aggregation
- Isolated sessions per subagent

### MCP Protocol Support

Connect to any [Model Context Protocol](https://modelcontextprotocol.io/) server to extend capabilities:

- **Transport**: STDIO, SSE, Streamable HTTP
- **Official servers**: filesystem, fetch, git, postgres, and more
- **Third-party**: GitHub, Slack, AWS, Kubernetes
- **Custom**: Build your own MCP server in Python or Node.js
- **UI management**: Add, configure, and monitor servers from the settings panel

### Scheduled Automation

Set up recurring tasks with cron expressions:

- **Cron scheduling** with persistent job storage
- **Delivery channels**: Email (SMTP), Webhook (HTTP POST)
- **Missed job handling**: Configurable catch-up policies
- **Isolated sessions**: Each scheduled run gets its own session context

### Desktop Application

JaguarClaw ships as an Electron desktop app with a bundled JRE — no Java installation required.

- **Windows** (.exe installer) and **macOS** (.dmg)
- Auto-updates via GitHub Releases
- Embedded backend — just install and run
- SQLite mode for zero-configuration local use

---

## Quick Start

### Option 1: Desktop App (Recommended)

Download the latest release from [GitHub Releases](https://github.com/jaguarliuu/jaguarclaw/releases), install, configure your LLM API key in Settings, and start chatting.

### Option 2: Docker Compose

```bash
git clone https://github.com/jaguarliuu/jaguarclaw.git
cd jaguarclaw

# Configure your LLM
cp .env.example .env
# Edit .env — set LLM_ENDPOINT, LLM_API_KEY, LLM_MODEL

docker-compose up -d
```

Open http://localhost to access the UI.

### Option 3: Development Setup

**Prerequisites**: Java 24+, Node.js 20+, Maven 3.9+

```bash
git clone https://github.com/jaguarliuu/jaguarclaw.git
cd jaguarclaw

# Backend (uses embedded SQLite by default)
cat > src/main/resources/application-local.yml << 'EOF'
llm:
  endpoint: https://api.deepseek.com
  api-key: your-api-key-here
  model: deepseek-chat
EOF

mvn clean package -DskipTests
java -jar target/jaguarclaw-*.jar --spring.profiles.active=local

# Frontend (in another terminal)
cd jaguarclaw-ui
npm install
npm run dev
```

Open http://localhost:5173 and start chatting.

### Supported LLM Providers

| Provider | Endpoint | Recommended Models |
|----------|----------|--------------------|
| DeepSeek | `https://api.deepseek.com` | deepseek-chat, deepseek-reasoner |
| Qwen (Alibaba) | `https://dashscope.aliyuncs.com/compatible-mode` | qwen-plus, qwen-max |
| OpenAI | `https://api.openai.com` | gpt-4o, gpt-4o-mini |
| Ollama (Local) | `http://localhost:11434` | llama3, qwen2.5, deepseek-r1 |
| GLM (Zhipu) | `https://open.bigmodel.cn/api/paas/v4` | glm-4-plus |

Any OpenAI-compatible API endpoint works out of the box.

---

## Architecture

```
┌──────────────────────────────────────────────────────┐
│  Client Layer                                        │
│  Desktop App (Electron) / Web UI (Vue 3)             │
└──────────────────────────┬───────────────────────────┘
                           │ WebSocket
┌──────────────────────────▼───────────────────────────┐
│  Control Plane                                       │
│  RPC Router · Event Bus · Connection Manager         │
└──────────────────────────┬───────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────┐
│  Execution Plane                                     │
│  ReAct Loop · HITL Gate · Context Builder            │
│  Lane Queue (session-serial + concurrency control)   │
│  Subagent Orchestration                              │
└──────────┬───────────────────────────┬───────────────┘
           │                           │
┌──────────▼──────────┐  ┌─────────────▼───────────────┐
│  Extension Plane    │  │  Automation Plane            │
│  Tools · Skills     │  │  Cron Scheduler              │
│  Memory · MCP       │  │  Email / Webhook Delivery    │
└──────────┬──────────┘  └─────────────┬───────────────┘
           │                           │
┌──────────▼───────────────────────────▼───────────────┐
│  State Plane                                         │
│  PostgreSQL / SQLite · pgvector · Workspace FS       │
└──────────────────────────────────────────────────────┘
```

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Java 24, Spring Boot 3.4, WebFlux, Spring Data JPA |
| Frontend | Vue 3, Vite, TypeScript |
| Database | PostgreSQL 16 + pgvector / SQLite (embedded) |
| Desktop | Electron, electron-builder |
| Migrations | Flyway |
| AI | OpenAI-compatible API (any provider) |
| Protocol | Model Context Protocol (MCP) |

## Project Structure

```
jaguarclaw/
├── src/main/java/com/jaguarliu/ai/
│   ├── gateway/         # WebSocket, RPC router, event bus
│   ├── runtime/         # ReAct loop, HITL, context builder
│   ├── agents/          # Agent profiles, lane management
│   ├── llm/             # LLM client (OpenAI-compatible)
│   ├── tools/           # Built-in tools, tool registry
│   ├── skills/          # Skill parser, registry, hot-reload
│   ├── memory/          # Long-term memory, search, flush
│   ├── subagent/        # Subagent orchestration
│   ├── mcp/             # MCP client (STDIO/SSE/HTTP)
│   ├── schedule/        # Cron scheduling
│   ├── channel/         # Email & webhook delivery
│   ├── soul/            # System prompt configuration
│   └── datasource/      # Data source & NL-to-SQL
├── jaguarclaw-ui/       # Vue 3 frontend
├── electron/            # Desktop application
├── .jaguarclaw/skills/  # Built-in skills
├── docs/                # Design docs & plans
└── data/                # Runtime data (gitignored)
```

---

## Roadmap

### v1.0 — Current

- [x] ReAct agent loop with streaming output
- [x] 10+ built-in tools with safety policies
- [x] Skill system (Claude Skills compatible, hot-reload)
- [x] Long-term memory with semantic search
- [x] Subagent parallel execution
- [x] Scheduled tasks with email/webhook delivery
- [x] MCP protocol support (STDIO/SSE/HTTP)
- [x] Desktop app (Windows & macOS)
- [x] Multi-agent profiles with sandbox isolation
- [x] Natural language data query (NL-to-SQL)
- [x] Bilingual UI (English & Chinese)

### v1.1 — Planned

- [ ] Sandboxed code execution environment
- [ ] External verification loop (Ralph Loop)
- [ ] Plugin marketplace
- [ ] RAG over uploaded documents
- [ ] Voice input/output

---

## Contributing

Contributions are welcome! Whether it's bug fixes, new tools, skills, or documentation improvements — all PRs are appreciated.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

<p align="center">
  <sub>Built with Java, Vue, and a lot of coffee.</sub>
</p>
