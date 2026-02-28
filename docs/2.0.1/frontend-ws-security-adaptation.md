# 2.0.1 前端 WebSocket 安全适配计划（已执行）

## 目标

将前端从“裸 RPC 调用”切换到“连接后自动认证 + 失效自动续期 + 安全字段注入”模式，兼容后端新增的：

- `auth.local.bootstrap`
- `auth.refresh`
- `UNAUTHORIZED` 重试逻辑
- `timestamp/nonce/idempotencyKey`

## 适配步骤

1. 连接建立后不立即标记可用，先完成认证握手。
2. 认证流程优先 `auth.refresh`，失败自动回退 `auth.local.bootstrap`。
3. 在本地持久化 `deviceId` 与 token 会话（前端存储）。
4. 所有 RPC 注入安全字段：`timestamp`、`nonce`、`idempotencyKey`。
5. 业务 RPC 若遇 `UNAUTHORIZED`，自动重认证并重试一次。
6. 连接断开时清理 pending 请求，避免悬挂 Promise。

## 本次改动文件

- `jaguarclaw-ui/src/composables/useWebSocket.ts`
- `jaguarclaw-ui/src/types/index.ts`

## 行为变化

1. `connectionState=connected` 现在表示“socket 已连通且认证完成”。
2. 页面加载后首次请求不再依赖 race timing（避免先请求后鉴权）。
3. 重连后会自动恢复认证，不需要用户手工刷新。

## 兼容性说明

1. 旧接口调用方式不变（仍使用 `request(method, payload)`）。
2. 后端若尚未强制 replay 字段，前端新增字段不会破坏兼容。
3. 若后端返回 `UNAUTHORIZED`，前端会尝试自动恢复一次，失败再抛错。

