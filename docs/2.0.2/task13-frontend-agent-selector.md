# Task13 前端落地说明：Agent 选择器与会话绑定

> 日期：2026-03-01
> 范围：`jaguarclaw-ui` 聊天主界面（Workspace）

## 1. 目标

1. 在不破坏现有交互习惯的前提下，支持在聊天入口选择 Agent。
2. 会话级展示 Agent 绑定信息，降低多 Agent 场景下的上下文混淆。
3. 与现有视觉语言保持一致（按钮体系、灰阶 token、面板形态、字体体系）。

## 2. 交互与数据设计

1. `useAgents` 新增 Agent 列表加载能力（`agent.list`），提供默认 Agent 解析。
2. `useChat` 新增 `selectedAgentId` 状态：
- 新建会话时将 `agentId` 写入 `session.create`。
- 发送消息时将 `agentId` 写入 `agent.run`。
- 选中会话时自动同步该会话的 `agentId`。
3. `SessionSidebar` 每个会话增加轻量 Agent 标记。
4. `MessageInput` 在工具栏右侧增加 Agent 下拉入口，位置与 Model 选择并列。

## 3. 风格统一策略

1. 复用现有 `toolbar-btn` / `model-btn` 体系，不引入新按钮范式。
2. 下拉面板采用与 `ModelSelector` 同级的弹层语义：
- 同类边框、圆角、阴影 token
- 同类分组头部与列表 hover/selected 状态
3. 会话 Agent 徽标采用低饱和灰阶背景，激活态仅做透明度适配。
4. 不改动主布局和主题变量，不新增高冲击动画。

## 4. 兼容策略

1. 对旧会话（可能缺少 `agentId`）前端回退到 `main`。
2. Agent 列表不可用时，不阻断聊天流程，使用回退 Agent。
3. 保持原有 MCP/数据源/模型选择流程不变。

## 5. 验证结果

1. `npm run type-check` 通过。
2. `npm run build` 通过。

## 6. 后续建议（Task14/15）

1. Task14 增加 `@agent` 提及路由后，优先级建议为：`@mention > 当前会话绑定 > 默认 Agent`。
2. Task15 设置页接入 Agent 管理后，可在 UI 中补充 Agent 状态（enabled/default）可视化提示。
