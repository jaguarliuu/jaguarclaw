# 内网 IM 模块设计文档

**日期**: 2026-03-12
**状态**: 已批准
**作者**: 协作设计

---

## 一、概述

为 MiniClaw 桌面端 AI 助手增加内网即时通讯（IM）模块，支持：
- 文字消息、图片、文件传输
- 去中心化身份认证（无注册/登录）
- 端到端加密 + 消息签名
- 内网节点自动发现（Redis 注册中心）
- 配对授权机制（未授权不可发送任何内容）
- Agent 集成：人→Agent、Agent→Agent 跨节点交互

**技术约束**：
- 内网多网段，不支持组播
- 无中心化用户数据库
- 每个桌面端数据库完全本地
- 使用 Redis 作为可配置的轻量注册中心（不同部门可部署独立 Redis 实例）

---

## 二、身份层

### 密钥对

每个节点首次启动时自动生成两个密钥对，存入本地 SQLite，私钥永不离开本机：

| 密钥 | 算法 | 用途 |
|------|------|------|
| 签名密钥 | Ed25519 | 消息签名、身份证明 |
| 加密密钥 | X25519 | ECDH 密钥协商、消息加密 |

**nodeId** = Ed25519 公钥的 SHA-256 前 16 字节（32 位 hex），全局唯一。

用户仅需配置 **DisplayName**（显示名称）。

### 本地身份存储

```sql
CREATE TABLE im_identity (
  node_id              TEXT PRIMARY KEY,
  display_name         TEXT NOT NULL,
  public_key_ed25519   TEXT NOT NULL,
  public_key_x25519    TEXT NOT NULL,
  private_key_ed25519  BLOB NOT NULL,  -- 存储时可用本地主密钥加密
  private_key_x25519   BLOB NOT NULL,
  created_at           TIMESTAMP NOT NULL
);
```

---

## 三、Redis 注册中心与发现

### 节点注册

节点上线时写入 Redis，每 30s 刷新 TTL，下线后自动过期：

```
Key:   im:nodes:{nodeId}
Value: {
  nodeId, displayName,
  publicKeyEd25519,
  publicKeyX25519,
  ip,
  fileTransferPort,
  lastSeen
}
TTL: 60s
```

### Agent 注册（可选暴露）

```
Key:   im:agents:{nodeId}
Value: [
  {
    agentId,
    displayName,
    description,
    acceptsFrom: "all" | ["nodeId1", "nodeId2", ...]
  }
]
TTL: 与节点 TTL 同步刷新
```

### 发现流程

```
启动 → 写入 im:nodes:{nodeId}
     → 订阅 im:requests:{nodeId}（配对请求频道）
     → 订阅 im:messages:{nodeId}（消息频道）

每 30s  → 刷新 im:nodes TTL（心跳）

UI 刷新 → 扫描 im:nodes:* → 过滤自身 → 返回在线节点列表
         → 同步拉取各节点 im:agents:{nodeId}
```

### 多实例隔离

不同部门配置各自的 Redis 地址，节点只在自己的 Redis 内可见，天然实现跨部门 IM 隔离。

---

## 四、配对与授权流程

### 频道约定

```
im:requests:{nodeId}  — 接收配对请求（上线即订阅，任何人可发）
im:messages:{nodeId}  — 接收加密消息（仅已配对节点的消息被处理）
```

### 配对流程

**A 发起请求：**
```json
PUBLISH im:requests:{nodeIdB}
{
  "type": "PAIR_REQUEST",
  "fromNodeId": "...",
  "fromDisplayName": "张三",
  "fromPublicKeyEd25519": "...",
  "fromPublicKeyX25519": "...",
  "timestamp": 1710000000000,
  "signature": "..."  // A 用 Ed25519 私钥对上述字段签名
}
```

**B 收到后：**
1. 验证 signature（防伪造）
2. UI 弹出通知：`"「张三」想与你建立联系，身份指纹：a3f2...c91b"`
3. 用户选择 [接受] 或 [拒绝]

**B 接受：**
```json
PUBLISH im:requests:{nodeIdA}
{ "type": "PAIR_ACCEPT", "fromNodeId": "...", "timestamp": ..., "signature": "..." }
```
双方各自将对方存入本地 `im_contacts` 表。

**B 拒绝：**
```json
PUBLISH im:requests:{nodeIdA}
{ "type": "PAIR_REJECT", "fromNodeId": "...", "timestamp": ..., "signature": "..." }
```
A 收到通知，不存储 B 的任何信息。

### 联系人存储

```sql
CREATE TABLE im_contacts (
  node_id              TEXT PRIMARY KEY,
  display_name         TEXT NOT NULL,
  public_key_ed25519   TEXT NOT NULL,
  public_key_x25519    TEXT NOT NULL,
  paired_at            TIMESTAMP NOT NULL,
  status               TEXT NOT NULL DEFAULT 'active'  -- 'active' | 'blocked'
);
```

---

## 五、消息加密协议（E2EE + 签名）

### 混合加密方案

**发送流程（A → B）：**

```
1. 生成随机 AES-256-GCM 会话密钥 sessionKey（每条消息独立）

2. 用 sessionKey 加密消息明文
   (ciphertext, iv, authTag) = AES_GCM.encrypt(plaintext, sessionKey)

3. 用 B 的 X25519 公钥加密 sessionKey（Sealed Box / ECIES）
   encryptedKey = X25519_SealedBox.seal(sessionKey, B.publicKeyX25519)

4. 用 A 的 Ed25519 私钥对 [encryptedKey + ciphertext + iv + timestamp] 签名
   signature = Ed25519.sign(payload, A.privateKeyEd25519)

5. PUBLISH im:messages:{nodeIdB}
   {
     "type": "MESSAGE",
     "fromNodeId": "...",
     "fromAgentId": null,         // Agent 消息时填写
     "toAgentId": null,           // 发给 Agent 时填写
     "messageId": "UUID",
     "timestamp": 1710000000000,
     "encryptedKey": "...",
     "iv": "...",
     "ciphertext": "...",
     "authTag": "...",
     "signature": "..."
   }
```

**接收流程（B）：**
```
1. fromNodeId 不在 im_contacts → 丢弃
2. 验证 signature → 失败丢弃
3. 用自己 X25519 私钥解开 encryptedKey → sessionKey
4. AES-GCM 解密 → 明文
5. 存入 im_messages，推送 UI
```

### 明文内容结构（解密后）

| type | content JSON |
|------|-------------|
| `TEXT` | `{ "text": "..." }` |
| `IMAGE` | `{ "filename": "...", "size": 102400, "sha256": "...", "transferPort": 9876, "token": "UUID" }` |
| `FILE` | `{ "filename": "...", "size": 1048576, "sha256": "...", "transferPort": 9876, "token": "UUID" }` |
| `AGENT_MESSAGE` | `{ "text": "...", "context": {} }` |

### 消息存储

```sql
CREATE TABLE im_conversations (
  id            TEXT PRIMARY KEY,   -- peer nodeId
  display_name  TEXT,
  last_msg      TEXT,
  last_msg_at   TIMESTAMP,
  unread_count  INT DEFAULT 0
);

CREATE TABLE im_messages (
  id              TEXT PRIMARY KEY,  -- messageId (UUID)
  conversation_id TEXT NOT NULL,
  sender_node_id  TEXT NOT NULL,
  sender_agent_id TEXT,              -- null 表示人类发送
  type            TEXT NOT NULL,     -- TEXT | IMAGE | FILE | AGENT_MESSAGE
  content         TEXT NOT NULL,     -- 解密后明文 JSON
  local_file_path TEXT,              -- 接收文件的本地路径
  created_at      TIMESTAMP NOT NULL,
  status          TEXT NOT NULL      -- 'sent' | 'delivered' | 'failed'
);
```

---

## 六、文件 P2P 传输

### 流程

```
1. A 启动临时 HTTP 服务（JDK 内置 HttpServer，随机端口）
   GET /transfer/{token} → 流式返回文件内容

2. A 通过加密消息通道发送 IMAGE/FILE 消息（元信息 + token）

3. B 收到 → UI 展示文件卡片 [接受] [拒绝]

4. B 点接受 → GET http://{A.ip}:{transferPort}/transfer/{token}
   流式下载到本地临时目录

5. 下载完成 → SHA-256 校验
   ✓ 移动到用户文件目录，更新 im_messages
   ✗ 提示校验失败，删除临时文件

6. token 单次使用后立即失效；5 分钟内未消费自动关闭 HTTP 服务
```

### 安全保证

| 风险 | 解法 |
|------|------|
| 任意人下载 | token 一次性 + 仅 B 能解密获取 token（E2EE 保证） |
| 传输中篡改 | SHA-256 完整性校验 |
| 端口长期暴露 | 最长 5 分钟自动关闭 |
| 对方离线 | 连接失败提示"对方已离线，文件已失效" |

> 后续迭代可扩展为分片 + Range 断点续传，接口向后兼容。

---

## 七、Agent 集成

### 地址模型

```
用户节点：    {nodeId}
用户的 Agent：{nodeId}@{agentId}
```

消息结构复用五中的加密管道，增加 `fromAgentId` / `toAgentId` 两个可选字段。

### 三种交互模式

**模式一：人 → 对方 Agent**
```
用户 A 发消息，toAgentId = "code-reviewer"
  → B 节点收到，识别 toAgentId，路由到本地 Agent 运行时
  → Agent 调用本地 LLM 处理，生成回复
  → 回复消息 fromAgentId="code-reviewer" 发回 A
  → A 的 UI 标注"[李四的 code-reviewer] 回复了你"
```

**模式二：我的 Agent → 对方 Agent（人工触发）**
```
用户 A 指示自己的 Agent："帮我问李四的 code-reviewer 这段代码的问题"
  → A 的 Agent 构造消息，fromAgentId="default"，toAgentId="code-reviewer"
  → 走相同 E2EE 管道发送
  → B 的 Agent 处理后响应
  → A 的 Agent 汇总结果回复用户 A
```

**模式三：Agent → Agent 自动协作（完全自动，需用户预授权）**
```
预配置："允许 scheduler-agent 定期联系李四的 data-agent"
  → 触发条件满足时，Agent 自主发起 IM 消息
  → 全程 E2EE，记录在 im_messages 表，用户可审计
```

### 权限层级

| 层级 | 控制方 | 说明 |
|------|--------|------|
| 节点配对 | 双方用户 | 未配对节点的 Agent 消息同样拒绝 |
| Agent 暴露 | Agent 所有者 | 未暴露的 Agent 对外不可见、不可交互 |
| Agent 准入 | Agent 所有者 | 可限定"仅特定 nodeId"可交互 |
| Agent 主动发消息 | Agent 所有者 | 需显式开启"允许主动发起" |

---

## 八、技术栈

| 层 | 选型 | 说明 |
|----|------|------|
| 加密 | Bouncy Castle | Ed25519 签名、X25519 密钥协商、AES-GCM |
| Redis 客户端 | Lettuce（Spring Boot 默认） | 异步 Pub/Sub |
| 文件传输服务 | JDK `com.sun.net.httpserver.HttpServer` | 零依赖，轻量临时 HTTP |
| 前端 | Vue 3 + 新 `useIm.ts` composable | 复用现有 WebSocket RPC 通信 |
| 本地存储 | 现有 SQLite（新增 im_* 表） | 无需引入新数据库 |

---

## 九、架构总览

```
┌─────────────────────────────────────────────────────┐
│                    Redis（注册中心）                   │
│  im:nodes:*   im:requests:*   im:messages:*          │
│  im:agents:*                                         │
└──────────┬──────────────────────────┬────────────────┘
           │ 注册/订阅/发布            │ 注册/订阅/发布
    ┌──────▼──────┐            ┌──────▼──────┐
    │  节点 A      │            │  节点 B      │
    │  ─────────  │            │  ─────────  │
    │  身份密钥对  │            │  身份密钥对  │
    │  联系人列表  │  P2P文件   │  联系人列表  │
    │  消息历史   │◄──────────►│  消息历史   │
    │  Agent 运行时│            │  Agent 运行时│
    │  本地 SQLite │            │  本地 SQLite │
    └─────────────┘            └─────────────┘
```

---

## 十、后续迭代方向

1. **消息撤回** — 发送 `RETRACT` 类型消息，接收方删除本地记录
2. **已读回执** — 通过加密消息通道回传 `READ_ACK`
3. **文件断点续传** — HTTP Range 请求扩展
4. **Agent 协作协议标准化** — 定义 Agent 间通信的 schema，支持结构化任务传递
5. **私钥备份** — 用户主密码加密私钥后导出，用于多设备迁移
