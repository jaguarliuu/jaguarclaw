<script setup lang="ts">
import { useEditor, EditorContent, VueNodeViewRenderer } from '@tiptap/vue-3'
import { Node } from '@tiptap/core'
import { BubbleMenuPlugin } from '@tiptap/extension-bubble-menu'
import StarterKit from '@tiptap/starter-kit'
import CodeBlock from '@tiptap/extension-code-block'
import Placeholder from '@tiptap/extension-placeholder'
import Image from '@tiptap/extension-image'
import Link from '@tiptap/extension-link'
import { Table, TableRow, TableHeader, TableCell } from '@tiptap/extension-table'
import { watch, onBeforeUnmount, onMounted, ref, nextTick } from 'vue'
import { marked } from 'marked'
import MermaidBlockView from './MermaidBlockView.vue'
import StreamingBlockView from './StreamingBlockView.vue'
import type { Document } from '@/types'
import DocumentBubbleMenu from './DocumentBubbleMenu.vue'
import DocumentFormatToolbar from './DocumentFormatToolbar.vue'
import { createSlashExtension } from './SlashExtension'
import DocumentAiPromptBubble from './DocumentAiPromptBubble.vue'

const props = defineProps<{
  document: Document | null
  saving: boolean
  aiStreaming: boolean
}>()

const emit = defineEmits<{
  change: [title: string, content: string, wordCount: number]
  aiAction: [action: string, selection?: string, userPrompt?: string]
  aiSettings: []
}>()

const titleValue = ref(props.document?.title ?? '')
const bubbleMenuRef = ref<HTMLElement | null>(null)
const imageFileInput = ref<HTMLInputElement | null>(null)

const aiPromptVisible = ref(false)
const aiPromptAction = ref('')
const aiPromptX = ref(0)
const aiPromptY = ref(0)
const aiPromptSelection = ref<string | undefined>(undefined)

const MermaidCodeBlock = CodeBlock.extend({
  addNodeView() {
    return VueNodeViewRenderer(MermaidBlockView as any)
  }
})

const StreamingBlock = Node.create({
  name: 'streamingBlock',
  group: 'block',
  atom: true,
  addAttributes() {
    return { content: { default: '' } }
  },
  parseHTML() { return [{ tag: 'div[data-streaming-block]' }] },
  renderHTML({ HTMLAttributes }) { return ['div', { 'data-streaming-block': '', ...HTMLAttributes }] },
  addNodeView() { return VueNodeViewRenderer(StreamingBlockView as any) },
})

const editor = useEditor({
  extensions: [
    StarterKit.configure({ codeBlock: false }),
    MermaidCodeBlock,
    StreamingBlock,
    Placeholder.configure({ placeholder: '开始输入…' }),
    createSlashExtension((action) => {
      handleAiAction(action)
    }),
    Image.configure({ allowBase64: true }),
    Link.configure({ openOnClick: false, HTMLAttributes: { class: 'doc-link' } }),
    Table.configure({ resizable: false }),
    TableRow,
    TableHeader,
    TableCell,
  ],
  editorProps: {
    attributes: { class: 'doc-editor__prose' },
    handlePaste(_, event) {
      const items = Array.from(event.clipboardData?.items ?? [])
      const imageItem = items.find(item => item.type.startsWith('image/'))
      if (!imageItem) return false
      const file = imageItem.getAsFile()
      if (!file) return false
      uploadDocImage(file)
      return true
    },
  },
  onUpdate({ editor }) {
    if (!props.document) return
    const content = JSON.stringify(editor.getJSON())
    const wordCount = editor.getText().trim().split(/\s+/).filter(Boolean).length
    emit('change', titleValue.value, content, wordCount)
  },
})

watch(() => props.document, (doc) => {
  if (!editor.value || !doc) return
  titleValue.value = doc.title
  try {
    const json = JSON.parse(doc.content)
    editor.value.commands.setContent(json, { emitUpdate: false })
  } catch {
    editor.value.commands.setContent(doc.content, { emitUpdate: false })
  }
}, { immediate: true })

onMounted(async () => {
  await nextTick()
  if (editor.value && bubbleMenuRef.value) {
    editor.value.registerPlugin(
      BubbleMenuPlugin({
        pluginKey: 'bubbleMenu',
        editor: editor.value,
        element: bubbleMenuRef.value,
        appendTo: () => document.body,
      })
    )
  }
})

onBeforeUnmount(() => editor.value?.destroy())

function onTitleInput(e: Event) {
  const title = (e.target as HTMLInputElement).value
  titleValue.value = title
  if (!props.document || !editor.value) return
  const content = JSON.stringify(editor.value.getJSON())
  const wordCount = editor.value.getText().trim().split(/\s+/).filter(Boolean).length
  emit('change', title, content, wordCount)
}

function handleAiAction(action: string) {
  if (!editor.value) return
  const { from, to, empty } = editor.value.state.selection
  aiPromptSelection.value = empty ? undefined : editor.value.state.doc.textBetween(from, to)

  // Get cursor DOM coordinates for bubble positioning
  try {
    const coords = editor.value.view.coordsAtPos(from)
    // Position below cursor, clamped to viewport
    aiPromptX.value = Math.min(coords.left, window.innerWidth - 340)
    aiPromptY.value = Math.min(coords.bottom + 8, window.innerHeight - 120)
  } catch {
    // Fallback: center-ish position
    aiPromptX.value = Math.max(window.innerWidth / 2 - 180, 16)
    aiPromptY.value = 200
  }

  aiPromptAction.value = action
  aiPromptVisible.value = true
}

function handleAiPromptConfirm(userPrompt: string) {
  aiPromptVisible.value = false
  emit('aiAction', aiPromptAction.value, aiPromptSelection.value, userPrompt || undefined)
}

function handleAiPromptCancel() {
  aiPromptVisible.value = false
}

function insertChunk(text: string) {
  editor.value?.commands.insertContent(text)
}

function insertMarkdown(markdown: string) {
  if (!editor.value || !markdown.trim()) return
  const html = marked.parse(markdown) as string
  editor.value.chain().focus().insertContent(html).run()
}

// ── Streaming block ────────────────────────────────────────────────────────

function insertStreamingBlock() {
  editor.value?.chain().focus()
    .insertContent({ type: 'streamingBlock', attrs: { content: '' } })
    .run()
}

function updateStreamingBlock(content: string) {
  if (!editor.value) return
  const { state, view } = editor.value
  state.doc.descendants((node, pos) => {
    if (node.type.name === 'streamingBlock') {
      const tr = state.tr.setNodeMarkup(pos, undefined, { content })
      tr.setMeta('addToHistory', false)
      view.dispatch(tr)
      return false
    }
  })
}

function finalizeStreamingBlock(markdown: string) {
  if (!editor.value) return
  const { state } = editor.value
  let blockPos: number | null = null
  let blockEnd: number | null = null
  state.doc.descendants((node, pos) => {
    if (node.type.name === 'streamingBlock' && blockPos === null) {
      blockPos = pos
      blockEnd = pos + node.nodeSize
    }
  })
  if (blockPos === null) return
  if (!markdown.trim()) {
    editor.value.chain().command(({ tr }) => { tr.delete(blockPos!, blockEnd!); return true }).run()
    return
  }
  const html = marked.parse(markdown) as string
  editor.value.chain()
    .command(({ tr }) => { tr.delete(blockPos!, blockEnd!); return true })
    .insertContentAt(blockPos!, html)
    .run()
}

function removeStreamingBlock() {
  if (!editor.value) return
  const { state } = editor.value
  let blockPos: number | null = null
  let blockEnd: number | null = null
  state.doc.descendants((node, pos) => {
    if (node.type.name === 'streamingBlock' && blockPos === null) {
      blockPos = pos
      blockEnd = pos + node.nodeSize
    }
  })
  if (blockPos !== null) {
    editor.value.chain().command(({ tr }) => { tr.delete(blockPos!, blockEnd!); return true }).run()
  }
}

function openImageFilePicker() {
  imageFileInput.value?.click()
}

async function uploadDocImage(file: File) {
  if (!editor.value) return
  const ext = file.name.split('.').pop()?.toLowerCase() ?? 'png'
  const filename = `paste.${ext}`
  const formData = new FormData()
  formData.append('file', file, filename)
  try {
    const res = await fetch('/api/doc-images', { method: 'POST', body: formData })
    if (!res.ok) throw new Error(await res.text())
    const { url } = await res.json()
    editor.value.chain().focus().setImage({ src: url }).run()
  } catch (e) {
    console.error('Image upload failed:', e)
  }
}

function onImageFileSelected(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (file) uploadDocImage(file)
  ;(e.target as HTMLInputElement).value = ''
}

defineExpose({ insertChunk, insertMarkdown, insertStreamingBlock, updateStreamingBlock, finalizeStreamingBlock, removeStreamingBlock, openImageFilePicker })
</script>

<template>
  <div class="doc-editor" v-if="document">
    <!-- Title bar -->
    <div class="doc-editor__toolbar">
      <input
        class="doc-editor__title"
        :value="titleValue"
        placeholder="Untitled"
        @input="onTitleInput"
      />
      <div class="doc-editor__save-indicator" :class="{ saving: saving }" :title="saving ? '保存中…' : '已保存'">
        <span class="doc-editor__save-dot" />
      </div>
    </div>

    <DocumentFormatToolbar
      :editor="editor"
      :ai-streaming="aiStreaming"
      @insert-image="openImageFilePicker"
      @ai-action="handleAiAction"
      @ai-settings="emit('aiSettings')"
    />

    <input
      ref="imageFileInput"
      type="file"
      accept="image/*"
      style="display: none"
      @change="onImageFileSelected"
    />

    <!-- Streaming progress bar -->
    <div v-if="aiStreaming" class="doc-editor__progress" />

    <div ref="bubbleMenuRef" class="bubble-menu-wrapper">
      <DocumentBubbleMenu :ai-streaming="aiStreaming" @action="handleAiAction" />
    </div>

    <div class="doc-editor__body">
      <EditorContent :editor="editor" />
    </div>

    <DocumentAiPromptBubble
      :visible="aiPromptVisible"
      :action="aiPromptAction"
      :x="aiPromptX"
      :y="aiPromptY"
      @confirm="handleAiPromptConfirm"
      @cancel="handleAiPromptCancel"
    />
  </div>
  <div v-else class="doc-editor__empty">
    <svg class="doc-editor__empty-icon" width="48" height="48" viewBox="0 0 48 48" fill="none" stroke="#ccc" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
      <rect x="8" y="4" width="32" height="40" rx="4"/>
      <path d="M16 14h16M16 20h16M16 26h10"/>
    </svg>
    <h3>选择文档开始写作</h3>
    <p>从左侧选择一个文档，或点击 + 新建</p>
  </div>
</template>

<style scoped>
.doc-editor {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
  position: relative;
}

/* Title toolbar */
.doc-editor__toolbar {
  display: flex;
  align-items: center;
  padding: 16px 48px 12px;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
  background: #fff;
  gap: 12px;
}

.doc-editor__title {
  flex: 1;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.5px;
  color: #111;
  border: none;
  outline: none;
  background: transparent;
  font-family: var(--font-ui);
  line-height: 1.2;
}

.doc-editor__title::placeholder {
  color: #ccc;
  font-weight: 400;
}

/* Save status dot */
.doc-editor__save-indicator {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
}

.doc-editor__save-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #d1d5db;
  transition: background 300ms ease;
}

.doc-editor__save-indicator.saving .doc-editor__save-dot {
  background: #f59e0b;
  animation: savePulse 1s ease-in-out infinite;
}

@keyframes savePulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

/* Streaming progress bar */
.doc-editor__progress {
  height: 2px;
  background: linear-gradient(90deg, #6366f1, #8b5cf6, #6366f1);
  background-size: 200% 100%;
  animation: progressSlide 1.5s linear infinite;
  flex-shrink: 0;
}

@keyframes progressSlide {
  0% { background-position: 100% 0; }
  100% { background-position: -100% 0; }
}

.bubble-menu-wrapper {
  position: absolute;
  z-index: 100;
  visibility: hidden;
  opacity: 0;
}

.doc-editor__body {
  flex: 1;
  overflow-y: auto;
  padding: 32px 48px;
}

/* Empty state */
.doc-editor__empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #aaa;
}

.doc-editor__empty-icon {
  opacity: 0.6;
  margin-bottom: 4px;
}

.doc-editor__empty h3 {
  font-size: 16px;
  font-weight: 600;
  color: #888;
  margin: 0;
}

.doc-editor__empty p {
  font-size: 13px;
  color: #bbb;
  margin: 0;
}

/* Prose styles (global) */
:global(.doc-editor__prose) {
  outline: none;
  font-family: var(--font-ui);
  font-size: 15px;
  line-height: 1.75;
  color: var(--color-gray-900);
  min-height: 400px;
  max-width: 680px;
  margin: 0 auto;
}
:global(.doc-editor__prose p) { margin: 0 0 8px; }
:global(.doc-editor__prose h1) { font-size: 26px; font-weight: 700; margin: 24px 0 8px; }
:global(.doc-editor__prose h2) { font-size: 20px; font-weight: 600; margin: 20px 0 8px; }
:global(.doc-editor__prose h3) { font-size: 16px; font-weight: 600; margin: 16px 0 6px; }
:global(.doc-editor__prose ul, .doc-editor__prose ol) { padding-left: 20px; margin: 8px 0; }
:global(.doc-editor__prose li) { margin: 2px 0; }
:global(.doc-editor__prose code) {
  background: var(--color-gray-100);
  padding: 1px 5px;
  border-radius: 3px;
  font-family: var(--font-mono);
  font-size: 13px;
}
:global(.doc-editor__prose pre) {
  background: var(--color-gray-50);
  color: var(--color-gray-800);
  border: 1px solid var(--color-gray-200);
  padding: 16px;
  border-radius: var(--radius-md);
  overflow-x: auto;
  margin: 12px 0;
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.6;
}
:global(.doc-editor__prose pre code) {
  background: transparent;
  color: inherit;
  padding: 0;
  font-size: inherit;
}
:global(.doc-editor__prose .code-block-wrapper) { margin: 12px 0; }
:global(.doc-editor__prose blockquote) {
  border-left: 3px solid var(--color-gray-300);
  padding-left: 12px;
  color: var(--color-gray-500);
  margin: 8px 0;
}
:global(.doc-editor__prose .is-editor-empty:first-child::before) {
  content: attr(data-placeholder);
  color: var(--color-gray-400);
  float: left;
  pointer-events: none;
  height: 0;
}
:global(.doc-editor__prose strong) { font-weight: 600; }
:global(.doc-editor__prose hr) { border: none; border-top: var(--border); margin: 20px 0; }
:global(.doc-editor__prose img) {
  max-width: 100%;
  border-radius: var(--radius-md);
  margin: 8px 0;
  cursor: pointer;
  border: 2px solid transparent;
}
:global(.doc-editor__prose img.ProseMirror-selectednode) {
  border-color: var(--color-primary, #6366f1);
}
:global(.doc-link) {
  color: #6366f1;
  text-decoration: underline;
  cursor: pointer;
}
:global(.doc-link:hover) { color: #4f46e5; }
:global(.doc-editor__prose table) {
  border-collapse: collapse;
  width: 100%;
  margin: 12px 0;
  font-size: 14px;
}
:global(.doc-editor__prose th, .doc-editor__prose td) {
  border: 1px solid var(--color-gray-200);
  padding: 8px 12px;
  min-width: 80px;
  position: relative;
  vertical-align: top;
}
:global(.doc-editor__prose th) {
  background: var(--color-gray-50);
  font-weight: 600;
  text-align: left;
}
:global(.doc-editor__prose .selectedCell:after) {
  background: rgba(99, 102, 241, 0.1);
  content: "";
  left: 0; right: 0; top: 0; bottom: 0;
  pointer-events: none;
  position: absolute;
  z-index: 2;
}
:global(.doc-editor__prose .column-resize-handle) {
  background-color: #6366f1;
  bottom: -2px;
  position: absolute;
  right: -2px;
  pointer-events: none;
  top: 0;
  width: 4px;
}
</style>
