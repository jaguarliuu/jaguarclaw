# JaguarClaw

Desktop AI Assistant for Developers

---

## What is it

JaguarClaw is a desktop AI assistant that helps you get things done.

- Read and write files
- Execute shell commands  
- Search the web
- Long-term memory across sessions
- Automated task scheduling
- MCP protocol support

## Features

**ReAct Loop** - AI thinks, acts, observes, and iterates until task completion

**Tools** - File operations, shell execution, web search, memory, subagents

**Skills** - Auto-activated capabilities, compatible with Claude Skills format

**Memory** - Cross-session knowledge retention with semantic search

**Scheduling** - Cron-based automation with email/webhook delivery

**Subagents** - Parallel task execution with result aggregation

**MCP** - Connect to external tools and resources via Model Context Protocol

## Quick Start

**Requirements**: Java 24+, Node.js 20+, PostgreSQL 16+, LLM API key

```bash
# 1. Clone
git clone https://github.com/jaguarliuu/jaguarclaw.git
cd jaguarclaw

# 2. Start database
docker-compose up -d

# 3. Configure LLM
cat > src/main/resources/application-local.yml << EOF
llm:
  endpoint: https://api.deepseek.com
  api-key: your-api-key
  model: deepseek-chat
EOF

# 4. Build and run backend
mvn clean package -DskipTests
java -jar target/jaguarclaw-*.jar --spring.profiles.active=local

# 5. Run frontend
cd miniclaw-ui
npm install && npm run dev
```

Open http://localhost:5173

## Architecture

```
┌─────────────────────────────────────────┐
│  Frontend (Vue 3)                       │
│  Chat • Settings • Artifact Preview     │
└─────────────────────────────────────────┘
                    │ WebSocket
┌─────────────────────────────────────────┐
│  Gateway                                │
│  RPC Router • EventBus • Auth           │
└─────────────────────────────────────────┘
                    │
┌─────────────────────────────────────────┐
│  Runtime (ReAct Loop)                   │
│  Think → Act → Observe → Repeat         │
└─────────────────────────────────────────┘
                    │
┌─────────────────────────────────────────┐
│  Extensions                             │
│  Tools • Skills • Memory • MCP          │
└─────────────────────────────────────────┘
                    │
┌─────────────────────────────────────────┐
│  Storage                                │
│  PostgreSQL • Workspace • Vector DB     │
└─────────────────────────────────────────┘
```

## Project Structure

```
jaguarclaw/
├── src/main/java/com/jaguarliu/ai/
│   ├── runtime/       # ReAct engine
│   ├── tools/         # Tool system
│   ├── skills/        # Skill management
│   ├── memory/        # Long-term memory
│   ├── subagent/      # Subagent orchestration
│   ├── llm/           # LLM client
│   └── mcp/           # MCP protocol
├── miniclaw-ui/       # Vue 3 frontend
├── electron/          # Desktop app
└── workspace/         # Working directory
```

## Tech Stack

- Java 24 + Spring Boot 3.4
- Vue 3 + Vite
- PostgreSQL 16 + pgvector
- Electron

## Roadmap

**v1.0 (Current)**
- [x] ReAct loop engine
- [x] Tool system (10+ built-in)
- [x] Skill system
- [x] Global memory
- [x] Subagent parallel execution
- [x] Scheduled tasks
- [x] Remote node management
- [x] MCP protocol support
- [x] Desktop app

**v1.1**
- [ ] Sandbox execution
- [ ] External verification (Ralph Loop)
- [ ] Plugin system

## License

MIT
