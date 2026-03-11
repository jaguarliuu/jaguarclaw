<script setup lang="ts">
import { NodeViewWrapper, NodeViewContent } from '@tiptap/vue-3'
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import mermaid from 'mermaid'

const props = defineProps<{
  node: any
  updateAttributes: (attrs: Record<string, any>) => void
  editor: any
}>()

const isMermaid = computed(() => props.node.attrs.language === 'mermaid')
const svgContent = ref('')
const renderError = ref('')
const showCode = ref(false)

// ── Shared zoom/pan state factory ────────────────────────────────────────────
function usePan() {
  const scale = ref(1)
  const tx = ref(0)
  const ty = ref(0)
  const dragging = ref(false)
  let ds = { x: 0, y: 0, tx: 0, ty: 0 }

  function wheel(e: WheelEvent) {
    e.preventDefault()
    const f = e.deltaY < 0 ? 1.12 : 0.9
    scale.value = Math.min(20, Math.max(0.05, scale.value * f))
  }
  function mousedown(e: MouseEvent) {
    if (e.button !== 0) return
    dragging.value = true
    ds = { x: e.clientX, y: e.clientY, tx: tx.value, ty: ty.value }
    e.preventDefault()
  }
  function mousemove(e: MouseEvent) {
    if (!dragging.value) return
    tx.value = ds.tx + e.clientX - ds.x
    ty.value = ds.ty + e.clientY - ds.y
  }
  function mouseup() { dragging.value = false }
  function reset(s = 1) { scale.value = s; tx.value = 0; ty.value = 0 }

  return { scale, tx, ty, dragging, wheel, mousedown, mousemove, mouseup, reset }
}

// Inline pan state
const inline = usePan()
// Fullscreen pan state
const full = usePan()

// ── Global mouse handlers ────────────────────────────────────────────────────
function onGlobalMouseMove(e: MouseEvent) {
  inline.mousemove(e)
  full.mousemove(e)
}
function onGlobalMouseUp() {
  inline.mouseup()
  full.mouseup()
}
onMounted(() => {
  window.addEventListener('mousemove', onGlobalMouseMove)
  window.addEventListener('mouseup', onGlobalMouseUp)
})
onBeforeUnmount(() => {
  window.removeEventListener('mousemove', onGlobalMouseMove)
  window.removeEventListener('mouseup', onGlobalMouseUp)
})

// ── Inline wheel (non-passive) ────────────────────────────────────────────────
const inlineRef = ref<HTMLDivElement | null>(null)
const fullRef = ref<HTMLDivElement | null>(null)

watch(inlineRef, (el) => {
  if (el) el.addEventListener('wheel', inline.wheel, { passive: false })
})
watch(fullRef, (el) => {
  if (el) el.addEventListener('wheel', full.wheel, { passive: false })
})

// ── SVG natural size (for auto-fit) ──────────────────────────────────────────
const svgW = ref(800)
const svgH = ref(400)

function parseSvgSize(svg: string) {
  const vb = svg.match(/viewBox="([^"]+)"/)
  if (vb?.[1]) {
    const parts = vb[1].split(/\s+/).map(Number)
    const w = parts[2], h = parts[3]
    if (w && h) { svgW.value = w; svgH.value = h; return }
  }
  const wm = svg.match(/\swidth="([^"]+)"/)
  const hm = svg.match(/\sheight="([^"]+)"/)
  if (wm?.[1] && hm?.[1]) {
    svgW.value = parseFloat(wm[1]) || 800
    svgH.value = parseFloat(hm[1]) || 400
  }
}

// Fit scale for inline preview (container 280px tall, editor width ~700px)
const INLINE_H = 280
const INLINE_W = 700

function inlineFitScale() {
  return Math.min(1, Math.min(INLINE_W / svgW.value, INLINE_H / svgH.value) * 0.9)
}

// ── Mermaid render ────────────────────────────────────────────────────────────
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
    parseSvgSize(svg)
    await nextTick()
    inline.reset(inlineFitScale())
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

// ── Fullscreen modal ──────────────────────────────────────────────────────────
const fullscreen = ref(false)

function openFullscreen() {
  const fitScale = Math.min(
    (window.innerWidth * 0.9) / svgW.value,
    (window.innerHeight * 0.85) / svgH.value,
    1
  )
  full.reset(fitScale)
  fullscreen.value = true
}

function closeFullscreen() { fullscreen.value = false }

function fullFitScale() {
  return Math.min((window.innerWidth * 0.9) / svgW.value, (window.innerHeight * 0.85) / svgH.value, 1)
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && fullscreen.value) closeFullscreen()
}
onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <NodeViewWrapper class="mmd-wrapper" :data-language="node.attrs.language">

    <!-- ── Mermaid block ───────────────────────────────────────────────────── -->
    <template v-if="isMermaid">

      <!-- Header -->
      <div class="mmd-header">
        <span class="mmd-lang">mermaid</span>
        <div class="mmd-controls">
          <template v-if="!showCode && svgContent">
            <span class="mmd-zoom-pct">{{ Math.round(inline.scale.value * 100) }}%</span>
            <button class="mmd-btn" title="缩小" @click="inline.scale.value = Math.max(0.05, inline.scale.value - 0.1)">−</button>
            <button class="mmd-btn" title="放大" @click="inline.scale.value = Math.min(20, inline.scale.value + 0.1)">+</button>
            <button class="mmd-btn" title="适配" @click="inline.reset(inlineFitScale())">⊡</button>
            <button class="mmd-btn mmd-expand-btn" title="全屏展开" @click="openFullscreen">
              <svg width="13" height="13" viewBox="0 0 13 13" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round">
                <path d="M1 5V1h4M8 1h4v4M12 8v4H8M5 12H1V8"/>
              </svg>
            </button>
            <div class="mmd-sep" />
          </template>
          <!-- Code / diagram toggle -->
          <button class="mmd-btn" :title="showCode ? '查看图表' : '编辑代码'" @click="showCode = !showCode">
            <template v-if="showCode">
              <svg width="13" height="13" viewBox="0 0 13 13" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round">
                <rect x="1" y="1" width="4" height="3" rx="0.7"/><rect x="8" y="1" width="4" height="3" rx="0.7"/>
                <rect x="4" y="9" width="5" height="3" rx="0.7"/><path d="M3 4v2.5h7V4M6.5 6.5V9"/>
              </svg>
            </template>
            <template v-else>
              <svg width="13" height="13" viewBox="0 0 13 13" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round">
                <path d="M4 4L1.5 6.5 4 9M9 4l2.5 2.5L9 9M7 2.5l-1.5 8"/>
              </svg>
            </template>
          </button>
        </div>
      </div>

      <!-- Inline diagram -->
      <div
        v-if="!showCode"
        ref="inlineRef"
        class="mmd-canvas"
        :class="{ dragging: inline.dragging.value }"
        @mousedown="inline.mousedown"
        @dblclick="inline.reset(inlineFitScale())"
      >
        <div
          v-if="svgContent"
          class="mmd-inner"
          :style="{ transform: `translate(${inline.tx.value}px, ${inline.ty.value}px) scale(${inline.scale.value})` }"
          v-html="svgContent"
        />
        <div v-else-if="renderError" class="mmd-error">⚠ {{ renderError }}</div>
        <div v-else class="mmd-loading">渲染中…</div>
      </div>

      <!-- Code view (always in DOM for NodeViewContent) -->
      <pre v-show="showCode" class="mmd-source"><NodeViewContent as="code" /></pre>

    </template>

    <!-- Normal code block -->
    <pre v-else><NodeViewContent as="code" /></pre>

  </NodeViewWrapper>

  <!-- ── Fullscreen modal ──────────────────────────────────────────────────── -->
  <Teleport to="body">
    <div v-if="fullscreen" class="mmd-modal-backdrop" @click.self="closeFullscreen">
      <div class="mmd-modal">
        <!-- Modal toolbar -->
        <div class="mmd-modal-toolbar">
          <span class="mmd-modal-title">mermaid</span>
          <div class="mmd-controls">
            <span class="mmd-zoom-pct">{{ Math.round(full.scale.value * 100) }}%</span>
            <button class="mmd-btn" title="缩小" @click="full.scale.value = Math.max(0.05, full.scale.value - 0.15)">−</button>
            <button class="mmd-btn" title="放大" @click="full.scale.value = Math.min(20, full.scale.value + 0.15)">+</button>
            <button class="mmd-btn" title="适配画布" @click="full.reset(fullFitScale())">⊡</button>
            <button class="mmd-btn" title="1:1" @click="full.reset(1)">1:1</button>
            <div class="mmd-sep" />
            <button class="mmd-btn mmd-close-btn" title="关闭 (Esc)" @click="closeFullscreen">
              <svg width="13" height="13" viewBox="0 0 13 13" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round">
                <path d="M2 2l9 9M11 2l-9 9"/>
              </svg>
            </button>
          </div>
        </div>

        <!-- Infinite canvas -->
        <div
          ref="fullRef"
          class="mmd-fullcanvas"
          :class="{ dragging: full.dragging.value }"
          @mousedown="full.mousedown"
          @dblclick="full.reset(fullFitScale())"
        >
          <div
            class="mmd-inner"
            :style="{ transform: `translate(${full.tx.value}px, ${full.ty.value}px) scale(${full.scale.value})` }"
            v-html="svgContent"
          />
          <div class="mmd-fullcanvas-hint">滚轮缩放 · 拖拽平移 · 双击适配 · Esc 关闭</div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.mmd-wrapper { margin: 12px 0; }

/* ── Header ── */
.mmd-header {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 4px;
}
.mmd-lang {
  font-size: 11px; color: var(--color-gray-400); font-family: var(--font-mono);
}
.mmd-controls { display: flex; align-items: center; gap: 2px; }
.mmd-zoom-pct {
  font-size: 11px; color: var(--color-gray-400); font-family: var(--font-mono);
  min-width: 36px; text-align: right; margin-right: 2px;
}
.mmd-sep { width: 1px; height: 12px; background: var(--color-gray-200); margin: 0 4px; }
.mmd-btn {
  display: inline-flex; align-items: center; justify-content: center;
  width: 22px; height: 22px; border: none; border-radius: 4px;
  background: transparent; color: var(--color-gray-500);
  cursor: pointer; font-size: 13px; line-height: 1;
  transition: background 80ms;
}
.mmd-btn:hover { background: var(--color-gray-100); color: var(--color-gray-800); }
.mmd-expand-btn { color: var(--color-gray-400); }
.mmd-close-btn:hover { background: #fee2e2; color: #dc2626; }

/* ── Inline canvas ── */
.mmd-canvas {
  background: var(--color-gray-50);
  border: 1px solid var(--color-gray-200);
  border-radius: var(--radius-md);
  overflow: hidden;
  height: 280px;
  cursor: grab;
  display: flex; align-items: center; justify-content: center;
  user-select: none; position: relative;
}
.mmd-canvas.dragging { cursor: grabbing; }

.mmd-inner {
  transform-origin: center center;
  will-change: transform;
  display: inline-flex; align-items: center; justify-content: center;
}
.mmd-inner :deep(svg) { display: block; }

.mmd-error {
  background: #fff5f5; border: 1px solid #fed7d7; color: #c53030;
  border-radius: var(--radius-md); padding: 8px 12px;
  font-size: 12px; font-family: var(--font-mono);
}
.mmd-loading { color: var(--color-gray-400); font-size: 12px; }

/* ── Code source ── */
.mmd-source {
  background: var(--color-gray-50); color: var(--color-gray-800);
  border: 1px solid var(--color-gray-200);
  padding: 12px 16px; border-radius: var(--radius-md); overflow-x: auto;
  font-family: var(--font-mono); font-size: 13px; line-height: 1.6; margin: 0;
}
.mmd-source :deep(code) { background: transparent; color: inherit; padding: 0; }

/* ── Normal code block ── */
pre {
  background: var(--color-gray-50); color: var(--color-gray-800);
  border: 1px solid var(--color-gray-200);
  padding: 16px; border-radius: var(--radius-md); overflow-x: auto;
  font-family: var(--font-mono); font-size: 13px; line-height: 1.6; margin: 0;
}
pre :deep(code) { background: transparent; color: inherit; padding: 0; }

/* ── Fullscreen modal ── */
.mmd-modal-backdrop {
  position: fixed; inset: 0; z-index: 1000;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
  display: flex; align-items: center; justify-content: center;
}
.mmd-modal {
  background: #fff;
  border-radius: 10px;
  width: 92vw; height: 88vh;
  display: flex; flex-direction: column;
  overflow: hidden;
  box-shadow: 0 24px 80px rgba(0,0,0,0.3);
}
.mmd-modal-toolbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid var(--color-gray-100);
  flex-shrink: 0;
  background: #fafafa;
}
.mmd-modal-title {
  font-size: 12px; font-family: var(--font-mono); color: var(--color-gray-500);
}
.mmd-fullcanvas {
  flex: 1; overflow: hidden; position: relative;
  cursor: grab; user-select: none;
  background:
    radial-gradient(circle, var(--color-gray-300) 1px, transparent 1px);
  background-size: 24px 24px;
  background-color: var(--color-gray-50);
  display: flex; align-items: center; justify-content: center;
}
.mmd-fullcanvas.dragging { cursor: grabbing; }
.mmd-fullcanvas-hint {
  position: absolute; bottom: 12px; left: 50%; transform: translateX(-50%);
  font-size: 11px; color: var(--color-gray-400);
  background: rgba(255,255,255,0.8); padding: 3px 10px;
  border-radius: 100px; pointer-events: none; white-space: nowrap;
}
</style>
