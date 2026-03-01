# Delivery Tools 功能检查文档 & 前端修复方案

## 一、功能概述

Delivery Tools 包含两个子功能：

- **Email Tool (send_email)** — 通过 SMTP 发送邮件，支持 HTML、CC、TLS
- **Webhook Tool (send_webhook)** — 向配置的 HTTP 端点发送 POST/PUT 请求，支持自定义 headers、trigger 路由

配置通过 Settings → Tools → Delivery Tools 卡片进入 Modal 编辑，保存后持久化到 `data/tool-config.yml`。

---

## 二、后端功能检查 ✅

后端实现质量高，功能完整，无需修改。

| 检查项 | 状态 | 说明 |
|--------|------|------|
| Email SMTP 发送 | ✅ | Jakarta Mail，支持 TLS、HTML 自动检测、CC |
| Webhook HTTP 发送 | ✅ | WebClient，POST/PUT，自定义 headers，30s 超时 |
| Webhook CRUD | ✅ | upsert/remove/list，alias 唯一键 |
| Webhook 路由解析 | ✅ | alias → URL → trigger 三级匹配 |
| 配置持久化 | ✅ | YAML 文件双写，PostConstruct 加载 |
| 密码脱敏 | ✅ | GET 返回 `abc***xyz`，保存时跳过含 `***` 的值 |
| URL 校验 | ✅ | `URI.create()` 检查 scheme 和 host |
| 响应截断 | ✅ | 32KB 上限 |
| HITL 集成 | ✅ | send_email / send_webhook 均需人工确认 |
| Schedule 集成 | ✅ | ScheduledTaskExecutor 支持 email/webhook 投递 |

### 后端小问题（非阻塞）

1. **`DeliveryToolService:97`** — HTML 检测逻辑 `body.contains("<") && body.contains(">")` 过于粗糙，可能误判普通文本中的比较符号。不过考虑到邮件场景，可以接受。

---

## 三、前端功能检查

### 3.1 数据流检查 ✅

| 检查项 | 状态 | 说明 |
|--------|------|------|
| ConfigCard 展示 summary | ✅ | `deliverySummary` 计算属性正确显示 email/webhook 状态 |
| Modal 打开时数据初始化 | ✅ | `currentDeliveryForModal()` 正确深拷贝 |
| Modal 保存 → API 调用 | ✅ | `handleSaveDelivery` 正确组装 payload 调用 `saveConfig` |
| 保存后刷新配置 | ✅ | `useToolConfig.saveConfig` 内部调用 `getConfig()` 刷新 |
| 密码保持逻辑 | ✅ | 留空时保留原密码 `password.value.trim() \|\| props.email.password \|\| ''` |
| Webhook JSON 解析 | ✅ | `JSON.parse` + `Array.isArray` 校验 |

### 3.2 前端问题清单 ❌

#### P0 — 样式体系完全不一致

`DeliveryConfigModal.vue` 的样式与项目中其他 3 个 Modal（TrustedDomainsModal、SearchProvidersModal、CommandSafetyModal）完全不同，是唯一一个没有遵循统一设计规范的 Modal。

**其他 3 个 Modal 共享的设计规范：**

```
结构:  .modal-overlay → .modal-content → .modal-header / .modal-body / .modal-footer
按钮:  .btn-close (关闭) | .btn-secondary (取消) | .btn-primary (保存)
输入:  .form-input (统一样式) + :focus 状态
开关:  .toggle-switch (滑块开关)
间距:  header padding 20px 24px | body padding 24px | footer padding 16px 24px
阴影:  box-shadow: var(--shadow-lg)
遮罩:  rgba(0,0,0,0.5)
```

**DeliveryConfigModal 当前状态 vs 规范：**

| 对比项 | 规范 (其他3个Modal) | DeliveryConfigModal (当前) |
|--------|---------------------|---------------------------|
| 根容器 class | `.modal-content` + flex-direction: column | `.modal` — 无 flex 布局 |
| 遮罩透明度 | `rgba(0,0,0, 0.5)` | `rgba(0,0,0, 0.24)` — 太淡 |
| 阴影 | `var(--shadow-lg)` | 无 — 只有 border |
| Header 结构 | 标题 + 副标题 + 底部分割线 | 仅标题，无分割线 |
| 关闭按钮 | `.btn-close` + hover 变色 | `.close-btn` — 无 hover |
| Body 区域 | `.modal-body` flex:1 + overflow-y:auto | 无 body 容器 |
| Footer 区域 | `.modal-footer` + 顶部分割线 | `.actions` — 无分割线 |
| 取消按钮 | `.btn-secondary` 10px 16px + hover | `button` 8px 12px — 无 hover |
| 保存按钮 | `.btn-primary` 10px 16px + hover | `button.primary` 8px 12px — 无 hover |
| 输入框 | `.form-input` + `:focus` outline | `input` 裸选择器 — 无 focus |
| 开关控件 | `.toggle-switch` 滑块 | 原生 checkbox — 丑 |
| 国际化 | `useI18n()` + `t()` | 全部硬编码英文 |

#### P1 — Webhook 配置使用原始 JSON 文本框

当前 Webhook 端点的编辑方式是一个 `<textarea>` 让用户手写 JSON 数组：

```html
<textarea v-model="endpointsJson" rows="12" placeholder='[{"alias":"ops-alert",...}]' />
```

问题：
- 用户需要了解 JSON 语法
- 缺少字段级校验（alias 为空、URL 格式错误等无法提前发现）
- 手动输入 headers 对象极易出错
- 与项目中其他结构化表单（如 SearchProviders 的卡片式编辑）体验差距巨大

#### P2 — 缺少 i18n 国际化

DeliveryConfigModal 和 ConfigCard 中的 Delivery Tools 使用硬编码字符串，是唯一没有接入 `useI18n()` 的 Settings Modal。

```typescript
// ToolsConfigSection.vue:296-298 — 硬编码
title="Delivery Tools"
description="Configure send_email and send_webhook tool visibility and targets."
```

#### P3 — 表单缺少 label

所有输入框仅有 placeholder，无可访问性 label。当用户已输入内容后，无法再看到字段含义。

#### P4 — 缺少表单校验

可以保存不完整的 Email 配置（如只填了 host 不填 username），后端虽然会在实际发送时报错，但前端应该在保存时提示。

---

## 四、前端修复方案

### 目标

将 `DeliveryConfigModal.vue` 重构为与其他 3 个 Modal 完全一致的设计规范，同时将 Webhook 配置从 JSON 文本框改为结构化卡片编辑。

### 4.1 Modal 结构对齐

采用与 `TrustedDomainsModal.vue` / `SearchProvidersModal.vue` 完全相同的 DOM 结构：

```
.modal-overlay
  .modal-content
    .modal-header (标题 + 副标题 + 关闭按钮 + border-bottom)
    .modal-body (flex:1, overflow-y:auto, padding:24px)
      Email 配置区
      Webhook 配置区
    .modal-footer (取消 + 保存 + border-top)
```

### 4.2 Email 配置区改造

```
Email 区块 (.config-section)
├── 标题行: "EMAIL CONFIGURATION" label + toggle-switch
├── 描述文字
├── (展开时) 表单卡片 (.provider-card)
│   ├── SMTP Host + Port (一行两列)
│   ├── Username (带 label)
│   ├── From Email (带 label)
│   ├── Password (带 visibility toggle，复用 SearchProviders 的 .input-with-toggle)
│   └── TLS checkbox
```

### 4.3 Webhook 配置区改造（核心）

替换 JSON textarea 为结构化卡片列表：

```
Webhook 区块 (.config-section)
├── 标题行: "WEBHOOK ENDPOINTS" label + toggle-switch
├── 描述文字
├── 已有端点列表 (v-for endpoint)
│   └── .provider-card
│       ├── Header: alias 名 + 状态 toggle + 删除按钮
│       └── Body (可折叠):
│           ├── URL input
│           ├── Method select (POST / PUT)
│           ├── Trigger input (可选)
│           └── Headers (key-value 对编辑器)
├── 无端点时: 空状态提示
└── 添加端点按钮
```

### 4.4 样式复用

直接复用其他 Modal 已有的 CSS class，不新增自定义样式：

| 用途 | 复用 class |
|------|-----------|
| 开关 | `.toggle-switch` + `.toggle-slider` (from SearchProvidersModal) |
| 输入框 | `.form-input` + `:focus` |
| 密码框 | `.input-with-toggle` + `.visibility-toggle` (from SearchProvidersModal) |
| 标签 | `.form-label` (uppercase, letter-spacing) |
| 提示 | `.form-hint` |
| 卡片 | `.provider-card` |
| 按钮 | `.btn-primary` / `.btn-secondary` / `.btn-close` |
| Modal | `.modal-overlay` / `.modal-content` / `.modal-header` / `.modal-body` / `.modal-footer` |

### 4.5 i18n 补充

在 i18n 资源文件中添加 `sections.delivery` 命名空间的翻译 key。

### 4.6 改动文件清单

| 文件 | 改动 |
|------|------|
| `DeliveryConfigModal.vue` | **重写** — 结构、样式、逻辑全面对齐 |
| `ToolsConfigSection.vue:295-299` | ConfigCard 的 title/description 改用 i18n `t()` |
| i18n 资源文件 | 新增 `sections.delivery.*` 翻译 key |

### 4.7 不改动

- 后端所有代码不动
- `useToolConfig.ts` composable 不动
- `ToolConfig` 类型定义不动
- 数据流和 API 调用逻辑不动

---

## 五、总结

| 维度 | 评价 |
|------|------|
| 后端功能 | ✅ 完整可用，无阻塞性问题 |
| 前端功能 | ✅ 数据流正确，保存/加载/脱敏均正常工作 |
| 前端样式 | ❌ 与其他 3 个 Modal 完全不一致，是项目中唯一的"异类" |
| UX 体验 | ❌ Webhook 用 JSON 文本框编辑，对用户极不友好 |
| 国际化 | ❌ 唯一未接入 i18n 的 Settings Modal |
| 可访问性 | ❌ 无 label、无 focus 状态 |

**核心修复工作：重写 `DeliveryConfigModal.vue`**，对齐项目设计规范，将 Webhook 改为结构化表单编辑。
