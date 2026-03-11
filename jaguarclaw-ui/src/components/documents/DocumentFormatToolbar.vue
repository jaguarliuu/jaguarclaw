<!-- jaguarclaw-ui/src/components/documents/DocumentFormatToolbar.vue -->
<script setup lang="ts">
import type { Editor } from '@tiptap/vue-3'

const props = defineProps<{
  editor: Editor | undefined
  aiStreaming?: boolean
}>()

const emit = defineEmits<{
  insertImage: []
  aiAction: [action: string]
  aiSettings: []
}>()

function toggleLink() {
  if (!props.editor) return
  if (props.editor.isActive('link')) {
    props.editor.chain().focus().unsetLink().run()
    return
  }
  const url = window.prompt('输入链接 URL：')
  if (url) {
    props.editor.chain().focus().setLink({ href: url }).run()
  }
}
</script>

<template>
  <div v-if="editor" class="format-toolbar">
    <!-- Text formatting -->
    <button :class="{ active: editor.isActive('bold') }" title="Bold (Ctrl+B)"
      @click="editor?.chain().focus().toggleBold().run()"><strong>B</strong></button>
    <button :class="{ active: editor.isActive('italic') }" title="Italic (Ctrl+I)"
      @click="editor?.chain().focus().toggleItalic().run()"><em>I</em></button>
    <button :class="{ active: editor.isActive('strike') }" title="Strikethrough"
      @click="editor?.chain().focus().toggleStrike().run()"><s>S</s></button>

    <div class="format-toolbar__sep" />

    <!-- Headings -->
    <button :class="{ active: editor.isActive('heading', { level: 1 }) }" title="Heading 1"
      @click="editor?.chain().focus().toggleHeading({ level: 1 }).run()">H1</button>
    <button :class="{ active: editor.isActive('heading', { level: 2 }) }" title="Heading 2"
      @click="editor?.chain().focus().toggleHeading({ level: 2 }).run()">H2</button>
    <button :class="{ active: editor.isActive('heading', { level: 3 }) }" title="Heading 3"
      @click="editor?.chain().focus().toggleHeading({ level: 3 }).run()">H3</button>

    <div class="format-toolbar__sep" />

    <!-- Lists -->
    <button :class="{ active: editor.isActive('bulletList') }" title="Bullet list"
      @click="editor?.chain().focus().toggleBulletList().run()">&#x2261;</button>
    <button :class="{ active: editor.isActive('orderedList') }" title="Numbered list"
      @click="editor?.chain().focus().toggleOrderedList().run()">1.</button>

    <div class="format-toolbar__sep" />

    <!-- Blocks -->
    <button :class="{ active: editor.isActive('codeBlock') }" title="Code block"
      @click="editor?.chain().focus().toggleCodeBlock().run()">&lt;/&gt;</button>
    <button :class="{ active: editor.isActive('blockquote') }" title="Blockquote"
      @click="editor?.chain().focus().toggleBlockquote().run()">&ldquo;</button>
    <button title="Horizontal rule"
      @click="editor?.chain().focus().setHorizontalRule().run()">—</button>
    <button title="Insert Mermaid diagram"
      @click="editor?.chain().focus().insertContent({ type: 'codeBlock', attrs: { language: 'mermaid' }, content: [{ type: 'text', text: 'graph TD\n  A --> B' }] }).run()">◇ Flow</button>

    <div class="format-toolbar__sep" />

    <!-- Media & links -->
    <button title="Insert image (or paste/drop)" @click="emit('insertImage')">
      <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
        <rect x="1" y="1" width="12" height="12" rx="1.5"/>
        <circle cx="4.5" cy="4.5" r="1.2" fill="currentColor" stroke="none"/>
        <path d="M1 9.5 4.5 6l2.5 2.5 2-2L13 10"/>
      </svg>
    </button>
    <button :class="{ active: editor.isActive('link') }" title="Link" @click="toggleLink">
      <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round">
        <path d="M5.5 9 4.4 10.1a2.6 2.6 0 0 1-3.677-3.677L3.6 3.546A2.6 2.6 0 0 1 7.28 3.9"/>
        <path d="M8.5 5l1.1-1.1a2.6 2.6 0 0 1 3.677 3.677L10.4 10.454A2.6 2.6 0 0 1 6.72 10.1"/>
        <path d="m5 9 4-4"/>
      </svg>
    </button>

    <!-- Table insert -->
    <button title="Insert Table"
      @click="editor?.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run()">
      <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="currentColor" stroke-width="1.3">
        <rect x="1" y="1" width="12" height="12" rx="1.2"/>
        <path d="M1 5h12M1 9h12M5 5v8M9 5v8"/>
      </svg>
    </button>

    <!-- Table operations — only shown when cursor is inside a table -->
    <template v-if="editor.isActive('table')">
      <div class="format-toolbar__sep" />
      <button title="Add column before" @click="editor?.chain().focus().addColumnBefore().run()">+col&#x2190;</button>
      <button title="Add column after" @click="editor?.chain().focus().addColumnAfter().run()">+col&#x2192;</button>
      <button title="Delete column" @click="editor?.chain().focus().deleteColumn().run()">&#x2212;col</button>
      <button title="Add row above" @click="editor?.chain().focus().addRowBefore().run()">+row&#x2191;</button>
      <button title="Add row below" @click="editor?.chain().focus().addRowAfter().run()">+row&#x2193;</button>
      <button title="Delete row" @click="editor?.chain().focus().deleteRow().run()">&#x2212;row</button>
      <button title="Delete table" class="danger" @click="editor?.chain().focus().deleteTable().run()">&#x2715; 表</button>
    </template>

    <!-- Spacer pushes AI buttons to right -->
    <div class="format-toolbar__spacer" />

    <!-- AI action buttons -->
    <div class="format-toolbar__ai">
      <button
        class="ai-btn"
        :disabled="aiStreaming"
        title="续写"
        @click="emit('aiAction', 'continue')"
      >续写</button>
      <button
        class="ai-btn"
        :disabled="aiStreaming"
        title="润色全文"
        @click="emit('aiAction', 'optimize')"
      >润色</button>
      <button
        class="ai-btn"
        :disabled="aiStreaming"
        title="总结"
        @click="emit('aiAction', 'summarize')"
      >总结</button>
      <button
        class="ai-btn ai-btn--settings"
        title="AI 设置"
        @click="emit('aiSettings')"
      >
        <svg width="13" height="13" viewBox="0 0 13 13" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round">
          <circle cx="6.5" cy="6.5" r="2"/>
          <path d="M6.5 1v1.5M6.5 10.5V12M1 6.5h1.5M10.5 6.5H12M2.9 2.9l1.05 1.05M9.05 9.05l1.05 1.05M9.05 3.95l-1.05 1.05M3.95 9.05 2.9 10.1"/>
        </svg>
      </button>
    </div>
  </div>
</template>

<style scoped>
.format-toolbar {
  display: flex;
  align-items: center;
  gap: 1px;
  padding: 0 16px;
  height: 36px;
  border-bottom: 1px solid #f0f0f0;
  background: #fff;
  flex-shrink: 0;
  overflow: hidden;
}

.format-toolbar button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  padding: 0 7px;
  height: 26px;
  min-width: 28px;
  font-size: 12px;
  font-family: var(--font-ui);
  font-weight: 500;
  border: 1px solid transparent;
  border-radius: 4px;
  background: transparent;
  color: #555;
  cursor: pointer;
  line-height: 1;
  white-space: nowrap;
  transition: background 80ms ease, color 80ms ease;
  flex-shrink: 0;
}

.format-toolbar button:hover {
  background: #f5f5f5;
  color: #111;
}

.format-toolbar button.active {
  background: #ebebeb;
  color: #111;
}

.format-toolbar button.danger:hover {
  background: #fff5f5;
  border-color: #fecaca;
  color: #dc2626;
}

.format-toolbar__sep {
  width: 1px;
  height: 16px;
  background: #e8e8e8;
  margin: 0 3px;
  flex-shrink: 0;
}

.format-toolbar__spacer {
  flex: 1;
  min-width: 8px;
}

/* AI section */
.format-toolbar__ai {
  display: flex;
  align-items: center;
  gap: 2px;
  padding-left: 8px;
  border-left: 1px solid #e8e8e8;
  flex-shrink: 0;
}

.ai-btn {
  font-size: 11.5px;
  font-weight: 500;
  color: #6366f1 !important;
  padding: 0 8px !important;
}

.ai-btn:hover {
  background: #eef2ff !important;
  color: #4f46e5 !important;
}

.ai-btn:disabled {
  opacity: 0.4;
  cursor: default;
  pointer-events: none;
}

.ai-btn--settings {
  color: #888 !important;
  min-width: 26px !important;
  padding: 0 5px !important;
}

.ai-btn--settings:hover {
  background: #f5f5f5 !important;
  color: #555 !important;
}
</style>
