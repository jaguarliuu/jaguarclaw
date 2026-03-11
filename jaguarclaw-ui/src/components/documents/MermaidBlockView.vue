<script setup lang="ts">
import { NodeViewWrapper, NodeViewContent } from '@tiptap/vue-3'
import { ref, computed, watch, onMounted } from 'vue'
import mermaid from 'mermaid'

const props = defineProps<{
  node: any
  updateAttributes: (attrs: Record<string, any>) => void
  editor: any
}>()

const isMermaid = computed(() => props.node.attrs.language === 'mermaid')
const svgContent = ref('')
const renderError = ref('')

let renderTimer: ReturnType<typeof setTimeout> | null = null
let counter = 0

mermaid.initialize({ startOnLoad: false, theme: 'neutral', securityLevel: 'loose' })

async function renderMermaid() {
  const code = props.node.textContent?.trim()
  if (!code) { svgContent.value = ''; renderError.value = ''; return }
  try {
    const id = `mmd-${Date.now()}-${++counter}`
    const { svg } = await mermaid.render(id, code)
    svgContent.value = svg
    renderError.value = ''
  } catch (e: any) {
    renderError.value = e?.message?.split('\n')[0] || String(e)
    svgContent.value = ''
  }
}

onMounted(() => { if (isMermaid.value) renderMermaid() })

watch(() => props.node.textContent, () => {
  if (!isMermaid.value) return
  if (renderTimer) clearTimeout(renderTimer)
  renderTimer = setTimeout(renderMermaid, 600)
})
</script>

<template>
  <NodeViewWrapper class="code-block-wrapper" :data-language="node.attrs.language">
    <template v-if="isMermaid">
      <div class="mermaid-label">mermaid</div>
      <div v-if="svgContent" class="mermaid-preview" v-html="svgContent" />
      <div v-else-if="renderError" class="mermaid-error">⚠ {{ renderError }}</div>
      <div v-else class="mermaid-loading">渲染中…</div>
      <pre class="mermaid-source"><NodeViewContent as="code" /></pre>
    </template>
    <pre v-else><NodeViewContent as="code" /></pre>
  </NodeViewWrapper>
</template>

<style scoped>
.code-block-wrapper { margin: 12px 0; }

/* Mermaid */
.mermaid-label {
  font-size: 11px; color: var(--color-gray-400); font-family: var(--font-mono);
  margin-bottom: 4px;
}
.mermaid-preview {
  background: var(--color-gray-50); border: 1px solid #c7d2fe;
  border-radius: var(--radius-md); padding: 16px; overflow-x: auto;
  display: flex; justify-content: center;
}
.mermaid-preview :deep(svg) { max-width: 100%; height: auto; }
.mermaid-error {
  background: #fff5f5; border: 1px solid #fed7d7; color: #c53030;
  border-radius: var(--radius-md); padding: 8px 12px; font-size: 12px;
  font-family: var(--font-mono);
}
.mermaid-loading {
  color: var(--color-gray-400); font-size: 12px; padding: 4px 0;
}
.mermaid-source {
  background: var(--color-gray-50); color: var(--color-gray-800);
  border: 1px solid var(--color-gray-200);
  padding: 12px 16px; border-radius: var(--radius-md); overflow-x: auto;
  font-family: var(--font-mono); font-size: 13px; line-height: 1.6;
  margin-top: 6px;
}
.mermaid-source :deep(code) { background: transparent; color: inherit; padding: 0; }

/* Normal code block */
pre {
  background: var(--color-gray-50); color: var(--color-gray-800);
  border: 1px solid var(--color-gray-200);
  padding: 16px; border-radius: var(--radius-md); overflow-x: auto;
  font-family: var(--font-mono); font-size: 13px; line-height: 1.6;
}
pre :deep(code) { background: transparent; color: inherit; padding: 0; }
</style>
