# 智能文档编辑功能设计文档

**日期**：2026-03-10
**状态**：已审批，待实现

---

## 背景与目标

在桌面端 AI 助手中新增类 Notion 的文档编辑能力，让用户可以在桌面端维护树形层级文档，并借助 AI 实现续写、优化、改写等写作辅助功能。知识库索引能力后续单独迭代。

**核心功能（本次实现范围）：**
- 树形层级文档管理（创建、编辑、删除、嵌套）
- TipTap 富文本编辑器
- 三种 AI 触发入口：选中文字（BubbleMenu）、光标处（Slash Command）、全文（工具栏）
- AI 结果内联流式插入编辑器
- 自动保存

**不在本次范围：**
- Word/PDF 导入导出
- 知识库索引与 RAG 检索

---

## 架构决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 编辑器 | TipTap（Vue 3）+ 开源扩展 | 成熟、Vue 3 原生支持、BubbleMenu/FloatingMenu 开箱即用 |
| AI 集成 | 复用现有 agent/run 管道 | 避免维护两套 LLM runtime |
| AI session 管理 | 每文档绑定一个隐藏 session | 保留上下文历史，不污染聊天侧边栏 |
| 内容存储格式 | TipTap JSON（TEXT 列） | SQLite TEXT 无大小限制，格式无损 |
| 文档组织 | 树形层级（parent_id） | 用户明确需求，类 Notion 体验 |
| AI 结果呈现 | 内联流式插入（方案 A） | 无跳出感，TipTap insertContent 天然支持 |

---

## 一、数据库变更

### 新表：documents

```sql
CREATE TABLE documents (
    id          VARCHAR(36)  PRIMARY KEY,
    parent_id   VARCHAR(36)  REFERENCES documents(id) ON DELETE SET NULL,
    title       VARCHAR(500) NOT NULL DEFAULT 'Untitled',
    content     TEXT         NOT NULL DEFAULT '{}',   -- TipTap JSON
    sort_order  INT          NOT NULL DEFAULT 0,
    word_count  INT          NOT NULL DEFAULT 0,
    owner_id    VARCHAR(64)  NOT NULL DEFAULT 'local-default',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_documents_owner   ON documents(owner_id);
CREATE INDEX idx_documents_parent  ON documents(parent_id);
CREATE INDEX idx_documents_updated ON documents(updated_at DESC);
```

### 变更：sessions 表

```sql
ALTER TABLE sessions ADD COLUMN session_type VARCHAR(20) DEFAULT 'chat';
CREATE INDEX idx_sessions_type ON sessions(session_type);
```

`session_type` 取值：`'chat'`（默认，显示在侧边栏）、`'document'`（隐藏，每个文档绑定一个）。

**迁移文件**（SQLite 和 PostgreSQL 各一份）：
- `V25__documents.sql`
- `V26__sessions_type.sql`

---

## 二、后端结构

### 领域层

```
com.jaguarliu.ai.document/
├── DocumentEntity.java       # JPA 实体
├── DocumentRepository.java   # findByOwnerIdOrderByUpdatedAtDesc, findByParentId
├── DocumentService.java      # CRUD + 字数统计 + 隐藏 session 查找/创建
```

### RPC Handler 层

```
gateway/rpc/handler/document/
├── DocumentListHandler.java       # document.list  — 返回完整树（无 content）
├── DocumentGetHandler.java        # document.get   — 返回含 content 的完整文档
├── DocumentCreateHandler.java     # document.create（支持 parent_id）
├── DocumentUpdateHandler.java     # document.update（标题 + content + sort_order）
├── DocumentDeleteHandler.java     # document.delete（级联删子节点）
└── DocumentAiAssistHandler.java   # document.ai.assist（查找/创建隐藏 session → agent/run）
```

### DocumentAiAssistHandler 核心逻辑

```java
// method: document.ai.assist
// params: { docId, action, selection }
// action: "continue" | "optimize" | "rewrite" | "summarize" | "translate"

DocumentEntity doc = documentService.get(docId);
Session session = documentService.findOrCreateHiddenSession(docId);
String prompt = buildPrompt(action, doc.getContent(), params.get("selection"));

// 委托给现有 agent/run 管道
AgentRunResult result = agentRunService.run(session.getId(), prompt);
return RpcResponse.success(reqId, Map.of("streamRunId", result.getRunId()));
```

**隐藏 session 管理**：`DocumentService.findOrCreateHiddenSession(docId)` 查询 `session_type = 'document'` 且 `name = "doc:" + docId` 的 session，不存在则创建。

### 五种 Action 的 System Prompt

| Action | Prompt 核心 |
|--------|-------------|
| `continue` | 续写，保持文风，只输出续写部分，不重复已有内容 |
| `optimize` | 润色全文，改善表达，保留原意，只输出改后文本 |
| `rewrite` | 改写选中段落，更清晰简洁，只输出改写结果 |
| `summarize` | 提炼 3-5 条核心要点，以 Markdown 列表输出 |
| `translate` | 中英互译，只输出译文 |

### SessionSidebar 过滤

`SessionListHandler` 查询加上 `WHERE session_type = 'chat'` 条件，隐藏 document session。

---

## 三、前端结构

### 新增路由

```typescript
{ path: '/documents',     component: () => import('@/views/DocumentView.vue') },
{ path: '/documents/:id', component: () => import('@/views/DocumentView.vue'), props: true }
```

### 导航

`ModeSwitcher.vue` 增加第三个模式按钮，切换到 `/documents`。

### 新增 Composable

**`useDocuments.ts`**（模块级 singleton state，模仿 `useSchedules.ts`）

```typescript
// 模块级状态
const documents = ref<DocumentNode[]>([])        // 完整树
const currentDoc = ref<Document | null>(null)
const loading = ref(false)
const saving = ref(false)
const aiStreaming = ref(false)

export function useDocuments() {
  // CRUD: loadTree, loadDocument, createDocument, updateDocument, deleteDocument
  // AI:   aiAssist(docId, action, selection?) → 订阅 assistant.delta → insertContent
  // 自动保存：1.5s debounce
}
```

### 新增组件

```
src/views/DocumentView.vue               # 编排器（同 WorkspaceView 模式）

src/components/documents/
├── DocumentSidebar.vue                  # 树形导航 + 新建/右键菜单
├── DocumentEditor.vue                   # TipTap 编辑器容器 + 工具栏
├── DocumentBubbleMenu.vue               # 选中文字浮动工具栏（AI 操作）
├── DocumentSlashMenu.vue                # Slash command 菜单
└── DocumentAiIndicator.vue             # 流式进行时的加载指示 + 撤销按钮
```

### 视图布局

```
┌───────────────────────────────────────────────────────────┐
│  DocumentSidebar (260px)     │  DocumentEditor (flex-1)   │
│  ┌─ 📁 工作文档              │  [标题输入]  [AI操作] [保存]│
│  │   📄 周报模板             │                            │
│  │   📄 系统设计             │  <TipTap 编辑区>           │
│  │     📄 数据库方案         │  BubbleMenu 浮于选中文字    │
│  └─ 📄 个人笔记              │  SlashMenu 浮于空行        │
│  [+ 新建页面]                │                            │
└───────────────────────────────────────────────────────────┘
```

### TipTap 安装包

```json
"@tiptap/vue-3": "latest",
"@tiptap/starter-kit": "latest",
"@tiptap/extension-bubble-menu": "latest",
"@tiptap/extension-floating-menu": "latest",
"@tiptap/extension-placeholder": "latest",
"@tiptap/suggestion": "latest"
```

### AI 内联流式插入流程

```
1. 用户触发操作（BubbleMenu / Slash / 工具栏）
2. 若是"替换"操作：editor.chain().deleteSelection().run()
3. 调用 document.ai.assist RPC → 拿到 streamRunId
4. 订阅 onEvent('assistant.delta')，每个 chunk：
     editor.commands.insertContent(chunk)
5. lifecycle.end 后显示 DocumentAiIndicator（"✓ 保留 / ↩ 撤销"）
6. 撤销：editor.chain().undo().run()（回滚全部插入）
7. 保留：关闭 indicator，触发 autosave
```

### 自动保存

编辑器 `onUpdate` 事件 → 1.5s debounce → `document.update` RPC。标题变更实时同步到 `DocumentSidebar` 树节点。

---

## 四、新增 / 修改文件清单

### 后端新建

```
src/main/java/com/jaguarliu/ai/document/
├── DocumentEntity.java
├── DocumentRepository.java
└── DocumentService.java

src/main/java/.../handler/document/
├── DocumentListHandler.java
├── DocumentGetHandler.java
├── DocumentCreateHandler.java
├── DocumentUpdateHandler.java
├── DocumentDeleteHandler.java
└── DocumentAiAssistHandler.java

src/main/resources/db/migration/V25__documents.sql
src/main/resources/db/migration/V26__sessions_type.sql
src/main/resources/db/migration-sqlite/V25__documents.sql
src/main/resources/db/migration-sqlite/V26__sessions_type.sql
```

### 前端新建

```
src/views/DocumentView.vue
src/composables/useDocuments.ts
src/components/documents/DocumentSidebar.vue
src/components/documents/DocumentEditor.vue
src/components/documents/DocumentBubbleMenu.vue
src/components/documents/DocumentSlashMenu.vue
src/components/documents/DocumentAiIndicator.vue
```

### 前端修改

```
src/router/index.ts                        # 新增 /documents 路由
src/components/layout/ModeSwitcher.vue     # 新增文档模式按钮
src/types/index.ts                         # 新增 DocumentNode, Document 类型
src/composables/useChat.ts 或相关文件      # SessionSidebar 过滤 session_type='chat'
```

---

## 五、验收标准

1. 启动后端，Flyway V25/V26 执行无报错，`documents` 表和 `session_type` 列存在
2. 前端可创建文档、多级嵌套、侧边栏树形展示正确
3. 编辑文档内容，1.5s 后自动保存，刷新后内容持久化
4. 选中文字 → BubbleMenu 出现 AI 按钮 → 触发后流式改写内联插入
5. 空行输入 `/` → Slash 菜单出现 → 选择续写 → 流式插入
6. 工具栏"全文润色" → 流式替换全部内容
7. AI 流式过程中出现加载指示，完成后可撤销
8. 聊天侧边栏不出现 `session_type = 'document'` 的 session
