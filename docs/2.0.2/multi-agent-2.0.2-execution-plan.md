# JaguarClaw 2.0.2 Multi-Agent 重构执行方案（任务级）

> 日期：2026-03-01  
> 版本目标：`2.0.2`  
> 文档类型：设计 + 执行计划（可直接按任务实施）

> **For Claude/Codex:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task.

**Goal:** 将 JaguarClaw 从“单主 Agent + 子代理执行机制”演进为“多平级 Agent + 可选 worker 派生执行”的统一架构，并保持向后兼容。  
**Architecture:** 采用“共享运行时引擎 + Agent 作用域解析（prompt/tool/memory/skill/mcp）”模型，避免按 Agent 复制整套运行时实例。  
**Tech Stack:** Spring Boot 3, WebFlux, JPA/Flyway, Vue3 + TS, PostgreSQL/SQLite。

---

## 1. 本次 2.0.2 的关键结论（冻结版）

1. **Prompt 分层重构**：通用内核共享，只有 `soul/tool/memory` 走 Agent 作用域注入。
2. **Memory 双层模型**：`GLOBAL`（共享）+ `AGENT`（私有）两个维度并存，所有 Agent 都可操作两个维度（受策略约束）。
3. **Multi-Agent 与 Subagent 解耦**：多 Agent 是平级角色模型；subagent 是异步执行机制，不是多 Agent 的组织关系。
4. **工具域模型收敛**：内置工具默认全 Agent 可用；仅 `MCP` 和 `Skill` 区分 `global` 与 `agent` 作用域。

---

## 2. 目标架构（2.0.2）

### 2.1 逻辑分层

1. **Agent Control Plane**
- Agent Profile 的 CRUD、默认 Agent、启停状态、配额。

2. **Agent Scoped Runtime Plane**
- 共享 `AgentRuntime`，按 `RunContext.agentId` 动态解析 prompt/tool/memory/skill/mcp。

3. **Worker Execution Plane**
- 保留 `sessions_spawn`（后续可加 `worker_spawn` 别名），负责异步任务，不承担多 Agent 建模职责。

### 2.2 Prompt Pipeline（重构后）

1. `PromptKernel`（全局共享）
- Identity/Safety/Planning/Runtime/通用行为协议。

2. `SoulFacet`（Agent 作用域）
- 注入当前 Agent 的 soul 内容。

3. `ToolFacet`（Agent 作用域）
- 注入当前 run 的可见工具清单（内置 + MCP + Skill）。

4. `MemoryFacet`（Agent 作用域）
- 注入双层记忆规则、默认检索策略、写入建议。

最终公式：`Prompt = Kernel + SoulFacet + ToolFacet + MemoryFacet + (可选 DataSource/MCP prompt additions)`。

### 2.3 Memory Model（重构后）

1. `scope=GLOBAL`
- 全局共享，跨 Agent 可见。

2. `scope=AGENT + agent_id`
- 私有记忆，仅对应 Agent 可见。

3. 检索默认策略
- 默认 `scope=both`，排序优先 `AGENT` 后 `GLOBAL`。

4. 工具接口
- `memory_search(query, scope=agent|global|both)`
- `memory_write(target, content, scope=agent|global)`
- `memory_get(path, scope=agent|global)`

### 2.4 Tool Domain（重构后）

1. Builtin 工具
- 默认全局可见（仍可由策略层 deny）。

2. MCP 工具
- 支持 `scope=global|agent`。

3. Skill 工具/索引
- 支持 `scope=global|agent`（目录与索引隔离）。

---

## 3. 实施阶段与里程碑

### M1（Week 1-2）
1. Agent 控制面（持久化 + RPC）落地。
2. `session.create` / `agent.run` 真正按 agentId 执行。

### M2（Week 3-4）
1. Prompt 分层（Kernel + Facets）完成。
2. Memory 双层（模型 + 工具 + 检索）完成。

### M3（Week 5-6）
1. MCP/Skill 的 global + agent 域模型完成。
2. 前端 Agent 选择/@提及与设置页落地。
3. 回归/灰度/发布。

---

## 4. 任务级执行计划

### 当前进度（2026-03-01）

- [x] Task 01: Agent Profile 持久化模型
- [x] Task 02: Agent 控制面服务与 RPC
- [x] Task 03: Session/Run 链路绑定 agentId
- [x] Task 04: Prompt 分层框架（Kernel + Facets）
- [x] Task 05: Soul/Heartbeat 服务 Agent 作用域化
- [x] Task 06: Memory 双层数据模型迁移
- [x] Task 07: Memory Store 与检索服务双层化
- [x] Task 08: Memory 工具升级（scope 参数）
- [x] Task 09: Tool 可见性聚合器（builtin + mcp + skill）
- [x] Task 10: MCP 作用域模型（global/agent）
- [x] Task 11: Skill 作用域模型（global/agent）
- [x] Task 12: Multi-Agent 与 Worker Subagent 语义解耦
- [x] Task 13: 前端 Agent 选择器与会话绑定
- [x] Task 14: @ 提及路由与输入体验
- [x] Task 15: 设置页扩展（Agent、Memory Scope、MCP/Skill Scope）
- [x] Task 16: 回归测试矩阵 + Feature Flag + 发布准备

## Task 01: Agent Profile 持久化模型（控制面基线）

**Files:**
- Create: `src/main/resources/db/migration/V19__create_agent_profile.sql`
- Create: `src/main/resources/db/migration-sqlite/V19__create_agent_profile.sql`
- Create: `src/main/java/com/jaguarliu/ai/agents/entity/AgentProfileEntity.java`
- Create: `src/main/java/com/jaguarliu/ai/agents/repository/AgentProfileRepository.java`
- Test: `src/test/java/com/jaguarliu/ai/agents/AgentProfileMigrationTest.java`

**Step 1: Write failing test**
- 验证 `agent_profile` 表、唯一索引（`name`）、默认列存在。

**Step 2: Run test (expect FAIL)**
- Run: `mvn -Dtest=AgentProfileMigrationTest test`

**Step 3: Implement schema + entity/repository**
- `name/display_name/description/enabled/is_default/model/allowed_tools/excluded_tools/workspace_path/...`。

**Step 4: Run test (expect PASS)**
- Run: `mvn -Dtest=AgentProfileMigrationTest test`

**Step 5: Commit**
- `feat(agent): create persistent agent_profile model`

---

## Task 02: Agent 控制面服务与 RPC（agent.list/create/get/update/delete）

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/agents/service/AgentProfileService.java`
- Create: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/agent/AgentListHandler.java`
- Create: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/agent/AgentCreateHandler.java`
- Create: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/agent/AgentGetHandler.java`
- Create: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/agent/AgentUpdateHandler.java`
- Create: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/agent/AgentDeleteHandler.java`
- Test: `src/test/java/com/jaguarliu/ai/gateway/rpc/handler/agent/AgentProfileHandlersTest.java`

**Step 1: Write failing tests**
- 覆盖 CRUD、默认 Agent 约束、禁删默认 Agent。

**Step 2: Run tests (expect FAIL)**
- Run: `mvn -Dtest=AgentProfileHandlersTest test`

**Step 3: Implement service + handlers**
- 支持 workspace 自动创建（`workspace/agents/<agentName>`）。

**Step 4: Run tests (expect PASS)**
- Run: `mvn -Dtest=AgentProfileHandlersTest test`

**Step 5: Commit**
- `feat(agent): add profile control plane RPC handlers`

---

## Task 03: Session/Run 链路绑定 agentId（主执行入口打通）

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/session/SessionCreateHandler.java`
- Modify: `src/main/java/com/jaguarliu/ai/session/SessionService.java`
- Modify: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/agent/AgentRunHandler.java`
- Modify: `src/main/java/com/jaguarliu/ai/session/RunService.java`
- Modify: `src/main/java/com/jaguarliu/ai/runtime/RunContext.java`
- Test: `src/test/java/com/jaguarliu/ai/gateway/rpc/handler/agent/AgentRunWithAgentIdTest.java`

**Step 1: Write failing tests**
- `session.create` 支持 `agentId`。
- `agent.run` 优先使用 session 绑定 agentId（payload 可覆盖）。
- 不传 `agentId` 回退默认 `main`。

**Step 2: Run tests (expect FAIL)**
- Run: `mvn -Dtest=AgentRunWithAgentIdTest test`

**Step 3: Implement**
- 打通 `session -> run -> RunContext` 的 agentId 传递。

**Step 4: Run tests (expect PASS)**
- Run: `mvn -Dtest=AgentRunWithAgentIdTest test`

**Step 5: Commit**
- `feat(agent): bind session and run execution to agentId`

---

## Task 04: Prompt 分层框架（Kernel + Facets）

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/runtime/prompt/PromptAssemblyContext.java`
- Create: `src/main/java/com/jaguarliu/ai/runtime/prompt/PromptFacet.java`
- Create: `src/main/java/com/jaguarliu/ai/runtime/prompt/KernelPromptFacet.java`
- Create: `src/main/java/com/jaguarliu/ai/runtime/prompt/SoulPromptFacet.java`
- Create: `src/main/java/com/jaguarliu/ai/runtime/prompt/ToolPromptFacet.java`
- Create: `src/main/java/com/jaguarliu/ai/runtime/prompt/MemoryPromptFacet.java`
- Modify: `src/main/java/com/jaguarliu/ai/runtime/SystemPromptBuilder.java`
- Test: `src/test/java/com/jaguarliu/ai/runtime/SystemPromptFacetTest.java`

**Step 1: Write failing tests**
- 验证 Kernel 仅构建一次模板。
- 同一 Kernel 下不同 agent 的 soul/tool/memory facet 可替换。

**Step 2: Run tests (expect FAIL)**
- Run: `mvn -Dtest=SystemPromptFacetTest test`

**Step 3: Implement facet pipeline**
- 保持原 `PromptMode` 兼容接口。

**Step 4: Run tests (expect PASS)**
- Run: `mvn -Dtest=SystemPromptFacetTest test`

**Step 5: Commit**
- `refactor(prompt): split system prompt into kernel and scoped facets`

---

## Task 05: Soul/Heartbeat 服务 Agent 作用域化

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/agents/context/AgentWorkspaceResolver.java`
- Modify: `src/main/java/com/jaguarliu/ai/soul/SoulConfigService.java`
- Modify: `src/main/java/com/jaguarliu/ai/heartbeat/HeartbeatConfigService.java`
- Modify: `src/main/java/com/jaguarliu/ai/heartbeat/HeartbeatScheduler.java`
- Modify: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/soul/SoulGetHandler.java`
- Modify: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/soul/SoulSaveHandler.java`
- Test: `src/test/java/com/jaguarliu/ai/soul/AgentScopedSoulConfigServiceTest.java`

**Step 1: Write failing tests**
- `soul.get/save` 按 `agentId` 读写对应目录。
- 心跳配置按 agent 独立加载。

**Step 2: Run tests (expect FAIL)**
- Run: `mvn -Dtest=AgentScopedSoulConfigServiceTest test`

**Step 3: Implement**
- 引入 `workspace/agents/<agent>/SOUL.md`、`HEARTBEAT.md`。

**Step 4: Run tests (expect PASS)**
- Run: `mvn -Dtest=AgentScopedSoulConfigServiceTest test`

**Step 5: Commit**
- `feat(agent): scope soul and heartbeat config by agent workspace`

---

## Task 06: Memory 双层数据模型迁移

**Files:**
- Create: `src/main/resources/db/migration/V20__memory_dual_scope.sql`
- Create: `src/main/resources/db/migration-sqlite/V20__memory_dual_scope.sql`
- Modify: `src/main/java/com/jaguarliu/ai/memory/index/MemoryChunkEntity.java`
- Modify: `src/main/java/com/jaguarliu/ai/memory/index/MemoryChunkRepository.java`
- Test: `src/test/java/com/jaguarliu/ai/memory/MemoryDualScopeMigrationTest.java`

**Step 1: Write failing tests**
- 验证 `memory_chunks` 新增 `scope`、`agent_id` 字段及索引。

**Step 2: Run tests (expect FAIL)**
- Run: `mvn -Dtest=MemoryDualScopeMigrationTest test`

**Step 3: Implement migration**
- 旧数据回填为 `scope=GLOBAL`。

**Step 4: Run tests (expect PASS)**
- Run: `mvn -Dtest=MemoryDualScopeMigrationTest test`

**Step 5: Commit**
- `feat(memory): add dual-scope fields for global and agent memory`

---

## Task 07: Memory Store 与检索服务双层化

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/memory/store/MemoryStore.java`
- Modify: `src/main/java/com/jaguarliu/ai/memory/search/MemorySearchService.java`
- Modify: `src/main/java/com/jaguarliu/ai/memory/index/MemoryIndexer.java`
- Create: `src/main/java/com/jaguarliu/ai/memory/model/MemoryScope.java`
- Test: `src/test/java/com/jaguarliu/ai/memory/MemorySearchScopeTest.java`

**Step 1: Write failing tests**
- `scope=agent` 仅返回该 agent 私有结果。
- `scope=global` 仅返回共享结果。
- `scope=both` 返回混合并按 agent 优先排序。

**Step 2: Run tests (expect FAIL)**
- Run: `mvn -Dtest=MemorySearchScopeTest test`

**Step 3: Implement**
- 检索接口支持 scope 参数和 agentId 参数。

**Step 4: Run tests (expect PASS)**
- Run: `mvn -Dtest=MemorySearchScopeTest test`

**Step 5: Commit**
- `feat(memory): implement global+agent dual-scope search and storage`

---

## Task 08: Memory 工具升级（scope 参数）

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/tools/builtin/MemoryWriteTool.java`
- Modify: `src/main/java/com/jaguarliu/ai/tools/builtin/MemorySearchTool.java`
- Modify: `src/main/java/com/jaguarliu/ai/tools/builtin/ReadFileTool.java` (仅必要兼容)
- Modify: `src/main/java/com/jaguarliu/ai/runtime/SystemPromptBuilder.java`
- Test: `src/test/java/com/jaguarliu/ai/tools/builtin/MemoryScopeToolsTest.java`

**Step 1: Write failing tests**
- 工具 schema 暴露 `scope`。
- 默认行为符合 `both/search`、`agent/write` 策略。

**Step 2: Run tests (expect FAIL)**
- Run: `mvn -Dtest=MemoryScopeToolsTest test`

**Step 3: Implement**
- 在 `ToolExecutionContext` 中读取 `agentId`。

**Step 4: Run tests (expect PASS)**
- Run: `mvn -Dtest=MemoryScopeToolsTest test`

**Step 5: Commit**
- `feat(memory): add scope-aware memory tools for agent/global operations`

---

## Task 09: Tool 可见性聚合器（builtin + mcp + skill）

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/tools/ToolVisibilityResolver.java`
- Modify: `src/main/java/com/jaguarliu/ai/tools/ToolRegistry.java`
- Modify: `src/main/java/com/jaguarliu/ai/runtime/AgentRuntime.java`
- Modify: `src/main/java/com/jaguarliu/ai/runtime/ContextBuilder.java`
- Test: `src/test/java/com/jaguarliu/ai/tools/ToolVisibilityResolverTest.java`

**Step 1: Write failing tests**
- Builtin 默认全可见。
- MCP/Skill 仅按 scope 可见。
- 最终工具集 obey：`AgentPolicy ∩ Strategy ∩ Skill`。

**Step 2: Run tests (expect FAIL)**
- Run: `mvn -Dtest=ToolVisibilityResolverTest test`

**Step 3: Implement**
- `toOpenAiTools(...)` 调整为通过 resolver 产出。

**Step 4: Run tests (expect PASS)**
- Run: `mvn -Dtest=ToolVisibilityResolverTest test`

**Step 5: Commit**
- `feat(tooling): resolve tool visibility by builtin/mcp/skill domains`

---

## Task 10: MCP 作用域模型（global/agent）

**Files:**
- Create: `src/main/resources/db/migration/V21__mcp_scope_fields.sql`
- Create: `src/main/resources/db/migration-sqlite/V21__mcp_scope_fields.sql`
- Modify: `src/main/java/com/jaguarliu/ai/mcp/persistence/McpServerEntity.java`
- Modify: `src/main/java/com/jaguarliu/ai/mcp/service/McpServerService.java`
- Modify: `src/main/java/com/jaguarliu/ai/mcp/tools/McpToolRegistry.java`
- Test: `src/test/java/com/jaguarliu/ai/mcp/McpScopeTest.java`

**Step 1: Write failing tests**
- `scope=global|agent` 的 MCP server 查询与工具注册行为正确。

**Step 2: Run tests (expect FAIL)**
- Run: `mvn -Dtest=McpScopeTest test`

**Step 3: Implement**
- 新增 `scope`、`agent_id` 字段并改造 registry。

**Step 4: Run tests (expect PASS)**
- Run: `mvn -Dtest=McpScopeTest test`

**Step 5: Commit**
- `feat(mcp): support global and agent-scoped mcp servers`

---

## Task 11: Skill 作用域模型（global/agent）

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/skills/registry/SkillRegistry.java`
- Modify: `src/main/java/com/jaguarliu/ai/skills/watcher/SkillFileWatcher.java`
- Modify: `src/main/java/com/jaguarliu/ai/skills/index/SkillIndexBuilder.java`
- Create: `src/main/java/com/jaguarliu/ai/skills/context/SkillScopeResolver.java`
- Test: `src/test/java/com/jaguarliu/ai/skills/SkillScopeRegistryTest.java`

**Step 1: Write failing tests**
- 支持 `global skills + agent skills` 的合并与同名覆盖优先级。

**Step 2: Run tests (expect FAIL)**
- Run: `mvn -Dtest=SkillScopeRegistryTest test`

**Step 3: Implement**
- agent 目录：`workspace/agents/<agent>/skills`。

**Step 4: Run tests (expect PASS)**
- Run: `mvn -Dtest=SkillScopeRegistryTest test`

**Step 5: Commit**
- `feat(skill): support global and agent-scoped skill registry`

---

## Task 12: Multi-Agent 与 Worker Subagent 语义解耦

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/runtime/SystemPromptBuilder.java`
- Modify: `src/main/java/com/jaguarliu/ai/subagent/SubagentService.java`
- Modify: `src/main/java/com/jaguarliu/ai/tools/builtin/SessionsSpawnTool.java`
- Modify: `src/main/java/com/jaguarliu/ai/runtime/RunContext.java`
- Test: `src/test/java/com/jaguarliu/ai/subagent/SubagentSemanticCompatibilityTest.java`
- Docs: `docs/2.0.2/worker-subagent-semantics.md`

**Step 1: Write failing tests**
- 多 Agent 主流程不依赖 spawn。
- `sessions_spawn` 仍可把任务委派到指定 agent worker。

**Step 2: Run tests (expect FAIL)**
- Run: `mvn -Dtest=SubagentSemanticCompatibilityTest test`

**Step 3: Implement**
- 更新系统提示中“subagent 语义描述”为 worker 执行模型。

**Step 4: Run tests (expect PASS)**
- Run: `mvn -Dtest=SubagentSemanticCompatibilityTest test`

**Step 5: Commit**
- `refactor(subagent): decouple worker semantics from multi-agent model`

---

## Task 13: 前端 Agent 选择器与会话绑定

**Files:**
- Modify: `jaguarclaw-ui/src/types/index.ts`
- Modify: `jaguarclaw-ui/src/composables/useChat.ts`
- Modify: `jaguarclaw-ui/src/components/SessionSidebar.vue`
- Modify: `jaguarclaw-ui/src/components/MessageInput.vue`
- Create: `jaguarclaw-ui/src/composables/useAgents.ts`
- Test: `jaguarclaw-ui/src/composables/__tests__/useChat-agent.spec.ts`

**Step 1: Write failing tests**
- 会话可绑定 agent，发送消息按 agent.run 指定。

**Step 2: Run tests (expect FAIL)**
- Run: `cd jaguarclaw-ui && npm run test -- useChat-agent.spec.ts`

**Step 3: Implement UI + composable**
- 增加 Agent 下拉与 session 徽标展示。

**Step 4: Run tests (expect PASS)**
- Run: `cd jaguarclaw-ui && npm run test -- useChat-agent.spec.ts`

**Step 5: Commit**
- `feat(ui): add agent selector and session agent binding`

---

## Task 14: @ 提及路由与输入体验

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/runtime/ChatRouter.java`
- Modify: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/agent/AgentRunHandler.java`
- Modify: `jaguarclaw-ui/src/components/MessageInput.vue`
- Modify: `jaguarclaw-ui/src/composables/useSlashCommands.ts`
- Test: `src/test/java/com/jaguarliu/ai/runtime/ChatRouterMentionTest.java`

**Step 1: Write failing tests**
- `@coder xxx` 能正确解析并路由。
- 无效 mention 回退默认 agent。

**Step 2: Run tests (expect FAIL)**
- Run: `mvn -Dtest=ChatRouterMentionTest test`

**Step 3: Implement**
- 后端 mention 解析 + 前端提及补全提示。

**Step 4: Run tests (expect PASS)**
- Run: `mvn -Dtest=ChatRouterMentionTest test`

**Step 5: Commit**
- `feat(chat): support @agent mention routing`

---

## Task 15: 设置页扩展（Agent、Memory Scope、MCP/Skill Scope）

**Files:**
- Create: `jaguarclaw-ui/src/components/settings/AgentsSection.vue`
- Modify: `jaguarclaw-ui/src/views/SettingsView.vue`
- Modify: `jaguarclaw-ui/src/components/settings/MemorySection.vue`
- Modify: `jaguarclaw-ui/src/components/settings/McpSection.vue`
- Modify: `jaguarclaw-ui/src/components/settings/SkillsSection.vue`
- Test: `jaguarclaw-ui/src/components/settings/__tests__/agents-section.spec.ts`

**Step 1: Write failing tests**
- Agent CRUD 表单行为与 scope 设置项联动。

**Step 2: Run tests (expect FAIL)**
- Run: `cd jaguarclaw-ui && npm run test -- agents-section.spec.ts`

**Step 3: Implement**
- 设置页增加 Agent 管理与 scope 配置入口。

**Step 4: Run tests (expect PASS)**
- Run: `cd jaguarclaw-ui && npm run test -- agents-section.spec.ts`

**Step 5: Commit**
- `feat(ui-settings): add agent and scope management sections`

---

## Task 16: 回归测试矩阵 + Feature Flag + 发布准备

**Files:**
- Modify: `src/main/resources/application.yml`
- Create: `src/main/java/com/jaguarliu/ai/feature/FeatureFlagsProperties.java`
- Create: `docs/2.0.2/release-checklist.md`
- Create: `docs/2.0.2/test-matrix.md`

**Step 1: Add flags**
- `feature.agent-control-plane`
- `feature.agent-scoped-prompt`
- `feature.agent-dual-memory`
- `feature.agent-scoped-mcp-skill`
- `feature.agent-mention-routing`

**Step 2: Prepare regression suite**
- 后端：`mvn test`
- 前端：`cd jaguarclaw-ui && npm run type-check && npm run build`

**Step 3: Soak and benchmark**
- 重点压测 `agent.run`、`memory_search(scope=both)`、MCP 工具注册耗时。

**Step 4: Release checklist sign-off**
- 文档、回滚脚本、迁移验证、监控告警均完成。

**Step 5: Commit**
- `chore(release): add 2.0.2 flags, test matrix and release checklist`

---

## 5. 验收标准（Definition of Done）

1. 可通过 RPC 动态管理多个 Agent，并在 UI 中选择/展示。
2. 同一用户输入在不同 Agent 下可见工具、soul、memory 策略不同。
3. Prompt 已拆分为 Kernel + Facets，不再复制大段 main prompt。
4. Memory 已支持 `global + agent` 双层写读检索，工具支持 scope。
5. MCP/Skill 已支持 global/agent 作用域并正确合并。
6. 多 Agent 主流程与 subagent worker 语义解耦且兼容旧接口。
7. 不传 `agentId` 仍保持 `main` 兼容行为。

---

## 6. 风险与缓解

1. **风险：Prompt 重构引发行为漂移**
- 缓解：保留旧 builder 开关，A/B 比较同提示词输出差异。

2. **风险：Memory 双层后检索性能下降**
- 缓解：新增 `(scope, agent_id)` 复合索引，限定 topK，并做缓存。

3. **风险：MCP/Skill 作用域切换导致工具“消失”**
- 缓解：UI 明示工具来源（builtin/global/agent），并提供诊断面板。

4. **风险：语义切换影响 subagent 既有流程**
- 缓解：保留 `subagent.*` 事件与 RPC，不做破坏式更名。

---

## 7. 回滚策略

1. 数据层：迁移均向后兼容新增列，禁止 destructive migration。
2. 行为层：通过 feature flag 按模块关闭新能力。
3. 路由层：`agent.run` 在 flag 关闭时回退旧主链路（`main`）。
4. 工具层：ToolVisibilityResolver 关闭后退回旧 `ToolRegistry` 逻辑。

---

## 8. 执行建议

1. 按 Task 01-16 顺序执行，不建议并行跳阶段。
2. 每个 Task 完成后必须跑对应测试并提交。
3. 每周至少一次端到端联调（后端 + UI + 数据迁移）。
4. 先灰度 `main + restricted` 两个 Agent，再全量开放。
