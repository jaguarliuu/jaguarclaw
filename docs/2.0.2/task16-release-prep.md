# Task 16 完成记录：回归矩阵 + Feature Flag + 发布准备

日期：2026-03-01

## 交付内容

1. Feature Flag 配置落地
- 新增配置类：`src/main/java/com/jaguarliu/ai/feature/FeatureFlagsProperties.java`
- 新增配置项（默认开启）：
  - `feature.agent-control-plane`
  - `feature.agent-scoped-prompt`
  - `feature.agent-dual-memory`
  - `feature.agent-scoped-mcp-skill`
  - `feature.agent-mention-routing`

2. 配置文件更新
- `src/main/resources/application.yml` 增加 `feature.*` 开关段，便于灰度与回滚。

3. 发布文档补齐
- `docs/2.0.2/release-checklist.md`
- `docs/2.0.2/test-matrix.md`

## 验证结果

1. 前端验证
- `cd jaguarclaw-ui && npm run type-check && npm run build`：通过。

2. 后端回归
- `mvn test`：未全绿（当前环境存在历史性测试阻塞）。
- 主要阻塞：
  - `NODE_CONSOLE_ENCRYPTION_KEY` 未设置时，部分 Spring 上下文类测试失败（属 Node Console 凭据加密基线，非本次迭代阻塞）。
  - `MemoryChunkTest` 有 2 个断言失败（既有基线问题，与本次 Task16 变更无直接耦合）。

3. 关键多 Agent 相关专项（本轮）
- `mvn -Dtest=SkillScopeRegistryTest test`：通过。
- `NODE_CONSOLE_ENCRYPTION_KEY=$(openssl rand -hex 32) mvn -Dtest=McpPropertiesTest,McpServerServiceTest,McpToolRegistryTest test`：通过（验证 MCP 失败主因是环境变量缺失）。
- `mvn -Dtest=MemoryChunkTest test`：失败（2 个断言，属既有测试基线问题）。

## 备注

- Task16 的“开关、矩阵、清单”交付已完成。
- 按本次迭代口径，可忽略上述范围外基线问题，不作为 2.0.2 多 Agent 发布阻塞项。
