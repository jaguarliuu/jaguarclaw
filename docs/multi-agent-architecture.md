# 多 Agent 架构重构方案

> 版本：v1.0
> 日期：2026-02-28
> 作者：Pace

---

## 📋 目录

1. [背景与目标](#1-背景与目标)
2. [现状分析](#2-现状分析)
3. [架构设计](#3-架构设计)
4. [数据模型](#4-数据模型)
5. [核心组件](#5-核心组件)
6. [API 设计](#6-api-设计)
7. [实施计划](#7-实施计划)
8. [风险评估](#8-风险评估)

---

## 1. 背景与目标

### 1.1 业务需求

企业实践中需要**不同人设的 Agent**：

| Agent | Soul | 工具 | 记忆 | 用途 |
|-------|------|------|------|------|
| **编程助手** | 技术专家 | shell, read_file, write_file | 代码片段、项目信息 | 日常开发 |
| **代码审查员** | 严格审查员 | read_file, memory_search | 代码规范、审查历史 | PR 审查 |
| **数据分析师** | 分析专家 | read_file, web_search | 数据源、分析模板 | 数据报告 |
| **内容创作者** | 创意作家 | write_file, web_search | 写作风格、素材库 | 文档撰写 |

### 1.2 设计目标

1. **多 Agent 支持** - 用户可创建多个独立 Agent
2. **独立 Workspace** - 每个 Agent 有独立的工作空间
3. **独立 Soul** - 每个 Agent 有独立的身份和性格
4. **工具隔离** - 每个 Agent 可配置不同的工具集
5. **记忆隔离** - 每个 Agent 有独立的记忆系统
6. **Skill 隔离** - 每个 Agent 有独立的技能集
7. **心跳隔离** - 每个 Agent 有独立的心跳配置
8. **@ 提及** - 用户可在对话中 @ 不同的 Agent

---

## 2. 现状分析

### 2.1 当前架构

```
┌─────────────────────────────────────┐
│          Single Agent               │
│                                     │
│  ┌──────────┐  ┌──────────┐        │
│  │ ToolReg  │  │  Memory  │        │
│  │ (全局)   │  │  (全局)   │        │
│  └──────────┘  └──────────┘        │
│                                     │
│  ┌──────────┐  ┌──────────┐        │
│  │  Soul    │  │  Skills  │        │
│  │ (全局)   │  │  (全局)   │        │
│  └──────────┘  └──────────┘        │
│                                     │
│  ┌─────────────────────────┐       │
│  │    AgentRuntime         │       │
│  │    (单例)               │       │
│  └─────────────────────────┘       │
└─────────────────────────────────────┘
```

### 2.2 问题分析

| 组件 | 当前状态 | 问题 |
|------|---------|------|
| **ToolRegistry** | 全局单例 | 所有 Agent 共享相同工具 |
| **MemoryService** | 全局单例 | 记忆混在一起 |
| **SoulConfigService** | 全局单例 | 只有一个 Soul |
| **SkillRegistry** | 全局单例 | 所有 Agent 共享技能 |
| **HeartbeatScheduler** | 全局单例 | 只有一个心跳配置 |
| **RunContext** | agentId 字段存在但未使用 | 无多 Agent 支持 |

---

## 3. 架构设计

### 3.1 新架构

```
┌─────────────────────────────────────────────────────┐
│                  AgentManager                        │
│           (管理所有 Agent 实例)                      │
└─────────────────────────────────────────────────────┘
                         │
        ┌────────────────┼────────────────┐
        ▼                ▼                ▼
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│   Agent 1   │  │   Agent 2   │  │   Agent 3   │
│  (main)     │  │  (coder)    │  │ (reviewer)  │
├─────────────┤  ├─────────────┤  ├─────────────┤
│ Workspace   │  │ Workspace   │  │ Workspace   │
│ - tools/    │  │ - tools/    │  │ - tools/    │
│ - memory/   │  │ - memory/   │  │ - memory/   │
│ - SOUL.md   │  │ - SOUL.md   │  │ - SOUL.md   │
│ - skills/   │  │ - skills/   │  │ - skills/   │
│ - HEARTBEAT │  │ - HEARTBEAT │  │ - HEARTBEAT │
├─────────────┤  ├─────────────┤  ├─────────────┤
│ Runtime     │  │ Runtime     │  │ Runtime     │
│ (独立实例)  │  │ (独立实例)  │  │ (独立实例)  │
└─────────────┘  └─────────────┘  └─────────────┘
```

### 3.2 目录结构

```
workspace/
├── agents/
│   ├── main/                    # 默认 Agent
│   │   ├── SOUL.md
│   │   ├── HEARTBEAT.md
│   │   ├── AGENTS.md
│   │   ├── USER.md
│   │   ├── memory/
│   │   │   └── *.md
│   │   └── skills/
│   │       └── skill-name/
│   │           └── SKILL.md
│   ├── coder/                   # 编程助手
│   │   ├── SOUL.md
│   │   ├── HEARTBEAT.md
│   │   ├── memory/
│   │   └── skills/
│   └── reviewer/                # 代码审查员
│       ├── SOUL.md
│       ├── HEARTBEAT.md
│       ├── memory/
│       └── skills/
├── shared/                      # 共享资源
│   └── tools/                   # 全局工具配置
└── config/
    └── agents.yml               # Agent 配置
```

---

## 4. 数据模型

### 4.1 Agent Profile

```java
@Entity
@Table(name = "agent_profile")
public class AgentProfile {

    @Id
    private String id;                    // agent-uuid

    @Column(unique = true)
    private String name;                  // "coder", "reviewer"

    private String displayName;           // "编程助手"

    private String description;           // "帮助你编写代码"

    private String workspacePath;         // workspace/agents/coder

    private String model;                 // "deepseek-chat"

    private boolean isDefault;            // 是否是默认 Agent

    private boolean enabled;              // 是否启用

    private Instant createdAt;

    private Instant updatedAt;

    // 工具配置
    @Column(columnDefinition = "TEXT")
    private String allowedTools;          // JSON array: ["read_file", "write_file"]

    @Column(columnDefinition = "TEXT")
    private String excludedTools;         // JSON array

    // 心跳配置
    private Integer heartbeatInterval;    // 分钟

    private String heartbeatActiveHours;  // "09:00-22:00"

    // 配额
    private Integer dailyTokenLimit;

    private Double monthlyCostLimit;
}
```

### 4.2 Agent Runtime Context

```java
public class AgentRuntimeContext {

    private final String agentId;

    private final String agentName;

    private final Path workspacePath;

    // Agent 独立的组件
    private final ToolRegistry toolRegistry;

    private final MemoryService memoryService;

    private final SoulService soulService;

    private final SkillRegistry skillRegistry;

    private final HeartbeatScheduler heartbeatScheduler;

    // Agent 独立的配置
    private final AgentConfig config;
}
```

---

## 5. 核心组件

### 5.1 AgentManager

```java
@Service
public class AgentManager {

    private final Map<String, AgentRuntimeContext> agentContexts = new ConcurrentHashMap<>();

    private final AgentProfileRepository profileRepository;

    /**
     * 获取 Agent 运行时上下文
     */
    public AgentRuntimeContext getContext(String agentId) {
        return agentContexts.computeIfAbsent(agentId, this::createContext);
    }

    /**
     * 创建 Agent 运行时上下文
     */
    private AgentRuntimeContext createContext(String agentId) {
        AgentProfile profile = profileRepository.findById(agentId)
            .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        Path workspace = Paths.get(profile.getWorkspacePath());

        return AgentRuntimeContext.builder()
            .agentId(agentId)
            .agentName(profile.getName())
            .workspacePath(workspace)
            .toolRegistry(createToolRegistry(profile, workspace))
            .memoryService(createMemoryService(workspace))
            .soulService(createSoulService(workspace))
            .skillRegistry(createSkillRegistry(workspace))
            .build();
    }

    /**
     * 创建 Agent 独立的工具注册表
     */
    private ToolRegistry createToolRegistry(AgentProfile profile, Path workspace) {
        Set<String> allowed = parseJsonArray(profile.getAllowedTools());
        Set<String> excluded = parseJsonArray(profile.getExcludedTools());

        return new AgentToolRegistry(allowed, excluded, workspace);
    }

    /**
     * 创建 Agent 独立的记忆服务
     */
    private MemoryService createMemoryService(Path workspace) {
        Path memoryDir = workspace.resolve("memory");
        return new AgentMemoryService(memoryDir);
    }

    /**
     * 创建 Agent 独立的 Soul 服务
     */
    private SoulService createSoulService(Path workspace) {
        Path soulFile = workspace.resolve("SOUL.md");
        return new AgentSoulService(soulFile);
    }

    /**
     * 列出所有 Agent
     */
    public List<AgentProfile> listAgents() {
        return profileRepository.findByEnabledTrue();
    }

    /**
     * 创建新 Agent
     */
    public AgentProfile createAgent(CreateAgentRequest request) {
        // 1. 创建数据库记录
        AgentProfile profile = new AgentProfile();
        profile.setId(UUID.randomUUID().toString());
        profile.setName(request.getName());
        profile.setDisplayName(request.getDisplayName());
        profile.setDescription(request.getDescription());
        profile.setModel(request.getModel());
        profile.setAllowedTools(toJson(request.getAllowedTools()));
        profile.setEnabled(true);
        profile = profileRepository.save(profile);

        // 2. 创建 workspace 目录
        Path workspace = Paths.get("workspace/agents", profile.getName());
        Files.createDirectories(workspace);
        Files.createDirectories(workspace.resolve("memory"));
        Files.createDirectories(workspace.resolve("skills"));

        // 3. 创建默认 SOUL.md
        Path soulFile = workspace.resolve("SOUL.md");
        Files.writeString(soulFile, generateDefaultSoul(request));

        // 4. 创建默认 HEARTBEAT.md
        Path heartbeatFile = workspace.resolve("HEARTBEAT.md");
        Files.writeString(heartbeatFile, generateDefaultHeartbeat());

        profile.setWorkspacePath(workspace.toString());
        return profileRepository.save(profile);
    }
}
```

### 5.2 AgentToolRegistry

```java
public class AgentToolRegistry implements ToolRegistry {

    private final Set<String> allowedTools;

    private final Set<String> excludedTools;

    private final Path workspace;

    private final GlobalToolRegistry globalRegistry;

    public AgentToolRegistry(
        Set<String> allowedTools,
        Set<String> excludedTools,
        Path workspace
    ) {
        this.allowedTools = allowedTools;
        this.excludedTools = excludedTools;
        this.workspace = workspace;
        this.globalRegistry = GlobalToolRegistry.getInstance();
    }

    @Override
    public List<Map<String, Object>> toOpenAiTools(Set<String> additionalExclude) {
        // 过滤工具
        return globalRegistry.toOpenAiTools().stream()
            .filter(tool -> isAllowed(tool))
            .filter(tool -> !isExcluded(tool, additionalExclude))
            .toList();
    }

    private boolean isAllowed(Map<String, Object> tool) {
        if (allowedTools == null || allowedTools.isEmpty()) {
            return true; // 未配置白名单 = 允许所有
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> function = (Map<String, Object>) tool.get("function");
        String name = (String) function.get("name");

        return allowedTools.contains(name);
    }

    private boolean isExcluded(Map<String, Object> tool, Set<String> additionalExclude) {
        @SuppressWarnings("unchecked")
        Map<String, Object> function = (Map<String, Object>) tool.get("function");
        String name = (String) function.get("name");

        return excludedTools.contains(name) ||
               (additionalExclude != null && additionalExclude.contains(name));
    }
}
```

### 5.3 AgentMemoryService

```java
public class AgentMemoryService implements MemoryService {

    private final Path memoryDir;

    private final MemoryIndex index;

    public AgentMemoryService(Path memoryDir) {
        this.memoryDir = memoryDir;
        this.index = new MemoryIndex(memoryDir);
    }

    @Override
    public void save(String content, Map<String, Object> metadata) {
        String filename = generateFilename(metadata);
        Path file = memoryDir.resolve(filename);
        Files.writeString(file, content);

        index.add(file, content, metadata);
    }

    @Override
    public List<MemoryChunk> search(String query, int limit) {
        return index.search(query, limit);
    }

    @Override
    public List<MemoryChunk> getRecent(int limit) {
        return index.getRecent(limit);
    }
}
```

### 5.4 AgentSoulService

```java
public class AgentSoulService implements SoulService {

    private final Path soulFile;

    private volatile String cachedContent;

    private volatile Instant lastModified;

    public AgentSoulService(Path soulFile) {
        this.soulFile = soulFile;
    }

    @Override
    public String getSoulContent() {
        // 检查文件是否修改
        if (cachedContent != null && !isModified()) {
            return cachedContent;
        }

        // 重新读取
        if (Files.exists(soulFile)) {
            cachedContent = Files.readString(soulFile);
            lastModified = Files.getLastModifiedTime(soulFile).toInstant();
        } else {
            cachedContent = "";
        }

        return cachedContent;
    }

    @Override
    public void updateSoul(String content) {
        Files.writeString(soulFile, content);
        cachedContent = content;
        lastModified = Instant.now();
    }

    private boolean isModified() {
        if (!Files.exists(soulFile)) {
            return true;
        }

        Instant fileTime = Files.getLastModifiedTime(soulFile).toInstant();
        return !fileTime.equals(lastModified);
    }
}
```

---

## 6. API 设计

### 6.1 WebSocket RPC

```java
// Agent 管理
@RpcMethod("agent.list")
public List<AgentProfile> listAgents();

@RpcMethod("agent.create")
public AgentProfile createAgent(CreateAgentRequest request);

@RpcMethod("agent.get")
public AgentProfile getAgent(String agentId);

@RpcMethod("agent.update")
public AgentProfile updateAgent(UpdateAgentRequest request);

@RpcMethod("agent.delete")
public void deleteAgent(String agentId);

// Agent 对话（支持 @ 提及）
@RpcMethod("chat")
public void chat(ChatRequest request);

// ChatRequest 扩展
public class ChatRequest {
    private String message;
    private String agentId;      // 指定 Agent
    private String sessionId;
    // ...
}
```

### 6.2 @ 提及语法

```
用户: @coder 帮我写一个排序算法
     ↓
系统: 路由到 coder Agent
     ↓
Agent(coder): 好的，我来帮你实现快速排序...

用户: @reviewer 检查下这段代码
     ↓
系统: 路由到 reviewer Agent
     ↓
Agent(reviewer): 我发现了 3 个问题...
```

**实现**：

```java
@Service
public class ChatRouter {

    private final AgentManager agentManager;

    private static final Pattern AGENT_MENTION = Pattern.compile("@(\\w+)");

    public void route(String message, String sessionId) {
        // 解析 @ 提及
        Matcher matcher = AGENT_MENTION.matcher(message);
        String agentId = "main"; // 默认

        if (matcher.find()) {
            String agentName = matcher.group(1);
            agentId = resolveAgentId(agentName);

            // 移除 @ 提及
            message = matcher.replaceFirst("").trim();
        }

        // 获取 Agent 上下文
        AgentRuntimeContext context = agentManager.getContext(agentId);

        // 执行对话
        context.getRuntime().executeChat(message, sessionId);
    }

    private String resolveAgentId(String agentName) {
        // 根据名称查找 agentId
        return agentManager.findByName(agentName)
            .map(AgentProfile::getId)
            .orElse("main");
    }
}
```

---

## 7. 实施计划

### 7.1 阶段划分

| 阶段 | 内容 | 工作量 | 依赖 |
|------|------|--------|------|
| **0** | 数据模型 + Repository | 1 天 | - |
| **1** | AgentManager 核心逻辑 | 2 天 | 阶段 0 |
| **2** | AgentToolRegistry | 1 天 | 阶段 1 |
| **3** | AgentMemoryService | 1 天 | 阶段 1 |
| **4** | AgentSoulService | 1 天 | 阶段 1 |
| **5** | AgentSkillRegistry | 1 天 | 阶段 1 |
| **6** | AgentHeartbeatScheduler | 1 天 | 阶段 1-5 |
| **7** | ChatRouter + @ 提及 | 1 天 | 阶段 1-6 |
| **8** | WebSocket API | 1 天 | 阶段 7 |
| **9** | 前端 UI | 3 天 | 阶段 8 |
| **10** | 测试 + 修复 | 2 天 | 阶段 1-9 |

**总计**：14 天

### 7.2 里程碑

- **M1 (Day 3)**: 后端核心逻辑完成，可创建 Agent
- **M2 (Day 7)**: 所有组件完成，Agent 独立运行
- **M3 (Day 10)**: @ 提及和路由完成
- **M4 (Day 14)**: 前端 UI 完成，全部功能可用

### 7.3 向后兼容

- `main` Agent 保持现有行为
- 不指定 agentId 时默认使用 `main`
- 现有 WebSocket API 保持兼容

---

## 8. 风险评估

### 8.1 技术风险

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| **内存占用增加** | 高 | 中 | 懒加载 Agent 上下文 |
| **文件句柄泄漏** | 中 | 高 | 及时关闭文件流 |
| **Agent 间冲突** | 低 | 高 | 严格的 workspace 隔离 |
| **性能下降** | 中 | 中 | 缓存 + 优化查询 |

### 8.2 业务风险

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| **用户困惑** | 中 | 中 | 清晰的 UI + 文档 |
| **误用 @ 提及** | 低 | 低 | 错误提示 + 自动回退 |

---

## 9. 示例配置

### 9.1 创建编程助手 Agent

```json
{
  "name": "coder",
  "displayName": "编程助手",
  "description": "专业的编程助手，擅长代码编写和调试",
  "model": "deepseek-chat",
  "allowedTools": [
    "read_file",
    "write_file",
    "shell",
    "web_search",
    "memory_search"
  ],
  "excludedTools": [],
  "heartbeatInterval": 30,
  "heartbeatActiveHours": "09:00-22:00"
}
```

### 9.2 SOUL.md 示例

```markdown
# SOUL.md - 编程助手

## Name
CodeMaster

## Personality
我是一个专业的编程助手，擅长多种编程语言和框架。
我会用简洁清晰的语言解释技术概念，并提供可执行的代码示例。

## Key Traits
- 技术严谨
- 代码质量导向
- 注重最佳实践
- 友好耐心

## Areas of Expertise
- Java / Spring Boot
- TypeScript / React
- Python / FastAPI
- System Design

## Response Style
Tone: Professional but friendly
Detail Level: Comprehensive with examples
```

---

## 10. 总结

这个重构方案将 JaguarClaw 从**单 Agent 架构**升级为**多 Agent 架构**，核心特性：

1. ✅ **多 Agent 支持** - 无限创建
2. ✅ **独立 Workspace** - 文件隔离
3. ✅ **独立 Soul** - 身份隔离
4. ✅ **工具隔离** - 权限控制
5. ✅ **记忆隔离** - 上下文隔离
6. ✅ **@ 提及** - 灵活路由
7. ✅ **向后兼容** - 平滑迁移

**工作量**：14 天
**风险等级**：中
**优先级**：高（企业级功能）

---

*文档版本：v1.0*
*最后更新：2026-02-28*
