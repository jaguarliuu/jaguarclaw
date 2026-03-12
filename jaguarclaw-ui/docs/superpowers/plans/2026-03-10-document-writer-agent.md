# Document Writer Agent & TipTap Enhancements Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the document editor with a rich-text formatting toolbar, slash command menu (`/`), a document-writing AI agent with isolated tools (doc_read, doc_insert, http_get), real-time content streaming from agent into TipTap editor, and global system prompt configuration.

**Architecture:** Three independent deliverables built sequentially. (1) Pure frontend TipTap enhancements using already-installed `@tiptap/suggestion`. (2) Backend foundation: new `doc.content.insert` event type, DocumentConfigService, DocReadTool/DocInsertTool/HttpGetTool, document-writer AgentProfile, and updated DocumentAiAssistHandler using AgentRuntime. (3) Frontend agent wiring: DocumentStatusBar showing `assistant.delta` thinking text, AI settings popover, useDocuments subscribing to `doc.content.insert`.

**Tech Stack:** Vue 3 + TipTap v3.20.1 + `@tiptap/suggestion` (installed); Java Spring Boot + AgentRuntime ReAct loop; `ToolExecutionContext.current()` for tool-level event publishing; Jsoup for HTTP page fetching; WebSocket RPC events via EventBus.

---

## Reference Files (read before implementing)

| Purpose | Path |
|---------|------|
| Tool interface | `src/main/java/com/jaguarliu/ai/tools/Tool.java` |
| Tool execution context (connectionId in tools) | `src/main/java/com/jaguarliu/ai/tools/ToolExecutionContext.java` |
| Example simple tool | any file in `src/main/java/com/jaguarliu/ai/tools/builtin/memory/` |
| AgentRuntime invoke method | `src/main/java/com/jaguarliu/ai/runtime/AgentRuntime.java` (find the public `run(...)` method) |
| RunContext fields | `src/main/java/com/jaguarliu/ai/runtime/RunContext.java` |
| Agent profile seeding pattern | `src/main/java/com/jaguarliu/ai/agents/service/AgentProfileBootstrapInitializer.java` |
| AgentProfileService | `src/main/java/com/jaguarliu/ai/agents/service/AgentProfileService.java` |
| AgentProfile model | `src/main/java/com/jaguarliu/ai/agents/model/AgentProfile.java` |
| Existing doc RPC handler pattern | `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/document/DocumentGetHandler.java` |
| AgentEvent factory methods | `src/main/java/com/jaguarliu/ai/gateway/events/AgentEvent.java` |
| DocumentService | `src/main/java/com/jaguarliu/ai/document/DocumentService.java` |
| TipTap suggestion docs | https://tiptap.dev/docs/editor/extensions/functionality/suggestions |
| Existing DocumentEditor | `jaguarclaw-ui/src/components/documents/DocumentEditor.vue` |
| useDocuments composable | `jaguarclaw-ui/src/composables/useDocuments.ts` |
| WorkspaceView doc section | `jaguarclaw-ui/src/views/WorkspaceView.vue` (lines 35-83) |

---

## Chunk 1: TipTap UI Enhancements (Pure Frontend)

### Task 1: DocumentFormatToolbar Component

**Goal:** Add a persistent formatting toolbar below the document title bar. Bold, italic, headings, lists, code block, blockquote, horizontal rule, mermaid insert. Uses TipTap's existing StarterKit commands — no new packages needed.

**Files:**
- Create: `jaguarclaw-ui/src/components/documents/DocumentFormatToolbar.vue`
- Modify: `jaguarclaw-ui/src/components/documents/DocumentEditor.vue`

**Important:** `StarterKit` already includes Bold, Italic, Strike, Code, Heading, BulletList, OrderedList, Blockquote, HorizontalRule, CodeBlock. No additional TipTap extensions needed for the toolbar.

- [ ] **Step 1: Create `DocumentFormatToolbar.vue`**

```vue
<!-- jaguarclaw-ui/src/components/documents/DocumentFormatToolbar.vue -->
<script setup lang="ts">
import type { Editor } from '@tiptap/vue-3'

const props = defineProps<{ editor: Editor | undefined }>()

function run(cmd: () => boolean) { cmd() }
</script>

<template>
  <div v-if="editor" class="format-toolbar">
    <button
      :class="{ active: editor.isActive('bold') }"
      title="Bold (Ctrl+B)"
      @click="run(() => editor!.chain().focus().toggleBold().run())"
    >B</button>
    <button
      :class="{ active: editor.isActive('italic') }"
      title="Italic (Ctrl+I)"
      @click="run(() => editor!.chain().focus().toggleItalic().run())"
    ><em>I</em></button>
    <button
      :class="{ active: editor.isActive('strike') }"
      title="Strikethrough"
      @click="run(() => editor!.chain().focus().toggleStrike().run())"
    ><s>S</s></button>

    <div class="format-toolbar__sep" />

    <button
      :class="{ active: editor.isActive('heading', { level: 1 }) }"
      @click="run(() => editor!.chain().focus().toggleHeading({ level: 1 }).run())"
    >H1</button>
    <button
      :class="{ active: editor.isActive('heading', { level: 2 }) }"
      @click="run(() => editor!.chain().focus().toggleHeading({ level: 2 }).run())"
    >H2</button>
    <button
      :class="{ active: editor.isActive('heading', { level: 3 }) }"
      @click="run(() => editor!.chain().focus().toggleHeading({ level: 3 }).run())"
    >H3</button>

    <div class="format-toolbar__sep" />

    <button
      :class="{ active: editor.isActive('bulletList') }"
      title="Bullet list"
      @click="run(() => editor!.chain().focus().toggleBulletList().run())"
    >• List</button>
    <button
      :class="{ active: editor.isActive('orderedList') }"
      title="Numbered list"
      @click="run(() => editor!.chain().focus().toggleOrderedList().run())"
    >1. List</button>

    <div class="format-toolbar__sep" />

    <button
      :class="{ active: editor.isActive('codeBlock') }"
      title="Code block"
      @click="run(() => editor!.chain().focus().toggleCodeBlock().run())"
    >&lt;/&gt;</button>
    <button
      :class="{ active: editor.isActive('blockquote') }"
      title="Blockquote"
      @click="run(() => editor!.chain().focus().toggleBlockquote().run())"
    >"</button>
    <button
      title="Horizontal rule"
      @click="run(() => editor!.chain().focus().setHorizontalRule().run())"
    >—</button>
    <button
      title="Insert Mermaid diagram"
      @click="run(() => editor!.chain().focus().insertContent({ type: 'codeBlock', attrs: { language: 'mermaid' }, content: [{ type: 'text', text: 'graph TD\n  A --> B' }] }).run())"
    >⬡ Mermaid</button>
  </div>
</template>

<style scoped>
.format-toolbar {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 4px 20px;
  border-bottom: var(--border);
  background: var(--color-white);
  flex-shrink: 0;
  flex-wrap: wrap;
}
.format-toolbar button {
  padding: 3px 8px;
  font-size: 12px;
  font-family: var(--font-ui);
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-gray-700);
  cursor: pointer;
  line-height: 1.4;
  min-width: 28px;
  transition: background var(--duration-fast) var(--ease-out);
}
.format-toolbar button:hover {
  background: var(--color-gray-100);
  border-color: var(--color-gray-200);
}
.format-toolbar button.active {
  background: var(--color-gray-200);
  color: var(--color-gray-900);
  font-weight: 600;
}
.format-toolbar__sep {
  width: 1px;
  height: 16px;
  background: var(--color-gray-200);
  margin: 0 4px;
}
</style>
```

- [ ] **Step 2: Wire toolbar into `DocumentEditor.vue`**

In `DocumentEditor.vue`, add the import and insert the component between the title toolbar and the editor body:

```typescript
// Add import at top of <script setup>
import DocumentFormatToolbar from './DocumentFormatToolbar.vue'
```

In template, after `</div>` that closes `.doc-editor__toolbar`, before the `ref="bubbleMenuRef"` div:

```html
<DocumentFormatToolbar :editor="editor" />
```

The result: title toolbar → format toolbar → bubble menu ref → editor body.

- [ ] **Step 3: Manual verification**

Start the frontend dev server (`npm run dev` or `vite`). Open a document. Confirm:
- Format toolbar appears below the title bar
- Clicking Bold toggles bold on selected text, button shows active state
- H1/H2/H3 buttons toggle headings
- List buttons toggle lists
- Code block, blockquote, mermaid insert all work

- [ ] **Step 4: Commit**

```bash
cd /path/to/jaguarclaw-ui
git add src/components/documents/DocumentFormatToolbar.vue src/components/documents/DocumentEditor.vue
git commit -m "feat(docs): add TipTap formatting toolbar with bold, italic, headings, lists, code block, mermaid"
```

---

### Task 2: Slash Command Menu

**Goal:** Typing `/` in the editor opens a floating menu with content blocks (headings, lists, code, mermaid) and AI actions (continue, optimize, summarize, translate). Uses `@tiptap/suggestion` (already installed at v3.20.1).

**Files:**
- Create: `jaguarclaw-ui/src/components/documents/DocumentSlashMenu.vue`
- Create: `jaguarclaw-ui/src/components/documents/SlashExtension.ts`
- Modify: `jaguarclaw-ui/src/components/documents/DocumentEditor.vue`

**Read first:** TipTap suggestion docs at https://tiptap.dev/docs/editor/extensions/functionality/suggestions to understand the `suggestion` option API for `render()`, `command()`, `items()`.

- [ ] **Step 1: Create `DocumentSlashMenu.vue`**

This is a plain Vue component rendered imperatively (mounted to `document.body`) by the suggestion render callbacks.

```vue
<!-- jaguarclaw-ui/src/components/documents/DocumentSlashMenu.vue -->
<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'

export interface SlashMenuItem {
  label: string
  description: string
  icon: string
  action: () => void
}

const props = defineProps<{
  items: SlashMenuItem[]
  clientRect: (() => DOMRect) | null
}>()

const selectedIndex = ref(0)
const menuRef = ref<HTMLDivElement | null>(null)

watch(() => props.items, () => { selectedIndex.value = 0 })

function selectItem(index: number) {
  props.items[index]?.action()
}

function onKeyDown(e: KeyboardEvent) {
  if (e.key === 'ArrowDown') {
    selectedIndex.value = (selectedIndex.value + 1) % props.items.length
    e.preventDefault()
    return true
  }
  if (e.key === 'ArrowUp') {
    selectedIndex.value = (selectedIndex.value - 1 + props.items.length) % props.items.length
    e.preventDefault()
    return true
  }
  if (e.key === 'Enter') {
    selectItem(selectedIndex.value)
    e.preventDefault()
    return true
  }
  return false
}

// Update menu position
watch(() => props.clientRect, () => {
  if (!menuRef.value || !props.clientRect) return
  const rect = props.clientRect()
  menuRef.value.style.top = `${rect.bottom + window.scrollY + 4}px`
  menuRef.value.style.left = `${rect.left + window.scrollX}px`
}, { immediate: true })

defineExpose({ onKeyDown })
</script>

<template>
  <div ref="menuRef" class="slash-menu">
    <div
      v-for="(item, i) in items"
      :key="item.label"
      class="slash-menu__item"
      :class="{ 'slash-menu__item--active': i === selectedIndex }"
      @click="selectItem(i)"
      @mouseenter="selectedIndex = i"
    >
      <span class="slash-menu__icon">{{ item.icon }}</span>
      <span class="slash-menu__text">
        <span class="slash-menu__label">{{ item.label }}</span>
        <span class="slash-menu__desc">{{ item.description }}</span>
      </span>
    </div>
    <div v-if="items.length === 0" class="slash-menu__empty">无匹配命令</div>
  </div>
</template>

<style scoped>
.slash-menu {
  position: fixed;
  z-index: 200;
  background: var(--color-white);
  border: var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
  padding: 4px;
  min-width: 220px;
  max-height: 320px;
  overflow-y: auto;
}
.slash-menu__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  user-select: none;
}
.slash-menu__item--active {
  background: var(--color-gray-100);
}
.slash-menu__icon { font-size: 16px; width: 24px; text-align: center; flex-shrink: 0; }
.slash-menu__text { display: flex; flex-direction: column; gap: 1px; }
.slash-menu__label { font-size: 13px; font-weight: 500; color: var(--color-gray-900); }
.slash-menu__desc { font-size: 11px; color: var(--color-gray-500); }
.slash-menu__empty { padding: 8px; font-size: 12px; color: var(--color-gray-400); text-align: center; }
</style>
```

- [ ] **Step 2: Create `SlashExtension.ts`**

The extension uses `@tiptap/suggestion`. The AI action items need to emit an event out to the parent — we do this via a callback prop passed at extension creation time.

```typescript
// jaguarclaw-ui/src/components/documents/SlashExtension.ts
import { Extension } from '@tiptap/core'
import Suggestion from '@tiptap/suggestion'
import { createApp, ref, h } from 'vue'
import type { App } from 'vue'
import DocumentSlashMenu from './DocumentSlashMenu.vue'

export type SlashAiActionCallback = (action: string) => void

// Content block commands
function contentCommands(editor: any) {
  return [
    { label: '标题 1', description: '大标题', icon: 'H1', action: () => editor.chain().focus().deleteRange(editor.state.selection).toggleHeading({ level: 1 }).run() },
    { label: '标题 2', description: '中标题', icon: 'H2', action: () => editor.chain().focus().deleteRange(editor.state.selection).toggleHeading({ level: 2 }).run() },
    { label: '标题 3', description: '小标题', icon: 'H3', action: () => editor.chain().focus().deleteRange(editor.state.selection).toggleHeading({ level: 3 }).run() },
    { label: '无序列表', description: '圆点列表', icon: '•', action: () => editor.chain().focus().deleteRange(editor.state.selection).toggleBulletList().run() },
    { label: '有序列表', description: '编号列表', icon: '1.', action: () => editor.chain().focus().deleteRange(editor.state.selection).toggleOrderedList().run() },
    { label: '代码块', description: '代码片段', icon: '</>', action: () => editor.chain().focus().deleteRange(editor.state.selection).toggleCodeBlock().run() },
    { label: '引用块', description: '引用文字', icon: '"', action: () => editor.chain().focus().deleteRange(editor.state.selection).toggleBlockquote().run() },
    { label: '分隔线', description: '水平分隔', icon: '—', action: () => editor.chain().focus().deleteRange(editor.state.selection).setHorizontalRule().run() },
    { label: 'Mermaid 图', description: '流程/时序图', icon: '⬡', action: () => editor.chain().focus().deleteRange(editor.state.selection).insertContent({ type: 'codeBlock', attrs: { language: 'mermaid' }, content: [{ type: 'text', text: 'graph TD\n  A --> B' }] }).run() },
  ]
}

// AI action commands — they call the external callback instead of modifying the editor directly
function aiCommands(onAiAction: SlashAiActionCallback, editor: any) {
  return [
    { label: 'AI 续写', description: '从光标处续写内容', icon: '✍', action: () => { editor.chain().focus().deleteRange(editor.state.selection).run(); onAiAction('continue') } },
    { label: 'AI 润色', description: '改善全文表达', icon: '✨', action: () => { editor.chain().focus().deleteRange(editor.state.selection).run(); onAiAction('optimize') } },
    { label: 'AI 总结', description: '提炼核心要点', icon: '📝', action: () => { editor.chain().focus().deleteRange(editor.state.selection).run(); onAiAction('summarize') } },
    { label: 'AI 翻译', description: '中英互译', icon: '🌐', action: () => { editor.chain().focus().deleteRange(editor.state.selection).run(); onAiAction('translate') } },
  ]
}

export function createSlashExtension(onAiAction: SlashAiActionCallback) {
  return Extension.create({
    name: 'slashCommand',
    addOptions() { return { suggestion: {} } },
    addProseMirrorPlugins() {
      return [
        Suggestion({
          editor: this.editor,
          char: '/',
          allowSpaces: false,
          startOfLine: false,
          items: ({ query }: { query: string }) => {
            const all = [
              ...contentCommands(this.editor),
              ...aiCommands(onAiAction, this.editor),
            ]
            if (!query) return all
            return all.filter(item =>
              item.label.toLowerCase().includes(query.toLowerCase()) ||
              item.description.toLowerCase().includes(query.toLowerCase())
            )
          },
          command: ({ editor, range, props }: any) => {
            // props.action() handles editor commands + optional AI callback
            props.action()
          },
          render: () => {
            let vueApp: App | null = null
            let menuInstance: any = null
            let mountEl: HTMLDivElement | null = null

            return {
              onStart(props: any) {
                mountEl = document.createElement('div')
                document.body.appendChild(mountEl)
                const itemsRef = ref(props.items)
                const rectRef = ref(props.clientRect)
                vueApp = createApp({
                  render: () => h(DocumentSlashMenu, {
                    items: itemsRef.value,
                    clientRect: rectRef.value,
                    ref: (el: any) => { menuInstance = el },
                  }),
                })
                vueApp.mount(mountEl)
              },
              onUpdate(props: any) {
                if (!menuInstance) return
                menuInstance.$.props.items = props.items
                menuInstance.$.props.clientRect = props.clientRect
              },
              onKeyDown(props: any) {
                if (props.event.key === 'Escape') {
                  vueApp?.unmount()
                  mountEl?.remove()
                  return true
                }
                return menuInstance?.onKeyDown(props.event) ?? false
              },
              onExit() {
                vueApp?.unmount()
                mountEl?.remove()
                vueApp = null
                mountEl = null
                menuInstance = null
              },
            }
          },
        }),
      ]
    },
  })
}
```

**Note on render():** The `createApp` + `ref` approach above is conceptual. TipTap v3 suggestion render callbacks are plain objects — they don't natively work with Vue reactivity. A simpler and more reliable pattern: instead of using Vue refs inside the render callbacks, just call `vueApp.unmount()` / re-`mount()` on each `onUpdate`. Or use a vanilla DOM approach for the menu. If the above has reactivity issues, fallback: render a static div with vanilla JS in `onStart`/`onUpdate`/`onExit` without Vue. The component's layout can still be used as reference. The implementer should test interactively and adapt.

- [ ] **Step 3: Wire `SlashExtension` into `DocumentEditor.vue`**

```typescript
// Add imports in <script setup>
import { createSlashExtension } from './SlashExtension'

// Add to useEditor extensions array (after StarterKit and Placeholder):
createSlashExtension((action) => {
  emit('aiAction', action)
})
```

The `emit('aiAction', action)` call reuses the existing AI action flow — no new wiring needed in WorkspaceView.

- [ ] **Step 4: Manual verification**

1. Open a document, click in editor body, type `/`
2. Confirm floating menu appears with 13 items (9 content + 4 AI)
3. Type `标题` — list filters to heading options
4. Arrow keys navigate, Enter selects
5. Select "标题 1" — heading is inserted at cursor, `/` text is deleted
6. Select "AI 续写" — triggers the same AI action as clicking the toolbar button

- [ ] **Step 5: Commit**

```bash
git add src/components/documents/DocumentSlashMenu.vue src/components/documents/SlashExtension.ts src/components/documents/DocumentEditor.vue
git commit -m "feat(docs): add slash command menu with content blocks and AI actions"
```

---

## Chunk 2: Backend Foundation

### Task 3: `doc.content.insert` Event Type

**Goal:** Add a new event type `DOC_CONTENT_INSERT` to `AgentEvent` so `DocInsertTool` can push content directly to the editor in real time.

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/gateway/events/AgentEvent.java`

**Read first:** The full `AgentEvent.java` to see the `EventType` enum and factory method pattern.

- [ ] **Step 1: Add event type to the `EventType` enum**

Find the `EventType` enum in `AgentEvent.java`. Add:
```java
DOC_CONTENT_INSERT,  // content pushed from doc_insert tool to editor
```

- [ ] **Step 2: Add factory method**

Add alongside other factory methods (e.g., near `assistantDelta`):
```java
public static AgentEvent docContentInsert(String connectionId, String runId, String content) {
    return AgentEvent.builder()
            .connectionId(connectionId)
            .type(EventType.DOC_CONTENT_INSERT)
            .runId(runId)
            .payload(Map.of("content", content))
            .build();
}
```

- [ ] **Step 3: Verify compilation**

```bash
cd /path/to/miniclaw
./mvnw compile -q
```
Expected: BUILD SUCCESS, no errors.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/gateway/events/AgentEvent.java
git commit -m "feat(events): add DOC_CONTENT_INSERT event type for document agent"
```

---

### Task 4: DocumentConfigService + RPC Handlers

**Goal:** Store and retrieve a global "document writer system prompt" as a JSON file at `~/.jaguarclaw/document-writer-config.json`. Expose via `document.config.get` and `document.config.set` RPC.

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/document/DocumentConfigService.java`
- Create: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/document/DocumentConfigGetHandler.java`
- Create: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/document/DocumentConfigSetHandler.java`

**Default system prompt to use:**
```
你是一位专业写作助手，帮助用户在文档编辑器中进行写作。
你可以使用以下工具：
- doc_read: 读取当前文档内容
- doc_insert: 向文档中插入内容（会实时显示在编辑器中）
- http_get: 获取网页内容（仅用于用户提供的内网链接）

写作原则：
1. 保持文风一致，续写时衔接自然
2. 使用 doc_insert 逐段插入内容，不要一次性插入过多
3. 需要了解文档现状时先调用 doc_read
4. 不使用任何其他工具，不执行任何系统命令
```

- [ ] **Step 1: Create `DocumentConfigService.java`**

```java
package com.jaguarliu.ai.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentConfigService {

    private static final String DEFAULT_SYSTEM_PROMPT =
            "你是一位专业写作助手，帮助用户在文档编辑器中进行写作。\n" +
            "你可以使用以下工具：\n" +
            "- doc_read: 读取当前文档内容\n" +
            "- doc_insert: 向文档中插入内容（会实时显示在编辑器中）\n" +
            "- http_get: 获取网页内容（仅用于用户提供的内网链接）\n\n" +
            "写作原则：\n" +
            "1. 保持文风一致，续写时衔接自然\n" +
            "2. 使用 doc_insert 逐段插入内容，不要一次性插入过多\n" +
            "3. 需要了解文档现状时先调用 doc_read\n" +
            "4. 不使用任何其他工具，不执行任何系统命令";

    private final ObjectMapper objectMapper;

    // In-memory cache; persisted to disk
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    private Path configFilePath() {
        return Path.of(System.getProperty("user.home"), ".jaguarclaw", "document-writer-config.json");
    }

    public String getSystemPrompt() {
        if (cache.containsKey("systemPrompt")) return cache.get("systemPrompt");
        try {
            Path p = configFilePath();
            if (Files.exists(p)) {
                Map<?, ?> data = objectMapper.readValue(p.toFile(), Map.class);
                String prompt = (String) data.get("systemPrompt");
                if (prompt != null) { cache.put("systemPrompt", prompt); return prompt; }
            }
        } catch (IOException e) {
            log.warn("Failed to read document-writer config: {}", e.getMessage());
        }
        return DEFAULT_SYSTEM_PROMPT;
    }

    public void setSystemPrompt(String prompt) {
        cache.put("systemPrompt", prompt);
        try {
            Path p = configFilePath();
            Files.createDirectories(p.getParent());
            objectMapper.writeValue(p.toFile(), Map.of("systemPrompt", prompt));
        } catch (IOException e) {
            log.error("Failed to persist document-writer config: {}", e.getMessage());
        }
    }
}
```

- [ ] **Step 2: Create `DocumentConfigGetHandler.java`**

```java
package com.jaguarliu.ai.gateway.rpc.handler.document;

import com.jaguarliu.ai.document.DocumentConfigService;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DocumentConfigGetHandler implements RpcHandler {
    private final DocumentConfigService documentConfigService;

    @Override
    public String getMethod() { return "document.config.get"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromSupplier(() ->
            RpcResponse.success(request.getId(), Map.of("systemPrompt", documentConfigService.getSystemPrompt()))
        );
    }
}
```

- [ ] **Step 3: Create `DocumentConfigSetHandler.java`**

```java
package com.jaguarliu.ai.gateway.rpc.handler.document;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.document.DocumentConfigService;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DocumentConfigSetHandler implements RpcHandler {
    private final DocumentConfigService documentConfigService;
    private final ObjectMapper objectMapper;

    @Override
    public String getMethod() { return "document.config.set"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromSupplier(() -> {
            try {
                Map<String, Object> p = objectMapper.convertValue(
                        request.getPayload(), new TypeReference<>() {});
                String prompt = (String) p.get("systemPrompt");
                if (prompt == null || prompt.isBlank())
                    return RpcResponse.error(request.getId(), "INVALID_PARAMS", "systemPrompt is required");
                documentConfigService.setSystemPrompt(prompt);
                return RpcResponse.success(request.getId(), Map.of("ok", true));
            } catch (Exception e) {
                return RpcResponse.error(request.getId(), "CONFIG_ERROR", e.getMessage());
            }
        });
    }
}
```

- [ ] **Step 4: Compile and verify**

```bash
./mvnw compile -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/document/DocumentConfigService.java \
        src/main/java/com/jaguarliu/ai/gateway/rpc/handler/document/DocumentConfigGetHandler.java \
        src/main/java/com/jaguarliu/ai/gateway/rpc/handler/document/DocumentConfigSetHandler.java
git commit -m "feat(docs): add DocumentConfigService with document.config.get/set RPC"
```

---

### Task 5: DocReadTool + DocInsertTool

**Goal:** Two Spring `@Component` tools. `doc_read` returns document content. `doc_insert` inserts text content into the document AND publishes a `doc.content.insert` WebSocket event so the frontend can display it in real time.

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/tools/builtin/document/DocReadTool.java`
- Create: `src/main/java/com/jaguarliu/ai/tools/builtin/document/DocInsertTool.java`

**Read first:**
1. `src/main/java/com/jaguarliu/ai/tools/Tool.java` — the interface (implements `getDefinition()` and `execute(Map<String,Object>)`)
2. `src/main/java/com/jaguarliu/ai/tools/ToolExecutionContext.java` — `ToolExecutionContext.current()` gives you `connectionId`, `runId`
3. `src/main/java/com/jaguarliu/ai/tools/ToolDefinition.java` — builder pattern for tool schema
4. `src/main/java/com/jaguarliu/ai/tools/ToolResult.java` — success/error factory methods
5. Any existing tool in `builtin/memory/` for a complete implementation example

The tool schema uses JSON Schema. Look at how other tools define their `parameters` schema in `ToolDefinition`.

- [ ] **Step 1: Create `DocReadTool.java`**

Note: adapt `ToolDefinition.builder()` fields to match the actual `ToolDefinition` API you find in the codebase.

```java
package com.jaguarliu.ai.tools.builtin.document;

import com.jaguarliu.ai.document.DocumentService;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolExecutionContext;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DocReadTool implements Tool {

    private final DocumentService documentService;
    private final ConnectionManager connectionManager;

    @Override
    public ToolDefinition getDefinition() {
        // Adapt builder API to match ToolDefinition in your codebase
        return ToolDefinition.builder()
                .name("doc_read")
                .description("读取当前正在编辑的文档内容。返回完整的文档标题和内容文本。在续写或修改文档前，先调用此工具了解文档现状。")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "doc_id", Map.of("type", "string", "description", "文档 ID")
                        ),
                        "required", java.util.List.of("doc_id")
                ))
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromSupplier(() -> {
            try {
                String docId = (String) arguments.get("doc_id");
                if (docId == null || docId.isBlank())
                    return ToolResult.error("doc_id is required");

                var ctx = ToolExecutionContext.current();
                String ownerId = resolveOwnerId(ctx);

                var doc = documentService.get(docId, ownerId);
                String result = "标题：" + doc.getTitle() + "\n\n" + doc.getContent();
                return ToolResult.success(result);
            } catch (Exception e) {
                return ToolResult.error("Failed to read document: " + e.getMessage());
            }
        });
    }

    private String resolveOwnerId(ToolExecutionContext ctx) {
        if (ctx == null || ctx.getConnectionId() == null) return "local-default";
        var principal = connectionManager.getPrincipal(ctx.getConnectionId());
        return principal != null ? principal.getPrincipalId() : "local-default";
    }
}
```

- [ ] **Step 2: Create `DocInsertTool.java`**

This tool inserts content into the document AND publishes a `doc.content.insert` event:

```java
package com.jaguarliu.ai.tools.builtin.document;

import com.jaguarliu.ai.document.DocumentService;
import com.jaguarliu.ai.gateway.events.AgentEvent;
import com.jaguarliu.ai.gateway.events.EventBus;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolExecutionContext;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocInsertTool implements Tool {

    private final DocumentService documentService;
    private final EventBus eventBus;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("doc_insert")
                .description("向当前文档末尾追加内容。内容会实时显示在用户的编辑器中。每次调用追加一段内容（不超过500字）。内容应为纯文本或 Markdown 格式。")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "doc_id", Map.of("type", "string", "description", "文档 ID"),
                                "content", Map.of("type", "string", "description", "要追加的文本内容，Markdown 格式")
                        ),
                        "required", java.util.List.of("doc_id", "content")
                ))
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromSupplier(() -> {
            try {
                String docId = (String) arguments.get("doc_id");
                String content = (String) arguments.get("content");

                if (docId == null || docId.isBlank()) return ToolResult.error("doc_id is required");
                if (content == null || content.isBlank()) return ToolResult.error("content is required");

                var ctx = ToolExecutionContext.current();
                String connectionId = ctx != null ? ctx.getConnectionId() : null;
                String runId = ctx != null ? ctx.getRunId() : null;
                String ownerId = resolveOwnerId(ctx);

                // Push to frontend editor via WebSocket event in real-time
                if (connectionId != null && runId != null) {
                    eventBus.publish(AgentEvent.docContentInsert(connectionId, runId, content));
                } else {
                    log.warn("DocInsertTool: no connection context, event not published");
                }

                // Persist: append to document content in DB
                try {
                    var doc = documentService.get(docId, ownerId);
                    String newContent = doc.getContent() + "\n" + content;
                    int wordCount = newContent.trim().split("\\s+").length;
                    documentService.update(docId, doc.getTitle(), newContent, wordCount, ownerId);
                } catch (Exception e) {
                    log.warn("DocInsertTool: failed to persist content: {}", e.getMessage());
                }

                return ToolResult.success("内容已插入文档");
            } catch (Exception e) {
                return ToolResult.error("Failed to insert content: " + e.getMessage());
            }
        });
    }

    private String resolveOwnerId(ToolExecutionContext ctx) {
        if (ctx == null || ctx.getConnectionId() == null) return "local-default";
        var principal = connectionManager.getPrincipal(ctx.getConnectionId());
        return principal != null ? principal.getPrincipalId() : "local-default";
    }
}
```

**Note:** `DocInsertTool` needs `ConnectionManager` injected — add it as a constructor-injected field (`private final ConnectionManager connectionManager`).

- [ ] **Step 3: Compile**

```bash
./mvnw compile -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/tools/builtin/document/
git commit -m "feat(tools): add DocReadTool and DocInsertTool for document writer agent"
```

---

### Task 6: HttpGetTool

**Goal:** A tool that fetches a URL's text content for use by the document writer agent. Uses Jsoup (already a transitive dep via Tika in most Spring Boot apps — verify first; if not present, add to pom.xml).

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/tools/builtin/document/HttpGetTool.java`

- [ ] **Step 1: Verify Jsoup dependency**

```bash
./mvnw dependency:tree -q | grep jsoup
```

If not found, add to `pom.xml`:
```xml
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>1.17.2</version>
</dependency>
```

- [ ] **Step 2: Create `HttpGetTool.java`**

```java
package com.jaguarliu.ai.tools.builtin.document;

import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.Map;

@Slf4j
@Component
public class HttpGetTool implements Tool {

    private static final int MAX_CONTENT_LENGTH = 8000; // chars
    private static final int TIMEOUT_MS = 10_000;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("http_get")
                .description("获取指定 URL 的网页内容（纯文本）。适用于内网链接或用户提供的文档链接。返回页面主要文字内容，不包含 HTML 标签。内容超长时自动截断。")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "url", Map.of("type", "string", "description", "要获取的网页 URL")
                        ),
                        "required", java.util.List.of("url")
                ))
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromSupplier(() -> {
            String url = (String) arguments.get("url");
            if (url == null || url.isBlank()) return ToolResult.error("url is required");

            try {
                Document doc = Jsoup.connect(url)
                        .timeout(TIMEOUT_MS)
                        .userAgent("Mozilla/5.0 (compatible; JaguarClaw/1.0)")
                        .get();
                String text = doc.body().text();
                if (text.length() > MAX_CONTENT_LENGTH) {
                    text = text.substring(0, MAX_CONTENT_LENGTH) + "...[内容已截断]";
                }
                return ToolResult.success("【页面标题】" + doc.title() + "\n\n【页面内容】\n" + text);
            } catch (Exception e) {
                log.warn("HttpGetTool failed for url={}: {}", url, e.getMessage());
                return ToolResult.error("无法获取页面内容: " + e.getMessage());
            }
        });
    }
}
```

- [ ] **Step 3: Compile**

```bash
./mvnw compile -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/tools/builtin/document/HttpGetTool.java
git commit -m "feat(tools): add HttpGetTool for document writer agent (Jsoup page fetching)"
```

---

### Task 7: Document-Writer AgentProfile + Update DocumentAiAssistHandler

**Goal:** Register a `document-writer` agent profile with restricted tools (`doc_read`, `doc_insert`, `http_get`, `use_skill`). Update `DocumentAiAssistHandler` to invoke `AgentRuntime` with this profile instead of calling `LlmClient` directly.

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/agents/service/AgentProfileService.java` (add `ensureDocumentWriterAgentExists()`)
- Modify: `src/main/java/com/jaguarliu/ai/agents/service/AgentProfileBootstrapInitializer.java` (call the new method)
- Modify: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/document/DocumentAiAssistHandler.java`

**Read first:**
1. `src/main/java/com/jaguarliu/ai/runtime/RunContext.java` — full file; find the `create()` factory signature and whether `agentId` can be set post-creation via `setAgentAllowedTools`
2. `src/main/java/com/jaguarliu/ai/agents/entity/AgentProfileEntity.java` — full file; all builder fields (especially `canSpawn`, `sandbox` if they exist)
3. `src/main/java/com/jaguarliu/ai/agents/service/AgentProfileService.java` — the `normalizeWorkspacePath()` and `ensureWorkspaceDirectories()` helper methods
4. `src/main/java/com/jaguarliu/ai/runtime/ContextBuilder.java` — how skill base path is resolved per agentId (search for `skillBasePath` or `skills`)

- [ ] **Step 1: Add `ensureDocumentWriterAgentExists()` to `AgentProfileService`**

Add this method after `ensureDefaultMainAgentExists()`, following the identical pattern. The `normalizeWorkspacePath()` and `ensureWorkspaceDirectories()` helpers are already in the class.

```java
public void ensureDocumentWriterAgentExists() {
    if (repository.existsById("document-writer")) return;

    String workspacePath = normalizeWorkspacePath("document-writer", null);
    ensureWorkspaceDirectories(workspacePath);

    AgentProfileEntity entity = AgentProfileEntity.builder()
            .id("document-writer")
            .name("document-writer")
            .displayName("文档写作助手")
            .workspacePath(workspacePath)
            .enabled(true)
            .isDefault(false)
            .allowedTools("[\"doc_read\", \"doc_insert\", \"http_get\", \"use_skill\"]")
            .excludedTools("[]")
            .build();
    // Note: if AgentProfileEntity has canSpawn or sandbox fields, add:
    //   .canSpawn(false).sandbox("restricted")
    // Read AgentProfileEntity.java to confirm which fields exist.

    try {
        repository.save(entity);
        log.info("Bootstrapped document-writer agent profile");
    } catch (DataIntegrityViolationException ex) {
        log.debug("document-writer bootstrap skipped due to concurrent initialization", ex);
    }
}
```

- [ ] **Step 2: Call `ensureDocumentWriterAgentExists()` in `AgentProfileBootstrapInitializer`**

```java
@Override
public void run(ApplicationArguments args) {
    agentProfileService.ensureDefaultMainAgentExists();
    agentProfileService.ensureDocumentWriterAgentExists(); // ADD THIS
    // ... rest unchanged
}
```

- [ ] **Step 3: Update `DocumentAiAssistHandler` to use `AgentRuntime`**

The actual `AgentRuntime.executeLoop` method signature (confirmed from source):
```java
public String executeLoop(String connectionId, String runId, String sessionId,
                          List<LlmRequest.Message> messages, String originalInput)
        throws TimeoutException
```

And `executeLoopWithContext(RunContext context, List<LlmRequest.Message> messages)` takes a pre-built context. Use the latter so we can set `agentAllowedTools` to enforce the document-writer tool restriction.

**Key pattern:** Generate a runId → build RunContext → set allowed tools → run async on `boundedElastic` → return runId immediately.

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentAiAssistHandler implements RpcHandler {

    private final DocumentService documentService;
    private final DocumentConfigService documentConfigService;
    private final AgentRuntime agentRuntime;
    private final LoopConfig loopConfig;
    private final CancellationManager cancellationManager;
    private final EventBus eventBus;
    private final ConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    private static final Set<String> DOC_WRITER_TOOLS =
            Set.of("doc_read", "doc_insert", "http_get", "use_skill");

    @Override
    public String getMethod() { return "document.ai.assist"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.defer(() -> {
            try {
                Map<String, Object> p = objectMapper.convertValue(
                        request.getPayload(), new TypeReference<Map<String, Object>>() {});
                String docId    = (String) p.get("docId");
                String action   = (String) p.get("action");
                String selection = (String) p.get("selection");

                if (docId == null || docId.isBlank())
                    return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "docId is required"));
                var validActions = Set.of("continue", "optimize", "rewrite", "summarize", "translate");
                if (action == null || !validActions.contains(action))
                    return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "Unknown action: " + action));

                String ownerId = resolveOwner(connectionId);
                var doc = documentService.get(docId, ownerId);
                String systemPrompt = documentConfigService.getSystemPrompt();
                String userMessage = buildUserMessage(action, docId, doc.getContent(), selection);

                String runId = "doc-assist-" + UUID.randomUUID();

                // Build messages list
                List<LlmRequest.Message> messages = new java.util.ArrayList<>();
                messages.add(LlmRequest.Message.system(systemPrompt));
                messages.add(LlmRequest.Message.user(userMessage));

                // Build RunContext — restricts tools to document-writer toolset
                // RunContext.create() signature confirmed: (runId, connectionId, sessionId, loopConfig, cancellationManager)
                RunContext context = RunContext.create(runId, connectionId, null, loopConfig, cancellationManager);
                context.setAgentAllowedTools(DOC_WRITER_TOOLS);
                context.setOriginalInput(userMessage);

                // Run agent asynchronously; return runId immediately so frontend can subscribe
                Mono.fromRunnable(() -> {
                    try {
                        agentRuntime.executeLoopWithContext(context, messages, userMessage);
                    } catch (java.util.concurrent.TimeoutException e) {
                        log.warn("doc.ai.assist timeout: runId={}", runId);
                    } catch (Exception e) {
                        log.error("doc.ai.assist error: runId={}", runId, e);
                        eventBus.publish(AgentEvent.lifecycleError(connectionId, runId, e.getMessage()));
                    }
                }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic()).subscribe();

                return Mono.just(RpcResponse.success(request.getId(), Map.of("streamRunId", runId)));

            } catch (Exception e) {
                log.error("document.ai.assist setup failed: {}", e.getMessage(), e);
                return Mono.just(RpcResponse.error(request.getId(), "AI_ASSIST_ERROR", e.getMessage()));
            }
        });
    }

    private String buildUserMessage(String action, String docId, String content, String selection) {
        String target = (selection != null && !selection.isBlank()) ? selection : content;
        String actionDesc = switch (action) {
            case "continue"  -> "请续写以下文档内容";
            case "optimize"  -> "请润色以下文档内容，改善表达";
            case "rewrite"   -> "请改写以下内容，使其更清晰";
            case "summarize" -> "请总结以下内容的核心要点";
            case "translate" -> "请翻译以下内容（中英互译）";
            default -> action;
        };
        return actionDesc + "。\n\n文档 ID: " + docId + "\n\n内容：\n" + target;
    }

    private String resolveOwner(String cid) {
        var principal = connectionManager.getPrincipal(cid);
        return principal != null ? principal.getPrincipalId() : "local-default";
    }
}
```

**Note on RunContext:** If `RunContext.create()` doesn't exist or has a different signature, read `RunContext.java` and adapt. The critical constraint is: `setAgentAllowedTools(DOC_WRITER_TOOLS)` must be called before `executeLoopWithContext` so the ToolVisibilityResolver restricts the LLM to only see the document-writer tools.

- [ ] **Step 4: Create document-writer skills directory**

Skills are resolved by `ContextBuilder` using the agent's workspace/skillsBasePath. For the document-writer agent, create the skills directory so `use_skill` calls don't fail with "directory not found":

```bash
mkdir -p ~/.jaguarclaw/skills/document-writer/
```

Then read `src/main/java/com/jaguarliu/ai/runtime/ContextBuilder.java` and search for `skillBasePath` or `skills` to verify the exact path it looks in for agentId `"document-writer"`. If the path differs from `~/.jaguarclaw/skills/document-writer/`, set it explicitly in the handler via:
```java
context.setSkillBasePath(Path.of(System.getProperty("user.home"), ".jaguarclaw", "skills", "document-writer"));
```
If ContextBuilder doesn't use this path at all and derives its own, that's fine — `use_skill` will still work via ContextBuilder's resolution, just the directory may need to exist elsewhere.

- [ ] **Step 5: Compile and start backend**

```bash
./mvnw compile -q
./mvnw spring-boot:run -q &
# Check logs for: "Created default document-writer agent profile"
```

- [ ] **Step 6: Basic smoke test**

Using the running frontend, open a document, click "续写". Check backend logs to confirm:
- Agent run starts with profile `document-writer`
- Tools restricted to allowed set
- Events published to frontend

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/agents/service/ \
        src/main/java/com/jaguarliu/ai/gateway/rpc/handler/document/DocumentAiAssistHandler.java
git commit -m "feat(agent): add document-writer AgentProfile with restricted toolset and update AI assist handler"
```

---

## Chunk 3: Frontend Agent Wiring

### Task 8: DocumentStatusBar Component

**Goal:** A status bar shown above the editor body while the document AI agent is running. Displays streaming `assistant.delta` text (the agent's thinking) and a "Stop" button. Replaces the current `DocumentAiIndicator` for the "running" state (keep `DocumentAiIndicator` for the post-completion keep/discard decision).

**Files:**
- Create: `jaguarclaw-ui/src/components/documents/DocumentStatusBar.vue`
- Modify: `jaguarclaw-ui/src/views/WorkspaceView.vue`

- [ ] **Step 1: Create `DocumentStatusBar.vue`**

```vue
<!-- jaguarclaw-ui/src/components/documents/DocumentStatusBar.vue -->
<script setup lang="ts">
defineProps<{ statusText: string }>()
defineEmits<{ stop: [] }>()
</script>

<template>
  <div class="doc-status-bar">
    <div class="doc-status-bar__left">
      <span class="doc-status-bar__dot" />
      <span class="doc-status-bar__text">{{ statusText || 'AI 正在写作…' }}</span>
    </div>
    <button class="doc-status-bar__stop" @click="$emit('stop')">■ 停止</button>
  </div>
</template>

<style scoped>
.doc-status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 20px;
  background: var(--color-gray-50);
  border-bottom: var(--border);
  flex-shrink: 0;
  gap: 12px;
}
.doc-status-bar__left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.doc-status-bar__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #4ade80;
  animation: pulse 1.2s ease-in-out infinite;
  flex-shrink: 0;
}
@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(0.85); }
}
.doc-status-bar__text {
  font-size: 12px;
  color: var(--color-gray-600);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 480px;
  font-family: var(--font-ui);
}
.doc-status-bar__stop {
  padding: 3px 10px;
  font-size: 11px;
  font-family: var(--font-ui);
  border: var(--border);
  border-radius: var(--radius-full);
  background: var(--color-white);
  color: var(--color-gray-700);
  cursor: pointer;
  flex-shrink: 0;
}
.doc-status-bar__stop:hover {
  background: var(--color-gray-100);
  border-color: var(--color-gray-300);
}
</style>
```

- [ ] **Step 2: Add status text state and subscription to `useDocuments.ts`**

The status bar shows the latest `assistant.delta` content while streaming. Add a `statusText` ref and update it:

In `useDocuments.ts`, add module-level:
```typescript
const aiStatusText = ref('')
```

In `aiAssist()`, reset it:
```typescript
aiStatusText.value = ''
```

In the `assistant.delta` subscriber (where `onChunk` is called), also update statusText with the last N chars of accumulated thinking — but since `assistant.delta` in agent mode is the agent's reasoning text (not directly inserted content), show the last line of the thinking:
```typescript
// In onEvent('assistant.delta', ...) callback:
aiStatusText.value = chunk.slice(0, 80) // show most recent thinking fragment
```

In `stopAiStream()`, clear it:
```typescript
aiStatusText.value = ''
```

Expose in return:
```typescript
aiStatusText: readonly(aiStatusText),
```

- [ ] **Step 3: Subscribe to `doc.content.insert` events in `useDocuments.ts`**

Add a new subscription inside `aiAssist()`:
```typescript
let aiUnsubInsert: (() => void) | null = null
// ... module-level variable

// Inside aiAssist(), after aiUnsubEnd setup:
aiUnsubInsert = onEvent('doc.content.insert', (event: RpcEvent) => {
  if (event.runId === streamRunId) {
    if (event.payload && typeof event.payload === 'object' && 'content' in event.payload) {
      const chunk = (event.payload as { content: string }).content
      onChunk?.(chunk) // calls docEditorRef.insertChunk in WorkspaceView
    }
  }
})
```

Also clean up in `stopAiStream()`:
```typescript
aiUnsubInsert?.()
aiUnsubInsert = null
```

Export `aiStatusText` from the composable.

- [ ] **Step 4: Wire `DocumentStatusBar` into `WorkspaceView.vue`**

Add import and state to WorkspaceView:
```typescript
import DocumentStatusBar from '@/components/documents/DocumentStatusBar.vue'

// In useDocuments() destructuring, add:
const { ..., aiStatusText: docAiStatusText } = useDocuments()
```

In the template, inside the `<div class="main-area doc-main">`, add the status bar ABOVE the `DocumentEditor`, conditionally:
```html
<DocumentStatusBar
  v-if="docAiStreaming"
  :status-text="docAiStatusText"
  @stop="onDocAiDiscard"
/>
<DocumentEditor ... />
```

- [ ] **Step 5: Manual verification**

1. Open a document with content, click "续写"
2. Confirm status bar appears above the editor with pulsing green dot and thinking text
3. Confirm "■ 停止" button stops the agent
4. Confirm content inserted by `doc_insert` tool appears in the TipTap editor
5. Confirm status bar disappears after completion

- [ ] **Step 6: Commit**

```bash
git add src/components/documents/DocumentStatusBar.vue \
        src/composables/useDocuments.ts \
        src/views/WorkspaceView.vue
git commit -m "feat(docs): add DocumentStatusBar showing agent thinking, subscribe doc.content.insert events"
```

---

### Task 9: AI Settings Popover in DocumentEditor Toolbar

**Goal:** Add an "AI 设置" button in the document editor's title toolbar. Clicking it opens a popover where users can view and edit the global system prompt. On save, calls `document.config.set` RPC.

**Files:**
- Create: `jaguarclaw-ui/src/components/documents/DocumentAiSettingsPopover.vue`
- Modify: `jaguarclaw-ui/src/components/documents/DocumentEditor.vue`
- Modify: `jaguarclaw-ui/src/composables/useDocuments.ts`

- [ ] **Step 1: Add config RPC methods to `useDocuments.ts`**

```typescript
// Add inside useDocuments() (not module-level — these are request/response):
async function getConfig(): Promise<string> {
  const result = await request<{ systemPrompt: string }>('document.config.get', {})
  return result.systemPrompt
}

async function setConfig(systemPrompt: string): Promise<void> {
  await request('document.config.set', { systemPrompt })
}

// Add to return object:
return { ..., getConfig, setConfig }
```

- [ ] **Step 2: Create `DocumentAiSettingsPopover.vue`**

```vue
<!-- jaguarclaw-ui/src/components/documents/DocumentAiSettingsPopover.vue -->
<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{ modelValue: boolean; systemPrompt: string }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  save: [systemPrompt: string]
}>()

const localPrompt = ref(props.systemPrompt)

watch(() => props.systemPrompt, (v) => { localPrompt.value = v })
watch(() => props.modelValue, (v) => { if (v) localPrompt.value = props.systemPrompt })

function handleSave() {
  emit('save', localPrompt.value)
  emit('update:modelValue', false)
}
</script>

<template>
  <Teleport to="body">
    <div v-if="modelValue" class="ai-settings-overlay" @click.self="$emit('update:modelValue', false)">
      <div class="ai-settings-popover">
        <div class="ai-settings-popover__header">
          <span>AI 写作设置</span>
          <button class="ai-settings-popover__close" @click="$emit('update:modelValue', false)">✕</button>
        </div>
        <div class="ai-settings-popover__body">
          <label class="ai-settings-popover__label">全局系统提示词</label>
          <p class="ai-settings-popover__hint">这个提示词会告诉 AI 如何写作。修改后对所有文档生效。</p>
          <textarea
            v-model="localPrompt"
            class="ai-settings-popover__textarea"
            rows="12"
            placeholder="输入系统提示词…"
          />
        </div>
        <div class="ai-settings-popover__footer">
          <button class="ai-settings-popover__cancel" @click="$emit('update:modelValue', false)">取消</button>
          <button class="ai-settings-popover__save" @click="handleSave">保存</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.ai-settings-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.3);
  z-index: 300;
  display: flex;
  align-items: center;
  justify-content: center;
}
.ai-settings-popover {
  background: var(--color-white);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  width: 560px;
  max-width: 90vw;
  display: flex;
  flex-direction: column;
  max-height: 80vh;
}
.ai-settings-popover__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: var(--border);
  font-size: 14px;
  font-weight: 600;
  color: var(--color-gray-900);
}
.ai-settings-popover__close {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 14px;
  color: var(--color-gray-500);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
}
.ai-settings-popover__close:hover { background: var(--color-gray-100); }
.ai-settings-popover__body {
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  overflow-y: auto;
}
.ai-settings-popover__label {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-gray-700);
}
.ai-settings-popover__hint {
  font-size: 12px;
  color: var(--color-gray-500);
  margin: 0;
}
.ai-settings-popover__textarea {
  width: 100%;
  font-size: 12px;
  font-family: var(--font-mono);
  border: var(--border);
  border-radius: var(--radius-md);
  padding: 10px;
  resize: vertical;
  line-height: 1.6;
  color: var(--color-gray-900);
  box-sizing: border-box;
}
.ai-settings-popover__textarea:focus { outline: none; border-color: var(--color-primary, #6366f1); }
.ai-settings-popover__footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px;
  border-top: var(--border);
}
.ai-settings-popover__cancel {
  padding: 6px 16px;
  font-size: 13px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  color: var(--color-gray-700);
  cursor: pointer;
}
.ai-settings-popover__save {
  padding: 6px 16px;
  font-size: 13px;
  border: none;
  border-radius: var(--radius-md);
  background: var(--color-gray-900);
  color: white;
  cursor: pointer;
  font-weight: 500;
}
.ai-settings-popover__save:hover { background: var(--color-gray-700); }
</style>
```

- [ ] **Step 3: Wire into `DocumentEditor.vue`**

The editor receives `getConfig`/`setConfig` via new emits, or we can have the editor manage config directly by emitting events to WorkspaceView. Simplest: pass `systemPrompt` as a prop and emit `update-config`.

**Alternative (simpler):** Add a new prop `aiSettingsVisible` and emit `ai-settings-open`. Handle config loading in the `DocumentEditor` via a new prop `onOpenSettings` callback. Given the complexity, use this approach:

- Add a new event emit to `DocumentEditor`: `@ai-settings` (no payload)
- Add "AI 设置" button to the `.doc-editor__actions` div in the template
- WorkspaceView handles the popover display with its own state

In `DocumentEditor.vue` template, in `.doc-editor__actions`:
```html
<button @click="emit('ai-settings')" style="margin-left: 8px">⚙ AI 设置</button>
```

In `DocumentEditor.vue` script, add `aiSettings` to emit:
```typescript
const emit = defineEmits<{
  change: [title: string, content: string, wordCount: number]
  aiAction: [action: string, selection?: string]
  aiSettings: []  // ADD
}>()
```

- [ ] **Step 4: Wire popover in `WorkspaceView.vue`**

```typescript
import DocumentAiSettingsPopover from '@/components/documents/DocumentAiSettingsPopover.vue'

const showAiSettings = ref(false)
const aiSystemPrompt = ref('')
const { ..., getConfig, setConfig } = useDocuments()

async function onDocAiSettings() {
  aiSystemPrompt.value = await getConfig()
  showAiSettings.value = true
}

async function onDocAiSettingsSave(prompt: string) {
  await setConfig(prompt)
}
```

In WorkspaceView template, inside the `<div class="main-area doc-main">`:
```html
<DocumentEditor
  ref="docEditorRef"
  ...
  @ai-settings="onDocAiSettings"
/>
...
<DocumentAiSettingsPopover
  v-model="showAiSettings"
  :system-prompt="aiSystemPrompt"
  @save="onDocAiSettingsSave"
/>
```

- [ ] **Step 5: Manual verification**

1. Click "⚙ AI 设置" button in document editor toolbar
2. Confirm modal opens with current system prompt text
3. Edit the prompt, click "保存"
4. Reopen settings — confirm the new prompt is loaded
5. Check `~/.jaguarclaw/document-writer-config.json` exists with the saved prompt

- [ ] **Step 6: Commit**

```bash
git add src/components/documents/DocumentAiSettingsPopover.vue \
        src/components/documents/DocumentEditor.vue \
        src/composables/useDocuments.ts \
        src/views/WorkspaceView.vue
git commit -m "feat(docs): add AI settings popover for editing global system prompt"
```

---

## Final Verification Checklist

After all tasks are complete, verify end-to-end:

- [ ] Formatting toolbar works (bold, italic, headings, lists, code, mermaid insert)
- [ ] Slash command `/` opens menu, filters by typing, arrows + Enter selects, Esc closes
- [ ] Slash AI commands trigger the same AI flow as toolbar buttons
- [ ] AI settings popover loads and saves system prompt (persisted across restarts)
- [ ] Document writer runs with restricted tools (backend logs show `document-writer` profile)
- [ ] `doc_read` tool visible in backend logs when agent reads document
- [ ] `doc_insert` tool causes content to appear in TipTap editor in real-time
- [ ] Status bar shows agent thinking text during AI run, disappears when done
- [ ] "■ 停止" button cancels the agent run
- [ ] Keep/Discard indicator still works after completion (DocumentAiIndicator)
- [ ] All existing document CRUD still works (create, rename, delete, save)
