# JaguarClaw 2.0.1 执行拆解（Win/Mac 本地安全方案）

> 对应主文档：`docs/2.0.1/websocket-security-retrofit-plan.md`  
> 原则：提交级拆分、每步可验证、可回滚

---

## 0. 执行规则

1. 每个提交只完成一类改动。
2. 每个提交都提供最小验证命令。
3. 先落 Win/Mac 本地方案；Linux 严格配对仅保留预设文档，不进入本轮代码。

---

## 1. 提交计划总览（推荐 13 个提交）

| 提交 | 目标 | 预计耗时 |
|---|---|---:|
| C01 | 新增 local security 配置骨架 | 0.5h |
| C02 | 连接上下文升级为 principal 模型 | 0.5h |
| C03 | 新增 `auth.local.bootstrap` / `auth.refresh` | 1h |
| C04 | `RpcRouter` 接入认证前置（未认证仅放行 PUBLIC） | 0.5h |
| C05 | method-level 权限映射与校验 | 1h |
| C06 | 敏感 RPC（tool/config）加门禁 | 0.5h |
| C07 | V15 迁移：`owner_principal_id` 字段 | 1h |
| C08 | 实体与仓储 owner principal 化 | 1h |
| C09 | Session/Run/Message 服务 owner 强约束 | 1h |
| C10 | 连接/消息限流 | 1h |
| C11 | token 日预算（按 principal） | 0.5h |
| C12 | replay/idempotency 防护 | 1h |
| C13 | 安全审计 + 测试收口 + Linux 预案文档 | 1.5h |

---

## 2. 分提交实施细节

### C01 - 配置骨架（local_desktop）

**修改文件**
- `src/main/resources/application.yml`
- 新增 `src/main/java/com/jaguarliu/ai/gateway/security/SecurityProperties.java`

**动作**
1. 新增 `security.mode=local_desktop` 及子配置。
2. 默认放行匿名方法：`ping/auth.local.bootstrap/auth.refresh`。

**验证**
- `mvn -q -DskipTests compile`

**建议提交**
- `feat(security): add local desktop security properties`

---

### C02 - 连接上下文升级

**修改文件**
- 新增 `src/main/java/com/jaguarliu/ai/gateway/security/ConnectionPrincipal.java`
- 新增 `src/main/java/com/jaguarliu/ai/gateway/security/ConnectionContext.java`
- 修改 `src/main/java/com/jaguarliu/ai/gateway/ws/ConnectionManager.java`

**动作**
1. `ConnectionManager` 存储 `ConnectionContext`。
2. 支持按 `connectionId` 获取 principal。

**验证**
- 新增 `src/test/java/com/jaguarliu/ai/gateway/ws/ConnectionManagerTest.java`
- `mvn -q -Dtest=ConnectionManagerTest test`

**建议提交**
- `refactor(ws): upgrade connection manager to principal context`

---

### C03 - 本地自动引导 RPC

**修改文件**
- 新增 `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/auth/AuthLocalBootstrapHandler.java`
- 新增 `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/auth/AuthRefreshHandler.java`
- 新增 `src/main/java/com/jaguarliu/ai/gateway/security/AuthTokenService.java`

**动作**
1. 实现 `auth.local.bootstrap`（仅 loopback 连接允许）。
2. 实现 `auth.refresh`。
3. 签发 `access_token/refresh_token`，主体为 `principal_id=device_id`。

**验证**
- 新增 `src/test/java/com/jaguarliu/ai/gateway/security/AuthLocalBootstrapHandlerTest.java`
- `mvn -q -Dtest=AuthLocalBootstrapHandlerTest test`

**建议提交**
- `feat(auth): add local bootstrap and token refresh rpc`

---

### C04 - RpcRouter 认证前置

**修改文件**
- `src/main/java/com/jaguarliu/ai/gateway/rpc/RpcRouter.java`

**动作**
1. 未认证连接仅允许 `PUBLIC` 方法。
2. 非法调用返回 `UNAUTHORIZED`。

**验证**
- 新增 `src/test/java/com/jaguarliu/ai/gateway/rpc/RpcRouterAuthGuardTest.java`
- `mvn -q -Dtest=RpcRouterAuthGuardTest test`

**建议提交**
- `feat(rpc): enforce auth guard with public method allowlist`

---

### C05 - 权限映射

**修改文件**
- 新增 `src/main/java/com/jaguarliu/ai/gateway/security/RpcPermission.java`
- 新增 `src/main/java/com/jaguarliu/ai/gateway/security/RpcAuthorizationService.java`
- 修改 `src/main/java/com/jaguarliu/ai/gateway/rpc/RpcRouter.java`

**动作**
1. 建立 method -> permission 映射。
2. 默认角色 `local_admin`，预留 `local_limited`。

**验证**
- 新增 `src/test/java/com/jaguarliu/ai/gateway/security/RpcAuthorizationServiceTest.java`
- `mvn -q -Dtest=RpcAuthorizationServiceTest test`

**建议提交**
- `feat(rpc): add permission matrix for desktop principal`

---

### C06 - 敏感 RPC 上锁

**修改文件**
- `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/tool/ToolExecuteHandler.java`
- `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/llm/LlmConfigHandler.java`
- `src/main/java/com/jaguarliu/ai/tools/ToolDispatcher.java`

**动作**
1. `tool.execute` 增加危险工具限制/HITL。
2. `llm.config.*` 等走 `CONFIG` 权限。

**验证**
- 新增 `src/test/java/com/jaguarliu/ai/gateway/rpc/handler/SensitiveRpcGuardTest.java`
- `mvn -q -Dtest=SensitiveRpcGuardTest test`

**建议提交**
- `feat(security): protect dangerous and config rpc methods`

---

### C07 - 数据库迁移（owner_principal_id）

**修改文件**
- 新增 `src/main/resources/db/migration/V16__owner_principal_fields.sql`
- 新增 `src/main/resources/db/migration-sqlite/V16__owner_principal_fields.sql`

**动作**
1. `sessions/runs/messages` 新增 `owner_principal_id`。
2. 增加索引，预留回填逻辑。

**验证**
- `mvn -q -DskipTests test-compile`

**建议提交**
- `feat(db): add owner_principal_id columns for data isolation`

---

### C08 - 实体与仓储改造

**修改文件**
- `src/main/java/com/jaguarliu/ai/storage/entity/SessionEntity.java`
- `src/main/java/com/jaguarliu/ai/storage/entity/RunEntity.java`
- `src/main/java/com/jaguarliu/ai/storage/entity/MessageEntity.java`
- `src/main/java/com/jaguarliu/ai/storage/repository/SessionRepository.java`
- `src/main/java/com/jaguarliu/ai/storage/repository/RunRepository.java`
- `src/main/java/com/jaguarliu/ai/storage/repository/MessageRepository.java`

**动作**
1. 增加 `ownerPrincipalId` 字段映射。
2. 增加按 principal 查询方法。

**验证**
- `mvn -q -DskipTests compile`

**建议提交**
- `refactor(storage): add owner principal fields and queries`

---

### C09 - 业务服务 owner 强约束

**修改文件**
- `src/main/java/com/jaguarliu/ai/session/SessionService.java`
- `src/main/java/com/jaguarliu/ai/session/RunService.java`
- `src/main/java/com/jaguarliu/ai/session/MessageService.java`
- `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/session/SessionListHandler.java`
- `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/system/MessageListHandler.java`

**动作**
1. 创建与查询链路全部绑定 `ownerPrincipalId`。
2. 拒绝跨 principal 数据访问。

**验证**
- 新增 `src/test/java/com/jaguarliu/ai/session/SessionOwnershipByPrincipalTest.java`
- `mvn -q -Dtest=SessionOwnershipByPrincipalTest test`

**建议提交**
- `feat(session): enforce owner principal isolation end-to-end`

---

### C10 - 连接/消息限流

**修改文件**
- 新增 `src/main/java/com/jaguarliu/ai/gateway/security/rate/ConnectionRateLimiter.java`
- 新增 `src/main/java/com/jaguarliu/ai/gateway/security/rate/MessageRateLimiter.java`
- 修改 `src/main/java/com/jaguarliu/ai/gateway/ws/GatewayWebSocketHandler.java`
- 修改 `src/main/java/com/jaguarliu/ai/gateway/rpc/RpcRouter.java`

**动作**
1. 连接限流（按 IP）。
2. 消息限流（按 connectionId）。

**验证**
- 新增 `src/test/java/com/jaguarliu/ai/gateway/security/rate/MessageRateLimiterTest.java`
- `mvn -q -Dtest=MessageRateLimiterTest test`

**建议提交**
- `feat(rate): add connection and rpc message rate limiters`

---

### C11 - Token 日预算

**修改文件**
- 新增 `src/main/java/com/jaguarliu/ai/gateway/security/rate/TokenBudgetService.java`
- 修改 LLM token 统计入口

**动作**
1. 按 `principalId/day` 控制预算。
2. 超预算返回 `TOKEN_BUDGET_EXCEEDED`。

**验证**
- 新增 `src/test/java/com/jaguarliu/ai/gateway/security/rate/TokenBudgetServiceTest.java`
- `mvn -q -Dtest=TokenBudgetServiceTest test`

**建议提交**
- `feat(cost): enforce per-principal daily token budget`

---

### C12 - 重放与幂等防护

**修改文件**
- 修改 `src/main/java/com/jaguarliu/ai/gateway/rpc/model/RpcRequest.java`
- 新增 `src/main/java/com/jaguarliu/ai/gateway/security/ReplayGuard.java`
- 修改 `src/main/java/com/jaguarliu/ai/gateway/rpc/RpcRouter.java`

**动作**
1. 增加 `timestamp/nonce/idempotencyKey` 字段。
2. 写操作启用时间窗口 + nonce 缓存 + 幂等键去重。

**验证**
- 新增 `src/test/java/com/jaguarliu/ai/gateway/security/ReplayGuardTest.java`
- `mvn -q -Dtest=ReplayGuardTest test`

**建议提交**
- `feat(security): add rpc replay protection and idempotency`

---

### C13 - 审计与收口

**修改文件**
- 新增 `src/main/resources/db/migration/V16__ws_security_audit_log.sql`
- 新增 `src/main/resources/db/migration-sqlite/V16__ws_security_audit_log.sql`
- 新增 `src/main/java/com/jaguarliu/ai/security/audit/*`
- 更新文档：`docs/2.0.1/websocket-security-retrofit-plan.md`
- 更新文档：`docs/2.0.1/websocket-security-execution-breakdown.md`

**动作**
1. 记录 bootstrap/auth/deny/rate/replay/dangerous 事件。
2. 增加脱敏策略。
3. 保留 Linux 严格配对预设章节（文档即可）。

**验证**
- 新增 `src/test/java/com/jaguarliu/ai/security/audit/SecurityAuditServiceTest.java`
- `mvn -q test`

**建议提交**
- `feat(audit): add websocket security audit and finalize desktop security docs`

---

## 3. 发布前检查

- [ ] Win/Mac 首次启动可自动完成 `auth.local.bootstrap`
- [ ] 未认证连接仅可调用 `PUBLIC` 方法
- [ ] 核心数据读写均按 `owner_principal_id`
- [ ] 敏感 RPC 已加权限保护
- [ ] 限流、预算、重放防护均可触发并返回标准错误
- [ ] 安全审计日志可检索
- [ ] `mvn -q test` 通过

---

## 4. 分支与 PR 建议

1. 分支：`feat/ws-security-desktop-2.0.1`
2. 每 2~3 个提交推送并跑 CI
3. PR 分批评审：
   - 批次 A：C01~C04（本地身份与认证链）
   - 批次 B：C05~C09（权限与数据隔离）
   - 批次 C：C10~C13（限流/重放/审计/收口）
