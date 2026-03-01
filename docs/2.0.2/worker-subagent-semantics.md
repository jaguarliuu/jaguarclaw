# Worker / Subagent 语义兼容说明（2.0.2）

## 背景

在 2.0.2 多 Agent 架构中，`agent` 是平级角色模型；`sessions_spawn` 是异步执行机制。  
为避免把执行机制误解为架构层级，语义统一为 **worker execution**。

## 结论

1. `sessions_spawn` 的职责是“派发 worker 任务”，不是“创建多 Agent 层级关系”。
2. 多 Agent 的选择与路由由 `agentId` 决定，与是否调用 `sessions_spawn` 无关。
3. 兼容性保持：
   - 事件名仍保留 `subagent.*`；
   - lane/runKind 仍兼容 `subagent`；
   - 旧 RPC/工具名不变（`sessions_spawn`、`subagent.*`）。

## 运行时约束

1. worker 任务在独立 session/run 中执行，不阻塞主对话。
2. 禁止嵌套派发：worker 任务内不能再次调用 `sessions_spawn`。
3. `sessions_spawn` 允许显式指定目标 `agentId`，用于把任务委派给对应 worker agent。

## 迁移策略

1. 文案层：系统提示、工具描述统一采用 worker 语义。
2. 代码层：新增 worker 语义别名（如 `RunContext.isWorker()`、`createWorker(...)`），底层继续兼容 subagent。
3. 数据/协议层：保持向后兼容，不做破坏式字段改名。
