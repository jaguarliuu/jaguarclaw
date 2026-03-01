# Task14 实施说明：@ 提及路由与输入体验

> 日期：2026-03-01

## 1. 路由规则

1. 输入以 `@agentId ` 开头时，触发 mention 路由。
2. mention 指向有效且启用的 Agent：
- 该 Agent 作为本次 `agent.run` 的执行 Agent。
- 提示词中剥离开头 mention 前缀，避免污染模型输入。
3. mention 无效（不存在或禁用）：
- 回退默认 Agent（`isDefault=true`，无则 `main`）。
- 同样剥离 mention 前缀。
4. 无 mention 时保持原链路：`payload.agentId > session.agentId > default`。

## 2. 后端改动

1. 新增 `ChatRouter`：
- 负责解析 leading mention 与最终 Agent 决策。
2. `AgentRunHandler` 接入 `ChatRouter`：
- 在 `prepareRun` 阶段统一决策 agentId + routed prompt。
- mention 无效时记录 fallback 日志。

## 3. 前端输入体验

1. `useSlashCommands` 增加 agent 数据源（`agent.list`），并新增 `filterMentions`。
2. `MessageInput` 在输入首字符为 `@` 时显示 Agent 候选。
3. 选择候选后自动补全为 `@agentId `。
4. 现有 `/` 命令补全行为保持不变。

## 4. 验证

1. 后端：`mvn -Dtest=ChatRouterMentionTest,AgentRunWithAgentIdTest test` 通过。
2. 前端：`npm run type-check`、`npm run build` 通过。
