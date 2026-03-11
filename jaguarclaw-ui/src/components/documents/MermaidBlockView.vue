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
const rawSvg = ref('')       // mermaid output, unmodified
const renderError = ref('')
const showCode = ref(false)
const fullscreen = ref(false)

// ── Natural viewBox parsed from mermaid SVG ──────────────────────────────────
interface Vb { x: number; y: number; w: number; h: number }
const natVb = ref<Vb>({ x: 0, y: 0, w: 800, h: 400 })

// ── Two independent viewport states (inline / fullscreen) ────────────────────
const inVb  = ref<Vb>({ x: 0, y: 0, w: 800, h: 400 })
const fullVb = ref<Vb>({ x: 0, y: 0, w: 800, h: 400 })

/** Replace SVG width/height/viewBox so browser renders at container size */
function buildSvg(vb: Vb): string {
  return rawSvg.value
    .replace(/\bwidth="[^"]*"/, 'width="100%"')
    .replace(/\bheight="[^"]*"/, 'height="100%"')
    .replace(/viewBox="[^"]*"/, `viewBox="${vb.x} ${vb.y} ${vb.w} ${vb.h}"`)
}

const inlineSvg  = computed(() => rawSvg.value ? buildSvg(inVb.value)   : '')
const fullSvg    = computed(() => rawSvg.value ? buildSvg(fullVb.value) : '')

// Current zoom as a percentage (natural viewBox width / current width)
const inlineZoomPct  = computed(() => Math.round((natVb.value.w / inVb.value.w) * 100))
const fullZoomPct    = computed(() => Math.round((natVb.value.w / fullVb.value.w) * 100))

// ── Viewbox zoom (scroll-centred) ────────────────────────────────────────────
const inlineRef  = ref<HTMLDivElement | null>(null)
const fullRef    = ref<HTMLDivElement | null>(null)

function zoomVb(vb: Vb, factor: number, cx: number, cy: number, containerW: number, containerH: number): Vb {
  const newW = Math.min(natVb.value.w * 10, Math.max(natVb.value.w * 0.05, vb.w * factor))
  const newH = newW * (vb.h / vb.w)   // keep aspect ratio
  const svgCx = vb.x + (cx / containerW) * vb.w
  const svgCy = vb.y + (cy / containerH) * vb.h
  return { x: svgCx - (cx / containerW) * newW, y: svgCy - (cy / containerH) * newH, w: newW, h: newH }
}

function onInlineWheel(e: WheelEvent) {
  e.preventDefault()
  const el = inlineRef.value!
  const r = el.getBoundingClientRect()
  inVb.value = zoomVb(inVb.value, e.deltaY < 0 ? 0.88 : 1.12,
    e.clientX - r.left, e.clientY - r.top, r.width, r.height)
}

function onFullWheel(e: WheelEvent) {
  e.preventDefault()
  const el = fullRef.value!
  const r = el.getBoundingClientRect()
  fullVb.value = zoomVb(fullVb.value, e.deltaY < 0 ? 0.88 : 1.12,
    e.clientX - r.left, e.clientY - r.top, r.width, r.height)
}

watch(inlineRef,  el => { if (el) el.addEventListener('wheel', onInlineWheel, { passive: false }) })
watch(fullRef,    el => { if (el) el.addEventListener('wheel', onFullWheel,   { passive: false }) })

// ── Pan (drag) ────────────────────────────────────────────────────────────────
const inDragging   = ref(false)
const fullDragging = ref(false)
let panState = { x: 0, y: 0, vbX: 0, vbY: 0, target: 'inline' as 'inline' | 'full' }

function startPan(e: MouseEvent, target: 'inline' | 'full') {
  if (e.button !== 0) return
  e.preventDefault()
  const vb = target === 'inline' ? inVb.value : fullVb.value
  panState = { x: e.clientX, y: e.clientY, vbX: vb.x, vbY: vb.y, target }
  if (target === 'inline') inDragging.value = true
  else fullDragging.value = true
}

function onGlobalMouseMove(e: MouseEvent) {
  if (!inDragging.value && !fullDragging.value) return
  const el = panState.target === 'inline' ? inlineRef.value : fullRef.value
  if (!el) return
  const r = el.getBoundingClientRect()
  const vb = panState.target === 'inline' ? inVb.value : fullVb.value
  const dx = (e.clientX - panState.x) / r.width  * vb.w
  const dy = (e.clientY - panState.y) / r.height * vb.h
  const updated = { ...vb, x: panState.vbX - dx, y: panState.vbY - dy }
  if (panState.target === 'inline') inVb.value = updated
  else fullVb.value = updated
}

function onGlobalMouseUp() { inDragging.value = false; fullDragging.value = false }

onMounted(() => {
  window.addEventListener('mousemove', onGlobalMouseMove)
  window.addEventListener('mouseup', onGlobalMouseUp)
})
onBeforeUnmount(() => {
  window.removeEventListener('mousemove', onGlobalMouseMove)
  window.removeEventListener('mouseup', onGlobalMouseUp)
})

// ── Fit helpers ───────────────────────────────────────────────────────────────
function fitInline() { inVb.value = { ...natVb.value } }

function stepZoomInline(factor: number) { stepZoom(inVb, factor) }
function stepZoomFull(factor: number)   { stepZoom(fullVb, factor) }

function fitFull() {
  const el = fullRef.value
  if (!el) { fullVb.value = { ...natVb.value }; return }
  const r = el.getBoundingClientRect()
  const scaleW = r.width  / natVb.value.w
  const scaleH = r.height / natVb.value.h
  const fit = Math.min(scaleW, scaleH) * 0.9
  const newW = natVb.value.w / fit
  const newH = natVb.value.h / fit
  fullVb.value = {
    x: natVb.value.x - (newW - natVb.value.w) / 2,
    y: natVb.value.y - (newH - natVb.value.h) / 2,
    w: newW, h: newH,
  }
}

function stepZoom(vbRef: typeof inVb, factor: number) {
  const vb = vbRef.value
  const newW = Math.min(natVb.value.w * 10, Math.max(natVb.value.w * 0.05, vb.w * factor))
  const newH = newW * (vb.h / vb.w)
  const cx = vb.x + vb.w / 2
  const cy = vb.y + vb.h / 2
  vbRef.value = { x: cx - newW / 2, y: cy - newH / 2, w: newW, h: newH }
}

// ── Mermaid render ────────────────────────────────────────────────────────────
let renderTimer: ReturnType<typeof setTimeout> | null = null
let counter = 0
mermaid.initialize({ startOnLoad: false, theme: 'neutral', securityLevel: 'loose' })

async function renderMermaid() {
  const code = props.node.textContent?.trim()
  if (!code) { rawSvg.value = ''; renderError.value = ''; return }
  try {
    const id = `mmd-${Date.now()}-${++counter}`
    const { svg } = await mermaid.render(id, code)
    rawSvg.value = svg
    renderError.value = ''

    // Parse natural viewBox
    const vbMatch = svg.match(/viewBox="([^"]+)"/)
    if (vbMatch?.[1]) {
      const parts = vbMatch[1].split(/\s+/).map(Number)
      natVb.value = { x: parts[0] ?? 0, y: parts[1] ?? 0, w: parts[2] ?? 800, h: parts[3] ?? 400 }
    } else {
      const wm = svg.match(/\bwidth="([\d.]+)"/)
      const hm = svg.match(/\bheight="([\d.]+)"/)
      natVb.value = { x: 0, y: 0, w: parseFloat(wm?.[1] ?? '800'), h: parseFloat(hm?.[1] ?? '400') }
    }

    await nextTick()
    fitInline()
  } catch (e: any) {
    renderError.value = e?.message?.split('\n')[0] || String(e)
    rawSvg.value = ''
  }
}

onMounted(() => { if (isMermaid.value) renderMermaid() })
watch(() => props.node.textContent, () => {
  if (!isMermaid.value) return
  if (renderTimer) clearTimeout(renderTimer)
  renderTimer = setTimeout(renderMermaid, 600)
})

// ── Fullscreen ────────────────────────────────────────────────────────────────
function openFullscreen() {
  fullscreen.value = true
  nextTick(() => fitFull())
}
function closeFullscreen() { fullscreen.value = false }

function onKeydown(e: KeyboardEvent) { if (e.key === 'Escape') closeFullscreen() }
onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <NodeViewWrapper class="mmd-wrap" :data-language="node.attrs.language">

    <template v-if="isMermaid">
      <!-- Header -->
      <div class="mmd-header">
        <span class="mmd-lang">mermaid</span>
        <div class="mmd-controls">
          <template v-if="!showCode && rawSvg">
            <span class="mmd-pct">{{ inlineZoomPct }}%</span>
            <button class="mmd-btn" title="缩小" @click="stepZoomInline(1.25)">−</button>
            <button class="mmd-btn" title="放大" @click="stepZoomInline(0.8)">+</button>
            <button class="mmd-btn" title="适配" @click="fitInline">⊡</button>
            <button class="mmd-btn mmd-expand" title="全屏展开" @click="openFullscreen">
              <svg width="13" height="13" viewBox="0 0 13 13" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round">
                <path d="M1 5V1h4M8 1h4v4M12 8v4H8M5 12H1V8"/>
              </svg>
            </button>
            <div class="mmd-sep"/>
          </template>
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
      <div v-if="!showCode"
        ref="inlineRef"
        class="mmd-canvas"
        :class="{ dragging: inDragging }"
        @mousedown="startPan($event, 'inline')"
        @dblclick="fitInline"
      >
        <div v-if="inlineSvg" class="mmd-svg-wrap" v-html="inlineSvg" />
        <div v-else-if="renderError" class="mmd-error">⚠ {{ renderError }}</div>
        <div v-else class="mmd-loading">渲染中…</div>
      </div>

      <!-- Code view (always in DOM for NodeViewContent) -->
      <pre v-show="showCode" class="mmd-src"><NodeViewContent as="code" /></pre>
    </template>

    <!-- Normal code block -->
    <pre v-else><NodeViewContent as="code" /></pre>

  </NodeViewWrapper>

  <!-- Fullscreen modal -->
  <Teleport to="body">
    <div v-if="fullscreen" class="mmd-backdrop" @click.self="closeFullscreen">
      <div class="mmd-modal">
        <div class="mmd-modal-bar">
          <span class="mmd-lang">mermaid</span>
          <div class="mmd-controls">
            <span class="mmd-pct">{{ fullZoomPct }}%</span>
            <button class="mmd-btn" title="缩小" @click="stepZoomFull(1.25)">−</button>
            <button class="mmd-btn" title="放大" @click="stepZoomFull(0.8)">+</button>
            <button class="mmd-btn" title="适配" @click="fitFull">⊡</button>
            <button class="mmd-btn" title="1:1" @click="fullVb = { ...natVb }">1:1</button>
            <div class="mmd-sep"/>
            <button class="mmd-btn mmd-close" title="关闭 (Esc)" @click="closeFullscreen">
              <svg width="13" height="13" viewBox="0 0 13 13" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round">
                <path d="M2 2l9 9M11 2l-9 9"/>
              </svg>
            </button>
          </div>
        </div>
        <div ref="fullRef" class="mmd-fullcanvas"
          :class="{ dragging: fullDragging }"
          @mousedown="startPan($event, 'full')"
          @dblclick="fitFull"
        >
          <div class="mmd-svg-wrap" v-html="fullSvg" />
          <div class="mmd-hint">滚轮缩放 · 拖拽平移 · 双击适配 · Esc 关闭</div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.mmd-wrap { margin: 12px 0; }

.mmd-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 4px; }
.mmd-lang { font-size: 11px; color: var(--color-gray-400); font-family: var(--font-mono); }
.mmd-controls { display: flex; align-items: center; gap: 2px; }
.mmd-pct { font-size: 11px; color: var(--color-gray-400); font-family: var(--font-mono); min-width: 36px; text-align: right; margin-right: 2px; }
.mmd-sep { width: 1px; height: 12px; background: var(--color-gray-200); margin: 0 4px; }
.mmd-btn {
  display: inline-flex; align-items: center; justify-content: center;
  width: 22px; height: 22px; border: none; border-radius: 4px;
  background: transparent; color: var(--color-gray-500);
  cursor: pointer; font-size: 13px; line-height: 1; transition: background 80ms;
}
.mmd-btn:hover { background: var(--color-gray-100); color: var(--color-gray-800); }
.mmd-expand { color: var(--color-gray-400); }
.mmd-close:hover { background: #fee2e2; color: #dc2626; }

/* Inline canvas */
.mmd-canvas {
  background: var(--color-gray-50);
  border: 1px solid var(--color-gray-200);
  border-radius: var(--radius-md);
  overflow: hidden; height: 280px;
  cursor: grab; user-select: none;
}
.mmd-canvas.dragging { cursor: grabbing; }

/* SVG fills its container — viewBox controls the viewport */
.mmd-svg-wrap { width: 100%; height: 100%; display: flex; }
.mmd-svg-wrap :deep(svg) { width: 100%; height: 100%; display: block; }

.mmd-error {
  background: #fff5f5; border: 1px solid #fed7d7; color: #c53030;
  border-radius: var(--radius-md); padding: 8px 12px;
  font-size: 12px; font-family: var(--font-mono);
  margin: 8px;
}
.mmd-loading { color: var(--color-gray-400); font-size: 12px; padding: 8px; }

.mmd-src {
  background: var(--color-gray-50); color: var(--color-gray-800);
  border: 1px solid var(--color-gray-200);
  padding: 12px 16px; border-radius: var(--radius-md); overflow-x: auto;
  font-family: var(--font-mono); font-size: 13px; line-height: 1.6; margin: 0;
}
.mmd-src :deep(code) { background: transparent; color: inherit; padding: 0; }

pre {
  background: var(--color-gray-50); color: var(--color-gray-800);
  border: 1px solid var(--color-gray-200);
  padding: 16px; border-radius: var(--radius-md); overflow-x: auto;
  font-family: var(--font-mono); font-size: 13px; line-height: 1.6; margin: 0;
}
pre :deep(code) { background: transparent; color: inherit; padding: 0; }

/* Fullscreen */
.mmd-backdrop {
  position: fixed; inset: 0; z-index: 1000;
  background: rgba(0,0,0,0.6); backdrop-filter: blur(4px);
  display: flex; align-items: center; justify-content: center;
}
.mmd-modal {
  background: #fff; border-radius: 10px;
  width: 92vw; height: 88vh;
  display: flex; flex-direction: column; overflow: hidden;
  box-shadow: 0 24px 80px rgba(0,0,0,0.3);
}
.mmd-modal-bar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 12px; border-bottom: 1px solid var(--color-gray-100);
  flex-shrink: 0; background: #fafafa;
}
.mmd-fullcanvas {
  flex: 1; overflow: hidden; position: relative;
  cursor: grab; user-select: none;
  background: radial-gradient(circle, var(--color-gray-300) 1px, transparent 1px);
  background-size: 24px 24px; background-color: var(--color-gray-50);
}
.mmd-fullcanvas.dragging { cursor: grabbing; }
.mmd-fullcanvas .mmd-svg-wrap { width: 100%; height: 100%; }
.mmd-hint {
  position: absolute; bottom: 12px; left: 50%; transform: translateX(-50%);
  font-size: 11px; color: var(--color-gray-400);
  background: rgba(255,255,255,0.85); padding: 3px 10px;
  border-radius: 100px; pointer-events: none; white-space: nowrap;
}
</style>
