<!-- src/components/documents/DocumentAiIndicator.vue -->
<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'

const props = defineProps<{ streaming: boolean; content: string }>()
defineEmits<{ keep: []; discard: [] }>()

const rendered = computed(() => {
  if (!props.content) return ''
  return marked.parse(props.content) as string
})
</script>

<template>
  <div class="ai-preview">
    <div class="ai-preview__header">
      <span class="ai-preview__label">
        <span v-if="streaming" class="ai-preview__dot" />
        {{ streaming ? 'AI 正在写作…' : 'AI 写作完成，请确认插入' }}
      </span>
      <div class="ai-preview__actions">
        <button class="keep" :disabled="streaming" @click="$emit('keep')">✓ 插入文档</button>
        <button class="discard" @click="$emit('discard')">✕ 丢弃</button>
      </div>
    </div>
    <div class="ai-preview__body">
      <div v-if="rendered" class="ai-preview__content markdown-body" v-html="rendered" />
      <div v-else class="ai-preview__placeholder">内容生成中…</div>
    </div>
  </div>
</template>

<style scoped>
.ai-preview {
  position: absolute;
  bottom: 0; left: 0; right: 0;
  max-height: 40%;
  display: flex;
  flex-direction: column;
  background: var(--color-white);
  border-top: 2px solid #6366f1;
  box-shadow: 0 -4px 16px rgba(0, 0, 0, 0.08);
  z-index: 20;
}
.ai-preview__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 20px;
  border-bottom: var(--border);
  flex-shrink: 0;
  background: #f5f3ff;
}
.ai-preview__label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  font-weight: 500;
  color: #4f46e5;
}
.ai-preview__dot {
  width: 7px; height: 7px; border-radius: 50%;
  background: #6366f1;
  animation: pulse 1.2s ease-in-out infinite;
  flex-shrink: 0;
}
@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(0.75); }
}
.ai-preview__actions { display: flex; gap: 6px; }
.ai-preview__actions button {
  padding: 4px 12px; font-size: 12px; font-family: var(--font-ui);
  border-radius: var(--radius-md); cursor: pointer; font-weight: 500;
  transition: all var(--duration-fast) var(--ease-out);
}
.keep {
  background: #6366f1; color: white; border: 1.5px solid #6366f1;
}
.keep:hover:not(:disabled) { background: #4f46e5; border-color: #4f46e5; }
.keep:disabled { opacity: 0.45; cursor: default; }
.discard {
  background: transparent; color: var(--color-gray-500);
  border: 1.5px solid var(--color-gray-200);
}
.discard:hover { border-color: var(--color-gray-400); color: var(--color-gray-700); }

.ai-preview__body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 48px;
}
.ai-preview__placeholder {
  color: var(--color-gray-400); font-size: 13px;
}

/* Markdown content inside preview */
.markdown-body { font-family: var(--font-ui); font-size: 14px; line-height: 1.7; color: var(--color-gray-800); }
.markdown-body :deep(h1) { font-size: 22px; font-weight: 700; margin: 16px 0 8px; }
.markdown-body :deep(h2) { font-size: 17px; font-weight: 600; margin: 14px 0 6px; }
.markdown-body :deep(h3) { font-size: 14px; font-weight: 600; margin: 12px 0 4px; }
.markdown-body :deep(p) { margin: 0 0 8px; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 20px; margin: 6px 0; }
.markdown-body :deep(li) { margin: 2px 0; }
.markdown-body :deep(code) {
  background: var(--color-gray-100); padding: 1px 5px;
  border-radius: 3px; font-family: var(--font-mono); font-size: 12px;
}
.markdown-body :deep(pre) {
  background: var(--color-gray-50); border: 1px solid var(--color-gray-200);
  padding: 12px 16px; border-radius: var(--radius-md); overflow-x: auto; margin: 8px 0;
  font-family: var(--font-mono); font-size: 12px; line-height: 1.6;
}
.markdown-body :deep(pre code) { background: transparent; padding: 0; }
.markdown-body :deep(blockquote) {
  border-left: 3px solid var(--color-gray-300); padding-left: 12px;
  color: var(--color-gray-500); margin: 8px 0;
}
.markdown-body :deep(hr) { border: none; border-top: var(--border); margin: 12px 0; }
.markdown-body :deep(strong) { font-weight: 600; }
</style>
