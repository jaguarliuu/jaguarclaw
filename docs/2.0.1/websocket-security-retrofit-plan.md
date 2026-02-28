# JaguarClaw 2.0.1 WebSocket 安全改造方案（Win/Mac 本地版）

> 日期：2026-02-28  
> 版本目标：`2.0.1`  
> 适用范围：桌面端 Win/Mac 本地运行（无登录体系）  
> 说明：本阶段不讨论 `wss` 部署细节，仅做代码层安全基线

---

## 1. 背景与目标

当前项目是桌面端本地应用，不存在传统“用户登录 -> JWT”流程。  
本次目标是：在不引入登录系统的前提下，建立**设备主体（principal）安全模型**，避免裸 WebSocket 被滥用（尤其是误暴露到公网机器时）。

### 1.1 核心目标

1. 建立“设备身份”而不是“用户身份”
2. 首次启动可自动引导，不增加普通用户操作负担
3. RPC 具备基础鉴权与高危能力保护
4. 会话/消息/运行数据按设备主体隔离
5. 加入限流、重放防护与审计

### 1.2 非目标

1. 不建设账号系统与登录页
2. 不接入外部 IAM/OAuth
3. 不在 2.0.1 实现 Linux 严格配对流程（仅保留预设方案）

---

## 2. 当前风险映射（基于现有代码）

| 风险点 | 当前现状 | 影响 | 代码位置 |
|---|---|---|---|
| 连接无身份 | `GatewayWebSocketHandler` 直接接入 | 任意连接可发 RPC | `src/main/java/com/jaguarliu/ai/gateway/ws/GatewayWebSocketHandler.java` |
| 连接无主体上下文 | `ConnectionManager` 只存 session | 无 principal 绑定 | `src/main/java/com/jaguarliu/ai/gateway/ws/ConnectionManager.java` |
| RPC 无鉴权前置 | `RpcRouter` 仅做 method 路由 | 敏感方法可直调 | `src/main/java/com/jaguarliu/ai/gateway/rpc/RpcRouter.java` |
| 高危 RPC 开放 | `tool.execute`、`llm.config.save` 等 | 资源滥用/配置篡改 | `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/tool/ToolExecuteHandler.java`、`src/main/java/com/jaguarliu/ai/gateway/rpc/handler/llm/LlmConfigHandler.java` |
| 数据无 owner | 核心实体无 owner 字段 | 隔离边界不清晰 | `src/main/java/com/jaguarliu/ai/storage/entity/SessionEntity.java`、`src/main/java/com/jaguarliu/ai/storage/entity/RunEntity.java`、`src/main/java/com/jaguarliu/ai/storage/entity/MessageEntity.java` |

---

## 3. 2.0.1 总体架构（本地设备主体）

```text
Desktop App (Win/Mac local client)
  -> /ws connect (localhost)
  -> auth.local.bootstrap (首次自动注册设备主体)
  -> access_token / refresh_token
  -> 后续 RPC 全部带 token

Gateway:
  -> ConnectionManager 绑定 principalId(deviceId)
  -> RpcRouter 前置: Auth -> Permission -> RateLimit -> Replay
  -> Service/Repository owner_principal_id 校验
  -> Audit
```

### 3.1 主体模型

- 主体字段：`principal_id`（建议等于 `device_id`）
- 角色字段：`role`（默认 `local_admin`）
- 不再使用 `user_id` 术语

---

## 4. 详细设计

## 4.1 首次启动自动引导（Win/Mac）

### 流程

1. 客户端首次启动生成并持久化 `device_id`（本地保存）。
2. 建立 WebSocket 后先调用 `auth.local.bootstrap`。
3. 服务端校验：
   - 来源连接为本机（loopback）
   - `device_id` 格式合法
   - 请求频率不过载
4. 若设备不存在：注册设备主体并签发 token。
5. 若设备已存在：直接签发新 access token（或 refresh）。

### 特性

- 普通用户“零交互”（无需手动输入配对码）
- 仅允许本地连接触发 bootstrap
- 给后续 Linux 严格模式保留接口扩展点

### 代码改造点

- 新增：`auth.local.bootstrap` RPC handler
- 新增：`auth.refresh` RPC handler
- 修改：`src/main/java/com/jaguarliu/ai/gateway/ws/GatewayWebSocketHandler.java`
- 修改：`src/main/java/com/jaguarliu/ai/gateway/ws/ConnectionManager.java`

---

## 4.2 连接上下文与 Token 验证

### 设计

`ConnectionManager` 从 `connectionId -> WebSocketSession` 升级为 `connectionId -> ConnectionContext`，包含：

- `connectionId`
- `principalId`
- `role`
- `clientIp`
- `authenticated`
- `session`

### 规则

1. 未认证连接只允许：`ping`、`auth.local.bootstrap`、`auth.refresh`
2. 其他方法统一返回 `UNAUTHORIZED`

---

## 4.3 RPC 权限模型

### 权限分层

- `PUBLIC`：`ping`、`auth.local.bootstrap`、`auth.refresh`
- `READ`：`session.get/list`、`message.list`、`run.get`
- `WRITE`：`session.create/delete`、`agent.run/cancel`、`soul.save`
- `CONFIG`：`llm.config.*`、`tools.config.*`、`mcp.servers.*`
- `DANGEROUS`：`tool.execute`（高风险工具）

### 默认角色策略

- `local_admin`：允许 `READ/WRITE/CONFIG`，`DANGEROUS` 可再叠加 HITL
- 后续如需限制，可新增 `local_limited`

---

## 4.4 数据隔离（owner_principal_id）

### 字段方案

核心表新增 `owner_principal_id`：

- `sessions.owner_principal_id`
- `runs.owner_principal_id`
- `messages.owner_principal_id`

### 迁移策略

1. V16 新增字段（可空）+ 索引
2. 启动时回填历史数据为“本机默认 principal”
3. 业务查询统一切换为 owner 条件
4. 稳定后再升级为 `NOT NULL`

### 代码改造点

- 实体：`SessionEntity`、`RunEntity`、`MessageEntity`
- 仓储：`SessionRepository`、`RunRepository`、`MessageRepository`
- 服务：`SessionService`、`RunService`、`MessageService`

---

## 4.5 限流与预算

### 限流

1. 建连限流：按 IP
2. 消息限流：按 `connectionId`
3. Token 预算：按 `principalId/day`

### 实现建议

- 2.0.1 先用内存窗口算法
- 后续可切 Redis（多实例）

---

## 4.6 重放防护

`RpcRequest` 增加：

- `timestamp`
- `nonce`
- `idempotencyKey`（写操作建议必填）

服务端校验：

1. 时间窗口（例如 10 秒）
2. nonce 去重（TTL 60 秒）
3. 幂等键短期去重

---

## 4.7 高危能力保护

1. `tool.execute` 对危险工具强制二次确认（HITL）或按策略拒绝
2. `llm.config.save`、`mcp.servers.*` 走 `CONFIG` 权限
3. 审计记录高危调用与拒绝原因

---

## 4.8 审计日志

新增 `ws_security_audit_logs` 记录：

- bootstrap success/fail
- auth fail
- permission deny
- rate-limit block
- replay block
- dangerous rpc action

脱敏：`token/apiKey/password` 全量掩码。

---

## 5. 分阶段实施计划

## P0（1 天）：本地设备身份 + 基础鉴权

1. 增加 `auth.local.bootstrap`、`auth.refresh`
2. `ConnectionManager` 接入 principal 上下文
3. `RpcRouter` 增加认证前置
4. 仅放行 `PUBLIC` 方法给未认证连接

**验收**

- 首次启动可自动获取 token 并进入可用状态
- 未认证连接无法调用业务 RPC

## P1（1.5 天）：owner 隔离 + 敏感方法保护 + 限流

1. 增加 `owner_principal_id` 迁移和查询链路
2. 锁定 `tool.execute`、`llm.config.*` 等敏感方法
3. 加入连接/消息/token 限流

**验收**

- 所有核心数据查询都带 owner 约束
- 高危 RPC 未授权时拒绝

## P2（1 天）：重放防护 + 审计

1. `RpcRequest` 扩展防重放字段
2. Replay Guard + 幂等控制
3. 安全审计表与查询能力

**验收**

- 重复 nonce 请求被阻断
- 安全拒绝事件可追溯

---

## 6. 实施步骤清单（工程任务级）

1. 新增 security 配置（desktop local 模式）
2. 新增 auth.local.bootstrap / auth.refresh RPC
3. 升级 `ConnectionManager` 为 principal context
4. `RpcRouter` 接入 auth/permission/rate/replay 前置链路
5. V15 增加 `owner_principal_id`（pg/sqlite）
6. 实体、仓储、服务切 owner principal 查询
7. 敏感 RPC 保护与高危工具限制
8. 增加限流、预算、防重放
9. 新增安全审计日志
10. 完成测试与回归

---

## 7. 测试计划

## 7.1 单元测试（新增）

- `WsLocalBootstrapHandlerTest`
- `RpcAuthorizationServiceTest`
- `MessageRateLimiterTest`
- `ReplayGuardTest`
- `SessionOwnershipByPrincipalTest`

## 7.2 集成测试（新增）

- 首次本地连接自动 bootstrap
- 非本地来源 bootstrap 拒绝
- 未认证调用 `session.list` 被拒绝
- 已认证 principal 正常调用
- 重放请求被拒绝

## 7.3 回归命令

```bash
mvn -q test
```

---

## 8. 配置模板（Win/Mac 默认）

```yaml
security:
  mode: local_desktop
  ws:
    local-only-bootstrap: true
    allow-anonymous-methods: [ping, auth.local.bootstrap, auth.refresh]
  auth:
    access-token-minutes: 30
    refresh-token-days: 30
  rate-limit:
    connection-per-minute-per-ip: 20
    message-per-minute-per-connection: 60
    token-per-day-per-principal: 1000000
  replay:
    enabled: true
    timestamp-window-seconds: 10
    nonce-ttl-seconds: 60
```

---

## 9. 前端对接建议（Win/Mac）

1. 应用启动后先调用 `auth.local.bootstrap`
2. 保存 `access_token/refresh_token` 到系统安全存储（DPAPI/Keychain）
3. 所有业务 RPC 携带 token
4. `AUTH_EXPIRED` 时调用 `auth.refresh` 自动续期
5. RPC 自动补 `timestamp/nonce/idempotencyKey`

---

## 10. 风险与回滚

### 风险

1. owner 字段迁移导致历史数据查询异常
2. bootstrap 逻辑失误导致旧客户端无法启动
3. 限流阈值设置过紧

### 回滚

1. 关闭强鉴权开关，仅保留审计（临时）
2. owner 校验降级为告警模式
3. 调整限流阈值并恢复服务

---

## 11. 验收标准（2.0.1）

- [ ] Win/Mac 首次启动无需手工配对即可完成身份初始化
- [ ] 未认证连接仅能访问 `PUBLIC` 方法
- [ ] 核心数据访问按 `owner_principal_id` 隔离
- [ ] 高危 RPC 有权限门禁
- [ ] 限流与重放防护生效
- [ ] 安全审计可追溯

---

## 12. Linux 预设安全方案（保留，不纳入本次实施）

Linux/公网环境建议启用严格模式 `strict_pairing`：

1. 首次连接走 `auth.bootstrap.begin/complete`
2. 需要一次性配对码或管理员确认
3. 设备公私钥 challenge 签名校验
4. 配对超时自动失效，需重新配对
5. 全链路建议强制 `wss`

该模式作为后续版本扩展，不影响 2.0.1 Win/Mac 本地落地。
