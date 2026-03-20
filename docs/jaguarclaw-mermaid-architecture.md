# JaguarClaw Mermaid 架构图集

这份文档整理了适合内部沟通、技术评审和对外展示的 Mermaid 图。

建议使用支持 Mermaid 的 Markdown 渲染器查看。

## 1. 总体架构图

这张图偏内部技术视角，贴近当前代码结构和运行时分层。

```mermaid
flowchart TB
    User["User / Operator"]
    UI["Vue 3 UI"]
    Electron["Electron Desktop Shell"]
    Gateway["Gateway Layer\nWebSocket + RPC Router + Event Bus"]
    Runtime["Execution Plane\nAgent Runtime + ReAct Loop + Session Lane"]
    Agents["Agent Profiles\nMain / Worker / Scheduler"]
    LLM["LLM Layer\nOpenAI-Compatible Client"]
    Tools["Extension Plane\nTools + HITL + Tool Registry"]
    Skills["Skill System\nParser + Registry + Hot Reload"]
    MCP["MCP Integration\nSTDIO / SSE / HTTP"]
    Memory["Memory System\nRecall + Flush + Search"]
    Schedule["Automation Plane\nScheduler + Delivery"]
    State["State Plane\nSQLite / PostgreSQL + Workspace FS"]
    Providers["Model Providers\nOpenAI / DeepSeek / Qwen / Custom"]

    User --> UI
    User --> Electron
    Electron --> UI
    UI --> Gateway
    Gateway --> Runtime
    Runtime --> Agents
    Runtime --> LLM
    Runtime --> Tools
    Runtime --> Skills
    Runtime --> Memory
    Runtime --> Schedule
    Tools --> MCP
    LLM --> Providers
    Runtime --> State
    Gateway --> State
    Memory --> State
    Schedule --> State
    Tools --> State
```

## 2. `agent.run` 主链路时序图

这张图适合解释一次正常会话是怎样从前端一路流转到模型和工具执行的。

```mermaid
sequenceDiagram
    autonumber
    participant U as User
    participant FE as UI / Desktop
    participant GW as Gateway RPC
    participant AR as Agent Runtime
    participant CB as Context Builder
    participant LLM as LLM Client
    participant TR as Tool Registry / Dispatcher
    participant DB as State Store

    U->>FE: 输入任务并发送
    FE->>GW: agent.run(sessionId, prompt, modelSelection)
    GW->>DB: 创建 run + 更新状态 queued
    GW->>AR: 投递到 session lane
    AR->>DB: 状态 running
    AR->>CB: 组装 system prompt + history + memory + skills
    CB-->>AR: messages
    AR->>LLM: stream(messages, tools, tool_choice)
    LLM-->>AR: assistant.delta
    AR-->>GW: assistant.delta 事件流
    GW-->>FE: 推送流式内容

    alt 模型发起工具调用
        LLM-->>AR: tool_calls
        AR->>TR: 执行工具
        TR->>DB: 记录 tool.call / tool.result
        TR-->>AR: observation
        AR->>LLM: 下一轮推理
    end

    LLM-->>AR: final answer / finish_reason
    AR->>DB: 保存 assistant message + run done
    AR-->>GW: lifecycle.end
    GW-->>FE: 最终完成事件
```

## 3. 工具调用闭环时序图

这张图更适合讲 JaguarClaw 的 agentic 核心，不强调 UI，而强调 ReAct + Tool Use。

```mermaid
sequenceDiagram
    autonumber
    participant RT as ReAct Loop
    participant LLM as OpenAI-Compatible Model
    participant TD as Tool Dispatcher
    participant Tool as Built-in Tool / MCP Tool
    participant HITL as Confirm Layer

    RT->>LLM: 提交当前上下文 + 工具定义
    LLM-->>RT: 返回 tool_calls
    RT->>TD: dispatch(toolName, arguments)

    alt 需要人工确认
        TD->>HITL: 发起 confirm_request
        HITL-->>TD: approve / reject
    end

    TD->>Tool: execute(arguments)
    Tool-->>TD: result / error
    TD-->>RT: observation
    RT->>LLM: 回灌 tool result，继续推理
    LLM-->>RT: 最终回答
```

## 4. 对外展示版产品图

这张图偏产品表达，适合放官网、PPT 或外部介绍材料。

```mermaid
flowchart LR
    A["One AI Workspace"]
    B["Chat + Documents + IM"]
    C["Multi-Agent Collaboration"]
    D["Tools + Skills + MCP"]
    E["Memory + Automation"]
    F["Any OpenAI-Compatible Model"]
    G["Desktop + Web-Friendly Architecture"]

    A --> B
    A --> C
    A --> D
    A --> E
    A --> F
    A --> G

    B --> B1["Conversations"]
    B --> B2["Document AI"]
    B --> B3["Intranet IM"]

    C --> C1["Main Agent"]
    C --> C2["Worker / Subagent"]
    C --> C3["Scheduled Agent"]

    D --> D1["Filesystem / Shell / HTTP"]
    D --> D2["Custom Skills"]
    D --> D3["MCP Servers"]

    E --> E1["Long-Term Memory"]
    E --> E2["Scheduled Jobs"]
    E --> E3["Delivery via Email / Webhook"]
```

## 5. 部署与集成拓扑图

这张图适合对外解释“如何落地到客户环境”。

```mermaid
flowchart TB
    Client["User Device"]
    Desktop["Electron App"]
    Browser["Browser UI"]
    Server["JaguarClaw Server\nSpring Boot + WebSocket"]
    DB["SQLite / PostgreSQL"]
    FS["Workspace / Files / Skills"]
    Model["OpenAI-Compatible Models"]
    MCP["Enterprise MCP Servers"]
    Mail["Email / Webhook / Delivery"]

    Client --> Desktop
    Client --> Browser
    Desktop --> Server
    Browser --> Server

    Server --> DB
    Server --> FS
    Server --> Model
    Server --> MCP
    Server --> Mail
```

## 6. 对外讲解建议

- 面向研发团队：优先用“总体架构图 + `agent.run` 主链路时序图”
- 面向客户或投资人：优先用“对外展示版产品图 + 部署与集成拓扑图”
- 面向实施或售前：补充“工具调用闭环时序图”，强调可控、可扩展、可接企业系统
