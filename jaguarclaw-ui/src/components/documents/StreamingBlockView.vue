<script setup lang="ts">
import { NodeViewWrapper } from '@tiptap/vue-3'
import { computed } from 'vue'
import { marked } from 'marked'

const props = defineProps<{ node: any }>()

const rendered = computed(() => {
  const content = props.node.attrs.content as string
  if (!content) return ''
  return marked.parse(content) as string
})
</script>

<template>
  <NodeViewWrapper class="streaming-block" contenteditable="false">
    <span class="streaming-block__sparkle">✦</span>
    <div class="streaming-block__content markdown-body" v-html="rendered" />
    <span class="streaming-cursor">▋</span>
  </NodeViewWrapper>
</template>

<style scoped>
.streaming-block {
  position: relative;
  padding: 12px 16px 12px 20px;
  margin: 8px 0;
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
  background: linear-gradient(to right, #fafafe, #ffffff);
  cursor: default;
  user-select: none;
  overflow: hidden;
}

/* Gradient left accent */
.streaming-block::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  border-radius: 2px 0 0 2px;
}

/* AI sparkle badge */
.streaming-block__sparkle {
  position: absolute;
  top: 8px;
  right: 10px;
  font-size: 10px;
  color: #a5b4fc;
  line-height: 1;
  user-select: none;
  pointer-events: none;
}

.streaming-cursor {
  display: inline-block;
  color: #818cf8;
  animation: blink 0.9s step-end infinite;
  font-size: 14px;
  margin-left: 1px;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* Markdown content */
.markdown-body {
  font-family: var(--font-ui);
  font-size: 15px;
  line-height: 1.75;
  color: var(--color-gray-900);
}
.markdown-body :deep(h1) { font-size: 26px; font-weight: 700; margin: 16px 0 8px; }
.markdown-body :deep(h2) { font-size: 20px; font-weight: 600; margin: 14px 0 8px; }
.markdown-body :deep(h3) { font-size: 16px; font-weight: 600; margin: 12px 0 6px; }
.markdown-body :deep(p) { margin: 0 0 8px; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 20px; margin: 6px 0; }
.markdown-body :deep(li) { margin: 2px 0; }
.markdown-body :deep(code) {
  background: var(--color-gray-100);
  padding: 1px 5px;
  border-radius: 3px;
  font-family: var(--font-mono);
  font-size: 13px;
}
.markdown-body :deep(pre) {
  background: var(--color-gray-50);
  border: 1px solid var(--color-gray-200);
  padding: 12px 16px;
  border-radius: var(--radius-md);
  overflow-x: auto;
  margin: 8px 0;
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.6;
}
.markdown-body :deep(pre code) { background: transparent; padding: 0; }
.markdown-body :deep(blockquote) {
  border-left: 3px solid var(--color-gray-300);
  padding-left: 12px;
  color: var(--color-gray-500);
  margin: 8px 0;
}
.markdown-body :deep(strong) { font-weight: 600; }
.markdown-body :deep(hr) { border: none; border-top: var(--border); margin: 12px 0; }
</style>
