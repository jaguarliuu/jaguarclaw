<script setup lang="ts">
import { NodeViewWrapper, NodeViewContent } from '@tiptap/vue-3'
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import mermaid from 'mermaid'

const props = defineProps<{
  node: any
  updateAttributes: (attrs: Record<string, any>) => void
  editor: any
}>()

const isMermaid = computed(() => props.node.attrs.language === 'mermaid')
const svgContent = ref('')
const renderError = ref('')
const showCode = ref(false)   // false = diagram view, true = code view

// ── Zoom / pan ──────────────────────────────────────────────────────────────
const scale = ref(1)
const tx = ref(0)
const ty = ref(0)
const dragging = ref(false)
let dragStart = { x: 0, y: 0, tx: 0, ty: 0 }
const previewRef = ref<HTMLDivElement | null>(null)

function onWheel(e: WheelEvent) {
  e.preventDefault()
  const factor = e.deltaY < 0 ? 1.1 : 0.9
  scale.value = Math.min(5, Math.max(0.2, scale.value * factor))
}

function onMouseDown(e: MouseEvent) {
  if (e.button !== 0) return
  dragging.value = true
  dragStart = { x: e.clientX, y: e.clientY, tx: tx.value, ty: ty.value }
  e.preventDefault()
}

function onMouseMove(e: MouseEvent) {
  if (!dragging.value) return
  tx.value = dragStart.tx + e.clientX - dragStart.x
  ty.value = dragStart.ty + e.clientY - dragStart.y
}

function onMouseUp() { dragging.value = false }

function resetZoom() { scale.value = 1; tx.value = 0; ty.value = 0 }

onMounted(() => {
  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', onMouseUp)
})
onBeforeUnmount(() => {
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
})

// ── Mermaid render ───────────────────────────────────────────────────────────
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

    <!-- Mermaid block -->
    <template v-if="isMermaid">
      <div class="mermaid-header">
        <span class="mermaid-lang">mermaid</span>
        <div class="mermaid-controls">
          <!-- Zoom controls — only in diagram view -->
          <template v-if="!showCode && svgContent">
            <span class="mermaid-zoom-label">{{ Math.round(scale * 100) }}%</span>
            <button class="mermaid-btn" title="缩小" @click="scale = Math.max(0.2, scale - 0.1)">−</button>
            <button class="mermaid-btn" title="放大" @click="scale = Math.min(5, scale + 0.1)">+</button>
            <button class="mermaid-btn" title="重置" @click="resetZoom">⟳</button>
            <div class="mermaid-sep" />
          </template>
          <!-- View toggle -->
          <button
            class="mermaid-btn mermaid-toggle"
            :title="showCode ? '查看图表' : '编辑代码'"
            @click="showCode = !showCode"
          >
            <template v-if="showCode">
              <!-- diagram icon -->
              <svg width="13" height="13" viewBox="0 0 13 13" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round">
                <rect x="1" y="1" width="4" height="3" rx="0.7"/>
                <rect x="8" y="1" width="4" height="3" rx="0.7"/>
                <rect x="4" y="9" width="5" height="3" rx="0.7"/>
                <path d="M3 4v2.5h7V4M6.5 6.5V9"/>
              </svg>
            </template>
            <template v-else>
              <!-- code icon -->
              <svg width="13" height="13" viewBox="0 0 13 13" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round">
                <path d="M4 4L1.5 6.5 4 9M9 4l2.5 2.5L9 9M7 2.5l-1.5 8"/>
              </svg>
            </template>
          </button>
        </div>
      </div>

      <!-- Diagram view -->
      <div
        v-if="!showCode"
        ref="previewRef"
        class="mermaid-preview"
        :class="{ dragging }"
        @wheel.passive="false"
        @wheel="onWheel"
        @mousedown="onMouseDown"
        @dblclick="resetZoom"
      >
        <div
          v-if="svgContent"
          class="mermaid-inner"
          :style="{ transform: `translate(${tx}px, ${ty}px) scale(${scale})` }"
          v-html="svgContent"
        />
        <div v-else-if="renderError" class="mermaid-error">⚠ {{ renderError }}</div>
        <div v-else class="mermaid-loading">渲染中…</div>
      </div>

      <!-- Code view -->
      <pre v-show="showCode" class="mermaid-source"><NodeViewContent as="code" /></pre>
    </template>

    <!-- Normal code block -->
    <pre v-else><NodeViewContent as="code" /></pre>

  </NodeViewWrapper>
</template>

<style scoped>
.code-block-wrapper { margin: 12px 0; }

/* Header row */
.mermaid-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}
.mermaid-lang {
  font-size: 11px;
  color: var(--color-gray-400);
  font-family: var(--font-mono);
}
.mermaid-controls {
  display: flex;
  align-items: center;
  gap: 2px;
}
.mermaid-zoom-label {
  font-size: 11px;
  color: var(--color-gray-400);
  font-family: var(--font-mono);
  min-width: 34px;
  text-align: right;
  margin-right: 2px;
}
.mermaid-sep {
  width: 1px;
  height: 12px;
  background: var(--color-gray-200);
  margin: 0 4px;
}
.mermaid-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--color-gray-500);
  cursor: pointer;
  font-size: 13px;
  line-height: 1;
  transition: background 80ms;
}
.mermaid-btn:hover { background: var(--color-gray-100); color: var(--color-gray-800); }
.mermaid-toggle { color: var(--color-gray-400); }

/* Diagram container */
.mermaid-preview {
  background: var(--color-gray-50);
  border: 1px solid var(--color-gray-200);
  border-radius: var(--radius-md);
  overflow: hidden;
  position: relative;
  min-height: 120px;
  cursor: grab;
  display: flex;
  align-items: center;
  justify-content: center;
  user-select: none;
}
.mermaid-preview.dragging { cursor: grabbing; }

.mermaid-inner {
  transform-origin: center center;
  transition: transform 0.05s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
}
.mermaid-inner :deep(svg) { max-width: 100%; height: auto; display: block; }

.mermaid-error {
  background: #fff5f5;
  border: 1px solid #fed7d7;
  color: #c53030;
  border-radius: var(--radius-md);
  padding: 8px 12px;
  font-size: 12px;
  font-family: var(--font-mono);
}
.mermaid-loading {
  color: var(--color-gray-400);
  font-size: 12px;
  padding: 4px 0;
}

/* Code view */
.mermaid-source {
  background: var(--color-gray-50);
  color: var(--color-gray-800);
  border: 1px solid var(--color-gray-200);
  padding: 12px 16px;
  border-radius: var(--radius-md);
  overflow-x: auto;
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.6;
  margin: 0;
}
.mermaid-source :deep(code) { background: transparent; color: inherit; padding: 0; }

/* Normal code block */
pre {
  background: var(--color-gray-50);
  color: var(--color-gray-800);
  border: 1px solid var(--color-gray-200);
  padding: 16px;
  border-radius: var(--radius-md);
  overflow-x: auto;
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.6;
  margin: 0;
}
pre :deep(code) { background: transparent; color: inherit; padding: 0; }
</style>
