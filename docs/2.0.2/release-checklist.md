# JaguarClaw 2.0.2 Release Checklist

日期：2026-03-01
版本：2.0.2

## 1. 变更冻结

- [ ] 所有 Task（01-16）代码完成并通过评审
- [ ] `docs/2.0.2/` 下设计与执行文档已同步
- [ ] DB migration（V19/V20/V21）在 SQLite 与 PostgreSQL 均可执行

## 2. 配置与开关

- [ ] 已确认 `feature.*` 开关默认值与发布策略
- [ ] 灰度环境按需关闭开关演练成功
  - [ ] `feature.agent-control-plane`
  - [ ] `feature.agent-scoped-prompt`
  - [ ] `feature.agent-dual-memory`
  - [ ] `feature.agent-scoped-mcp-skill`
  - [ ] `feature.agent-mention-routing`

## 3. 后端验证

- [ ] 编译与单测通过：`mvn test`
- [ ] 关键专项用例通过
  - [ ] `AgentProfileHandlersTest`
  - [ ] `AgentRunWithAgentIdTest`
  - [ ] `ChatRouterMentionTest`
  - [ ] `MemorySearchScopeTest`
  - [ ] `McpScopeTest`
  - [ ] `SkillScopeRegistryTest`

## 4. 前端验证

- [ ] 类型检查通过：`cd jaguarclaw-ui && npm run type-check`
- [ ] 生产构建通过：`cd jaguarclaw-ui && npm run build`
- [ ] 设置页多 Agent 维度手工验收
  - [ ] Agents 管理（增改删、默认 Agent）
  - [ ] Skills 作用域视图（effective/global）
  - [ ] MCP 作用域过滤与编辑（global/agent）
  - [ ] Memory 双层语义展示（global/agent/both）

## 5. 迁移与回滚

- [ ] 迁移前完成数据备份
- [ ] 回滚脚本与步骤可执行并验证
- [ ] 回滚策略可用：通过 `feature.*` 关闭新能力

## 6. 观测与告警

- [ ] 关键日志与指标可观测（run、memory、mcp、skill）
- [ ] 高错误率/高延迟告警阈值已配置
- [ ] 发布窗口内安排值守与升级预案

## 7. 发布签字

- [ ] 产品负责人签字
- [ ] 技术负责人签字
- [ ] QA 负责人签字
- [ ] 发布执行人签字

## 附注（本次迭代范围外）

- `NODE_CONSOLE_ENCRYPTION_KEY` 属于 Node Console 凭据加密基线要求，不作为 2.0.2 多 Agent 迭代发布阻塞项。
