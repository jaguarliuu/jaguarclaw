<script setup lang="ts">
import { useEditor, EditorContent } from '@tiptap/vue-3'
import { BubbleMenuPlugin } from '@tiptap/extension-bubble-menu'
import StarterKit from '@tiptap/starter-kit'
import Placeholder from '@tiptap/extension-placeholder'
import { watch, onBeforeUnmount, onMounted, ref, nextTick } from 'vue'
import type { Document } from '@/types'
import DocumentBubbleMenu from './DocumentBubbleMenu.vue'

const props = defineProps<{
  document: Document | null
  saving: boolean
  aiStreaming: boolean
}>()

const emit = defineEmits<{
  change: [title: string, content: string, wordCount: number]
  aiAction: [action: string, selection?: string]
}>()

const titleValue = ref(props.document?.title ?? '')
const bubbleMenuRef = ref<HTMLElement | null>(null)
const bubbleVisible = ref(false)

const editor = useEditor({
  extensions: [
    StarterKit,
    Placeholder.configure({ placeholder: '开始输入…' }),
  ],
  editorProps: {
    attributes: { class: 'doc-editor__prose' },
  },
  onUpdate({ editor }) {
    if (!props.document) return
    const content = JSON.stringify(editor.getJSON())
    const wordCount = editor.getText().trim().split(/\s+/).filter(Boolean).length
    emit('change', titleValue.value, content, wordCount)
  },
  onSelectionUpdate({ editor: ed }) {
    const { empty } = ed.state.selection
    bubbleVisible.value = !empty
  },
  onBlur() {
    // keep bubble visible briefly so user can click it
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
  const selection = empty ? undefined : editor.value.state.doc.textBetween(from, to)
  emit('aiAction', action, selection)
}
</script>

<template>
  <div class="doc-editor" v-if="document">
    <div class="doc-editor__toolbar">
      <input
        class="doc-editor__title"
        :value="titleValue"
        placeholder="Untitled"
        @input="onTitleInput"
      />
      <div class="doc-editor__actions">
        <button @click="handleAiAction('continue')" :disabled="aiStreaming">续写</button>
        <button @click="handleAiAction('optimize')" :disabled="aiStreaming">润色全文</button>
        <button @click="handleAiAction('summarize')" :disabled="aiStreaming">总结</button>
        <span class="doc-editor__save-status">{{ saving ? '保存中…' : '已保存' }}</span>
      </div>
    </div>

    <div ref="bubbleMenuRef" class="bubble-menu-wrapper">
      <DocumentBubbleMenu :ai-streaming="aiStreaming" @action="handleAiAction" />
    </div>

    <div class="doc-editor__body">
      <EditorContent :editor="editor" />
    </div>
  </div>
  <div v-else class="doc-editor__empty">
    <p>从左侧选择一个文档，或点击「＋」新建。</p>
  </div>
</template>

<style scoped>
.doc-editor { flex: 1; display: flex; flex-direction: column; overflow: hidden; background: var(--color-white); }
.doc-editor__toolbar {
  display: flex; align-items: center; gap: var(--space-3);
  padding: var(--space-3) var(--space-6); border-bottom: var(--border); flex-shrink: 0;
}
.doc-editor__title {
  flex: 1; font-size: 18px; font-weight: 600;
  border: none; outline: none; background: transparent; color: var(--color-gray-900);
  font-family: var(--font-ui);
}
.doc-editor__actions { display: flex; align-items: center; gap: var(--space-2); }
.doc-editor__actions button {
  padding: var(--space-1) var(--space-3); font-size: 12px;
  border: var(--border); border-radius: var(--radius-md);
  background: var(--color-white); cursor: pointer; font-family: var(--font-ui);
}
.doc-editor__actions button:hover:not(:disabled) { background: var(--color-gray-100); }
.doc-editor__actions button:disabled { opacity: 0.5; cursor: default; }
.doc-editor__save-status { font-size: 11px; color: var(--color-gray-400); }
.bubble-menu-wrapper { position: absolute; z-index: 100; visibility: hidden; opacity: 0; }
.doc-editor__body { flex: 1; overflow-y: auto; padding: var(--space-6) var(--space-8); }
.doc-editor__empty {
  flex: 1; display: flex; align-items: center; justify-content: center;
  color: var(--color-gray-400); font-size: 14px;
}
:global(.doc-editor__prose) {
  outline: none; font-family: var(--font-ui); font-size: 15px; line-height: 1.7;
  color: var(--color-gray-900); min-height: 400px; max-width: 680px; margin: 0 auto;
}
:global(.doc-editor__prose p) { margin: 0 0 var(--space-2); }
:global(.doc-editor__prose h1) { font-size: 26px; font-weight: 700; margin: var(--space-4) 0 var(--space-2); }
:global(.doc-editor__prose h2) { font-size: 20px; font-weight: 600; margin: var(--space-3) 0 var(--space-2); }
:global(.doc-editor__prose h3) { font-size: 16px; font-weight: 600; margin: var(--space-2) 0 var(--space-1); }
:global(.doc-editor__prose ul, .doc-editor__prose ol) { padding-left: var(--space-5); margin: var(--space-2) 0; }
:global(.doc-editor__prose code) {
  background: var(--color-gray-100); padding: 1px 5px;
  border-radius: 3px; font-family: var(--font-mono); font-size: 13px;
}
:global(.doc-editor__prose pre) {
  background: var(--color-gray-900); color: var(--color-gray-100);
  padding: var(--space-4); border-radius: var(--radius-md); overflow-x: auto;
}
:global(.doc-editor__prose blockquote) {
  border-left: 3px solid var(--color-gray-300); padding-left: var(--space-3); color: var(--color-gray-500);
}
:global(.doc-editor__prose .is-editor-empty:first-child::before) {
  content: attr(data-placeholder); color: var(--color-gray-400);
  float: left; pointer-events: none; height: 0;
}
</style>
