# Task 15 完成记录：设置页扩展（Agent / Memory Scope / MCP / Skill Scope）

日期：2026-03-01

## 目标

- 在设置页接入 Agent 管理入口与页面。
- 在 Memory / MCP / Skills 中补齐多 Agent 与作用域（global / agent）维度的可视化与配置能力。
- 保持当前设置页视觉语言与交互风格一致，不做破坏性改版。

## 关键实现

1. 设置页导航与路由接入 Agent 管理
- 新增 `settings.nav.agents` 导航项与图标。
- `SettingsView` 新增 `AgentsSection` 渲染分支。

2. Skills 设置页作用域视图
- 新增作用域视图切换：`effective (agent)` / `global`。
- 新增 Agent 选择器，用于查看指定 Agent 的生效技能（global + agent）。
- `skills.list / skills.get` 请求携带 `scope` 与 `agentId`。

3. MCP 设置页作用域化
- 新增筛选栏：按 `scope` 与 `agent` 过滤服务器列表。
- 表格新增 `scope`、`agent` 列，明确全局与 Agent 专属服务器。
- 新增/编辑弹窗支持 `scope` + `agentId` 配置（scope=agent 时必选 agent）。
- 前端 MCP 请求统一做 scope 归一化（`GLOBAL/AGENT` <-> `global/agent`）。

4. Memory 设置页双层模型表达
- 新增“默认检索策略 + Agent 视角”工具栏。
- 新增 Global / Agent / Both 三张说明卡，明确双层记忆语义。
- 增加 scope-aware memory 工具签名提示，便于运营与调试。

5. 后端兼容扩展（小改动）
- `skills.list` 与 `skills.get` 增加 `scope=global|effective` 支持。
- `SkillRegistry` 新增全局技能读取/激活入口，支撑前端“仅全局”视图。

## 主要变更文件

- `jaguarclaw-ui/src/views/SettingsView.vue`
- `jaguarclaw-ui/src/components/settings/SettingsSidebar.vue`
- `jaguarclaw-ui/src/components/settings/SkillsSection.vue`
- `jaguarclaw-ui/src/components/settings/McpSection.vue`
- `jaguarclaw-ui/src/components/settings/McpServerModal.vue`
- `jaguarclaw-ui/src/components/settings/MemorySection.vue`
- `jaguarclaw-ui/src/composables/useSkills.ts`
- `jaguarclaw-ui/src/composables/useMcpServers.ts`
- `jaguarclaw-ui/src/i18n/locales/en.ts`
- `jaguarclaw-ui/src/i18n/locales/zh.ts`
- `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/skill/SkillListHandler.java`
- `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/skill/SkillGetHandler.java`
- `src/main/java/com/jaguarliu/ai/skills/registry/SkillRegistry.java`

## 验证结果

- 前端类型检查通过：`cd jaguarclaw-ui && npm run type-check`
- 前端构建通过：`cd jaguarclaw-ui && npm run build`
- 后端定向测试通过：`mvn -Dtest=SkillScopeRegistryTest test`
