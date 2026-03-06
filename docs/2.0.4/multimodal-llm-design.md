# 多模态 LLM 接入设计稿

**版本目录：** `2.0.4`

**目标：** 在现有仅支持文本 chat 的 LLM 接入层上，升级出可持续演进的多模态输入能力，优先支持桌面端最常见的“识图”场景。

---

## 1. 背景

当前框架的 LLM 接入层本质上是“纯文本消息 + Tool Calling”的 OpenAI-compatible chat 框架，尚不具备真正的多模态消息抽象。

现状限制包括：

- `LlmRequest.Message` 只有字符串 `content`，不能表达文本块 + 图片块的组合。
- `OpenAiCompatibleLlmClient` 将消息直接序列化为字符串 `content`，请求体固定走 `/chat/completions`。
- 前端附件目前只是被拼接进 prompt 文本，而不是作为模型输入的一部分发送。
- 文件上传链路设计目标是“上传后给 agent 用 `read_file` 工具读取”，而不是直接送给模型。
- 会话消息持久化目前仅保存纯文本，无法在下一轮对话中重建“上一轮附带的图片”。

这意味着：

**识图能力不是 provider 层的小补丁，而是一次消息模型升级。**

---

## 2. 设计目标

### 2.1 功能目标

- 支持桌面端上传图片并让模型直接看图回答。
- 支持图片参与多轮上下文，而不是只在当前轮临时生效。
- 支持不同 provider / model 的能力判断。
- 支持后续扩展到 Anthropic、Gemini 等非 OpenAI 协议。

### 2.2 非目标

第一阶段暂不包含：

- 音频 / 视频输入
- 图片生成
- OCR 预处理流水线
- 所有 provider 一次性全支持
- 复杂的图片资产管理服务

---

## 3. 推荐总体方案

推荐采用：

**“统一多模态消息模型 + provider codec + 分阶段落地”**

即：

- 架构层先升级为统一的多模态消息抽象；
- 第一阶段只实现 OpenAI-compatible vision MVP；
- 第二阶段再接入 Anthropic / Gemini 等不同 schema 的 provider codec。

### 为什么不推荐只做 OpenAI client 特例补丁

如果仅在 `OpenAiCompatibleLlmClient` 中塞入图片兼容逻辑，短期可能能跑通，但后续接入：

- Anthropic `messages[].content[]`
- Gemini `contents[].parts[]`

会快速演变成 provider 特例堆积，导致请求模型、解析逻辑和能力判断全都耦合在一个类里。

---

## 4. 核心架构设计

### 4.1 统一消息抽象

当前：

- `LlmRequest.Message.content: String`

升级后建议：

- `LlmRequest.Message.parts: List<ContentPart>`
- 保留 `content` 作为兼容字段

建议结构：

```text
LlmRequest
  messages: List<Message>

Message
  role
  content        // 兼容旧逻辑
  parts          // 新结构化内容
  toolCalls
  toolCallId

ContentPart
  type: text | image
  text?: TextPart
  image?: ImagePart

ImagePart
  sourceType: workspace_file | data_url | remote_url
  filePath?: string
  dataUrl?: string
  url?: string
  mimeType?: string
  filename?: string
  detail?: auto | low | high
  width?: int
  height?: int
  sha256?: string
```

### 4.2 Provider Codec 层

新增一层 codec / adapter：

```text
LlmClient
  -> ProviderAdapter / ProviderCodec
      -> OpenAiChatCompletionsCodec
      -> AnthropicMessagesCodec
      -> GeminiGenerateContentCodec
```

职责划分：

- 统一层负责：
  - 消息抽象
  - provider 路由
  - 调用入口
- codec 层负责：
  - 将统一 `LlmRequest` 转为 provider-specific JSON
  - 将 provider 返回解析为统一 `LlmResponse` / `LlmChunk`

### 4.3 附件引用优先，不直接前端传大 base64

推荐图片输入采用：

- 前端上传图片到 workspace
- RPC 只传附件引用（`filePath`, `mimeType`, `filename`）
- 后端在调用 provider 前再读取图片并编码

优点：

- 避免 WebSocket / RPC 负载膨胀
- 避免数据库中塞大段 base64
- 保持会话消息与本地 workspace 的统一关系
- 更适合桌面端离线文件工作流

---

## 5. 前后端链路设计

### 5.1 前端发送链路

当前：

- 图片/文件附件信息会被拼进 prompt 字符串

升级后：

- `prompt`：纯文本用户输入
- `attachments`：模型直接消费的附件（第一阶段主要是图片）
- `contexts`：继续保留现有 folder/web/doc/rule 等上下文

建议 `agent.run` payload：

```json
{
  "sessionId": "...",
  "agentId": "...",
  "model": "...",
  "prompt": "请描述这张图",
  "attachments": [
    {
      "id": "ctx-1",
      "type": "image",
      "filePath": "uploads/abc123_example.png",
      "filename": "example.png",
      "mimeType": "image/png",
      "size": 123456,
      "detail": "auto"
    }
  ]
}
```

### 5.2 上传链路

当前 `FileUploadRouter` 仅允许文档类型。

第一阶段建议新增图片扩展名：

- `.png`
- `.jpg`
- `.jpeg`
- `.webp`
- `.gif`

同时返回：

- `filePath`
- `filename`
- `size`
- `mimeType`

### 5.3 后端组装链路

`AgentRunHandler` 中不再只做：

- `Message.user(prompt)`

而是应根据 `attachments` 构造：

- 一个 user message
- 内含：
  - 一个 text part
  - 若干 image parts

---

## 6. 会话历史与持久化设计

### 6.1 为什么必须升级持久化

如果用户第一轮上传图片并提问：

> 这张图里是什么？

第二轮再问：

> 左上角那个按钮是什么？

若历史里只保存文本 prompt，而没有图片引用，模型无法理解“这张图”指的是什么。

### 6.2 推荐方案

建议优先采用：

**在 `MessageEntity` 增加 `contentJson`（或 `messageJson`）字段**

存储完整结构化消息：

- role
- text parts
- image parts
- tool calls（后续也可统一）

同时保留：

- `content` 作为兼容文本摘要

### 6.3 兼容策略

- 旧消息：只有 `content`
  - 读取时自动映射为单一 `text part`
- 新消息：优先使用 `contentJson`
- `MessageService.toRequestMessages()` 需升级为：
  - 先读 `contentJson`
  - 无则 fallback 到 `content`

---

## 7. Provider / Model 能力设计

当前 provider 配置只存：

- `id`
- `name`
- `endpoint`
- `apiKey`
- `models: List<String>`

这不足以表达多模态能力。

建议未来升级为模型描述对象，例如：

```text
ModelCapability
  id
  label
  supportsVision
  supportsTools
  supportsStreaming
  maxImages
```

如果第一阶段不想改配置格式太大，可先临时采用：

- 后端维护一层 capability resolver
- 通过 providerId + model 名判断是否支持 vision

但长期应写入配置模型。

---

## 8. Provider 分阶段支持策略

### 第一阶段：OpenAI-compatible Vision

目标：

- 在现有框架最小成本下先跑通识图
- 支持兼容 OpenAI `chat.completions` content parts 的模型

包含：

- OpenAI 官方视觉模型
- 一些兼容 OpenAI schema 的网关/代理

### 第二阶段：Anthropic Codec

需要支持：

- `messages[].content[]` block schema
- image source 特定结构
- 可能与 tool calling 的编解码差异

### 第三阶段：Gemini Codec

需要支持：

- `contents[].parts[]`
- `inline_data` / `file_data`
- Gemini 自有 schema

---

## 9. 流式输出设计

第一阶段建议：

- 只扩展输入多模态
- 保持输出协议不变

即：

- `LlmChunk.delta` 继续承载文本
- `toolCalls` 流式累积逻辑继续保留
- 不在第一阶段支持复杂的多模态输出块流

这样可以避免一次性改动过大。

---

## 10. MVP 范围

第一阶段 MVP 成功标准：

- 用户能上传图片
- 用户选择支持 vision 的模型
- `agent.run` 不再把图片路径拼进 prompt，而是作为 image part 发送
- 模型能返回对图片的描述
- 图片可进入多轮历史
- 如果模型不支持 vision，前后端都能明确报错或提示切换模型

---

## 11. 风险与注意点

### 11.1 最大风险：只改 client，不改消息模型

如果只在 provider 层偷塞 base64，后续会带来：

- 无法做多轮图片上下文
- 前端无法感知模型能力
- 历史消息无法重建
- 继续扩展其他 provider 时返工

### 11.2 存储风险

- 不建议把大段 base64 存 DB
- 应优先存文件引用 + 元数据

### 11.3 成本治理

识图模型的 token / 成本通常高于纯文本模型，后续应补充：

- 图片数量限制
- 图片大小限制
- capability check
- token 预算估算

---

## 12. 推荐实施顺序

1. 升级 `LlmRequest.Message` 为 `parts` 模型，保留 `content` 兼容。
2. 升级 `agent.run` payload，加入 `attachments`。
3. 前端 MessageInput / useChat 不再把图片拼 prompt。
4. 上传接口支持图片 MIME。
5. OpenAI-compatible codec 支持 image parts。
6. 消息持久化支持 `contentJson`。
7. provider/model 能力判断与 UI 提示。
8. 后续再扩展 Anthropic / Gemini codec。

---

## 13. 最终建议

建议将本次工作定义为：

**“从文本消息框架升级为结构化多模态消息框架”**

第一阶段以 OpenAI-compatible vision 为 MVP，第二阶段再抽象 provider codec，逐步演进为真正可持续的多模态 LLM 接入层。
