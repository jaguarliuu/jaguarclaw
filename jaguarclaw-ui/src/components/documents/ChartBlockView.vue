<script setup lang="ts">
import { NodeViewWrapper } from '@tiptap/vue-3'
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import hljs from 'highlight.js/lib/core'
import hljsJson from 'highlight.js/lib/languages/json'
hljs.registerLanguage('json', hljsJson)

const props = defineProps<{
  node: any
  updateAttributes: (attrs: Record<string, any>) => void
  editor: any
}>()

const chartRef = ref<HTMLDivElement | null>(null)
const fullRef = ref<HTMLDivElement | null>(null)
const textareaRef = ref<HTMLTextAreaElement | null>(null)
const highlightRef = ref<HTMLPreElement | null>(null)
const showCode = ref(false)
const fullscreen = ref(false)
const renderError = ref('')
const specText = ref(props.node.attrs.spec)

// ── JSON syntax highlight for the editor ─────────────────────────────────────
function escHtml(s: string) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}
const highlightedJson = computed(() => {
  try {
    return hljs.highlight(specText.value, { language: 'json' }).value + '\n'
  } catch {
    return escHtml(specText.value) + '\n'
  }
})
function syncScroll() {
  if (highlightRef.value && textareaRef.value) {
    highlightRef.value.scrollTop = textareaRef.value.scrollTop
    highlightRef.value.scrollLeft = textareaRef.value.scrollLeft
  }
}

let chart: echarts.ECharts | null = null
let fullChart: echarts.ECharts | null = null
let renderTimer: ReturnType<typeof setTimeout> | null = null
let resizeOb: ResizeObserver | null = null

const CHART_LABELS: Record<string, string> = {
  bar: '柱状图', line: '折线图', pie: '饼图', scatter: '散点图',
  radar: '雷达图', funnel: '漏斗图', gauge: '仪表盘', heatmap: '热力图',
  candlestick: 'K线图', boxplot: '箱线图', tree: '树图', treemap: '矩形树图',
  effectScatter: '特效散点图', lines: '路径图', map: '地图',
}

const chartTypeLabel = computed(() => {
  try {
    const opt = JSON.parse(props.node.attrs.spec)
    const type = opt.series?.[0]?.type
    if (!type) return 'chart'
    if (type === 'line' && opt.series?.[0]?.areaStyle !== undefined) return '面积图'
    return CHART_LABELS[type] ?? type
  } catch { return 'chart' }
})

function renderChart() {
  const spec = props.node.attrs.spec
  if (!spec) return
  const el = chartRef.value
  if (!el) return
  try {
    const option = JSON.parse(spec)
    if (!chart) chart = echarts.init(el, null, { renderer: 'svg' })
    chart.setOption(option, true)
    renderError.value = ''
  } catch (e: any) {
    renderError.value = e?.message?.split('\n')[0] || String(e)
  }
}

function renderFullChart() {
  const spec = props.node.attrs.spec
  if (!spec || !fullRef.value) return
  try {
    const option = JSON.parse(spec)
    if (!fullChart) fullChart = echarts.init(fullRef.value, null, { renderer: 'svg' })
    fullChart.setOption(option, true)
    fullChart.resize()
  } catch {}
}

watch(chartRef, el => {
  if (!el) return
  renderChart()
  resizeOb = new ResizeObserver(() => chart?.resize())
  resizeOb.observe(el)
})

watch(() => props.node.attrs.spec, v => {
  specText.value = v
  if (renderTimer) clearTimeout(renderTimer)
  renderTimer = setTimeout(renderChart, 600)
})

watch(showCode, v => {
  if (!v) nextTick(() => chart?.resize())
})

function onSpecInput(e: Event) {
  specText.value = (e.target as HTMLTextAreaElement).value
  if (renderTimer) clearTimeout(renderTimer)
  renderTimer = setTimeout(() => {
    try {
      JSON.parse(specText.value)
      props.updateAttributes({ spec: specText.value })
    } catch {}
  }, 800)
}

function openFullscreen() {
  fullscreen.value = true
  nextTick(() => renderFullChart())
}

function closeFullscreen() {
  fullscreen.value = false
  fullChart?.dispose()
  fullChart = null
}

function onWindowResize() {
  chart?.resize()
  if (fullscreen.value) fullChart?.resize()
}

function onKeydown(e: KeyboardEvent) { if (e.key === 'Escape') closeFullscreen() }

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('resize', onWindowResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('resize', onWindowResize)
  resizeOb?.disconnect()
  chart?.dispose()
  fullChart?.dispose()
  if (renderTimer) clearTimeout(renderTimer)
})
</script>

<template>
  <NodeViewWrapper class="chart-wrap">
    <!-- Header -->
    <div class="chart-header">
      <span class="chart-lang">{{ chartTypeLabel }}</span>
      <div class="chart-controls">
        <template v-if="!showCode">
          <button class="chart-btn chart-expand" title="全屏展开" @click="openFullscreen">
            <svg width="13" height="13" viewBox="0 0 13 13" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round">
              <path d="M1 5V1h4M8 1h4v4M12 8v4H8M5 12H1V8"/>
            </svg>
          </button>
          <div class="chart-sep"/>
        </template>
        <button class="chart-btn" :title="showCode ? '查看图表' : '编辑数据'" @click="showCode = !showCode">
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

    <!-- ECharts canvas (always in DOM for resize tracking) -->
    <div v-show="!showCode" ref="chartRef" class="chart-canvas">
      <div v-if="renderError" class="chart-error">⚠ {{ renderError }}</div>
    </div>

    <!-- JSON spec editor with syntax highlighting -->
    <div v-if="showCode" class="chart-editor">
      <pre ref="highlightRef" class="chart-highlight"><code v-html="highlightedJson" /></pre>
      <textarea
        ref="textareaRef"
        class="chart-src-overlay"
        :value="specText"
        spellcheck="false"
        autocomplete="off"
        @input="onSpecInput"
        @scroll="syncScroll"
      />
    </div>
  </NodeViewWrapper>

  <!-- Fullscreen modal -->
  <Teleport to="body">
    <div v-if="fullscreen" class="chart-backdrop" @click.self="closeFullscreen">
      <div class="chart-modal">
        <div class="chart-modal-bar">
          <span class="chart-lang">{{ chartTypeLabel }}</span>
          <button class="chart-btn chart-close" title="关闭 (Esc)" @click="closeFullscreen">
            <svg width="13" height="13" viewBox="0 0 13 13" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round">
              <path d="M2 2l9 9M11 2l-9 9"/>
            </svg>
          </button>
        </div>
        <div ref="fullRef" class="chart-fullcanvas" />
        <div class="chart-hint">Esc 关闭 · 拖拽不可用（SVG 模式）</div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.chart-wrap { margin: 12px 0; }

.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}
.chart-lang { font-size: 11px; color: var(--color-gray-400); font-family: var(--font-mono); }
.chart-controls { display: flex; align-items: center; gap: 2px; }
.chart-sep { width: 1px; height: 12px; background: var(--color-gray-200); margin: 0 4px; }

.chart-btn {
  display: inline-flex; align-items: center; justify-content: center;
  width: 22px; height: 22px; border: none; border-radius: 4px;
  background: transparent; color: var(--color-gray-500);
  cursor: pointer; font-size: 13px; line-height: 1; transition: background 80ms;
}
.chart-btn:hover { background: var(--color-gray-100); color: var(--color-gray-800); }
.chart-expand { color: var(--color-gray-400); }
.chart-close:hover { background: #fee2e2; color: #dc2626; }

.chart-canvas {
  background: var(--color-gray-50);
  border: 1px solid var(--color-gray-200);
  border-radius: var(--radius-md);
  overflow: hidden;
  height: 280px;
  position: relative;
}

.chart-error {
  position: absolute; inset: 0;
  display: flex; align-items: center; justify-content: center;
  background: #fff5f5; color: #c53030;
  font-size: 12px; font-family: var(--font-mono);
  padding: 8px 12px; text-align: center;
}

.chart-src-overlay {
  position: absolute;
  inset: 0;
  padding: 12px 16px;
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.6;
  background: transparent;
  color: transparent;
  caret-color: var(--color-gray-800);
  border: none;
  outline: none;
  resize: none;
  width: 100%;
  height: 100%;
  box-sizing: border-box;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  z-index: 1;
  tab-size: 2;
}

/* Highlighted editor container */
.chart-editor {
  position: relative;
  height: 280px;
  background: var(--color-gray-50);
  border: 1px solid var(--color-gray-200);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.chart-highlight {
  position: absolute;
  inset: 0;
  margin: 0;
  padding: 12px 16px;
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.6;
  background: transparent;
  color: var(--color-gray-800);
  pointer-events: none;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  tab-size: 2;
}
.chart-highlight :deep(code) {
  background: transparent;
  color: inherit;
  padding: 0;
  font-size: inherit;
}

/* Fullscreen */
.chart-backdrop {
  position: fixed; inset: 0; z-index: 1000;
  background: rgba(0,0,0,0.6); backdrop-filter: blur(4px);
  display: flex; align-items: center; justify-content: center;
}
.chart-modal {
  background: #fff; border-radius: 10px;
  width: 92vw; height: 88vh;
  display: flex; flex-direction: column; overflow: hidden;
  box-shadow: 0 24px 80px rgba(0,0,0,0.3);
  position: relative;
}
.chart-modal-bar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 12px; border-bottom: 1px solid var(--color-gray-100);
  flex-shrink: 0; background: #fafafa;
}
.chart-fullcanvas { flex: 1; overflow: hidden; }
.chart-hint {
  position: absolute; bottom: 12px; left: 50%; transform: translateX(-50%);
  font-size: 11px; color: var(--color-gray-400);
  background: rgba(255,255,255,0.85); padding: 3px 10px;
  border-radius: 100px; pointer-events: none; white-space: nowrap;
}
</style>
