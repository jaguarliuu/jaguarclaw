# JaguarClaw 2.0.2 Test Matrix

日期：2026-03-01
范围：Multi-Agent 架构升级（Prompt/Memory/MCP/Skill/UI）

## 1. 自动化测试矩阵

| 维度 | 用例/命令 | 期望结果 |
|---|---|---|
| 后端全量回归 | `mvn test` | 全部通过 |
| 前端类型检查 | `cd jaguarclaw-ui && npm run type-check` | 通过 |
| 前端构建 | `cd jaguarclaw-ui && npm run build` | 通过 |
| Agent 控制面 | `mvn -Dtest=AgentProfileHandlersTest test` | CRUD + 默认约束通过 |
| 会话/执行绑定 | `mvn -Dtest=AgentRunWithAgentIdTest test` | `session.create/agent.run` 传递 agentId 正确 |
| Mention 路由 | `mvn -Dtest=ChatRouterMentionTest test` | `@agent` 路由与回退正确 |
| Prompt 分层 | `mvn -Dtest=SystemPromptFacetTest test` | Kernel + Facets 行为正确 |
| Memory 双层 | `mvn -Dtest=MemorySearchScopeTest,MemoryScopeToolsTest test` | scope 检索与工具参数正确 |
| MCP 作用域 | `mvn -Dtest=McpScopeTest test` | global/agent 工具可见性正确 |
| Skill 作用域 | `mvn -Dtest=SkillScopeRegistryTest test` | global + agent 合并与覆盖正确 |

## 2. 手工验收矩阵（核心场景）

| 场景 | 步骤 | 期望 |
|---|---|---|
| A1: 新建 Agent 并设默认 | 设置 > Agents 新增 profile 并设默认 | 聊天页新会话默认绑定新 Agent |
| A2: 会话固定 Agent | 创建两个会话分别绑定不同 Agent | 会话切换后输入框 Agent 与执行目标同步 |
| A3: Mention 指定 Agent | 输入 `@planner 帮我拆任务` | 后端路由至 planner；提示词去除 mention 前缀 |
| M1: Memory 双层检索 | 用工具写入 global 与 agent memory 后检索 both | 返回结果包含两层，agent 命中优先 |
| K1: MCP global 可见 | 新增 global MCP server | 所有 Agent 执行时可见该 server 工具 |
| K2: MCP agent 私有 | 新增 agent scope MCP server 到 planner | 仅 planner 可见该 server 工具 |
| S1: Skill effective 视图 | 设置 > Skills 切换到 effective + 指定 Agent | 列表为 global + agent 合并视图 |
| S2: Skill global 视图 | 设置 > Skills 切换到 global | 仅显示全局技能 |
| R1: 开关回滚演练 | 在配置中关闭单项 `feature.*` | 对应模块回退兼容行为，不影响其他模块 |

## 3. 性能与稳定性基线

| 指标 | 基线要求 |
|---|---|
| `agent.run` 首 token 延迟 | 不高于 2.0.1 基线 +15% |
| `memory_search(scope=both)` P95 | 不高于 2.0.1 基线 +20% |
| MCP 工具注册启动耗时 | 不高于 2.0.1 基线 +20% |
| 前端首屏交互可用 | 不高于 2.0.1 基线 +15% |

## 4. 缺陷分级与放行标准

- P0/P1 缺陷必须清零。
- P2 允许带风险说明放行，需明确缓解与回归时间。
- 发布前必须完成一次全链路冒烟（创建会话 -> 指定 Agent -> 触发工具 -> 结束）。
