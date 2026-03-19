import { computed, reactive, readonly, ref } from 'vue'

type CounterStat = {
  total: number
  perSecond: number
  lastDurationMs: number
  maxDurationMs: number
  avgDurationMs: number
  _secondCount: number
  _totalDurationMs: number
}

type ComponentStat = {
  mounts: number
  unmounts: number
  activeInstances: number
  updates: number
  lastUpdateAt: number | null
  lastIntervalMs: number | null
  avgIntervalMs: number | null
}

type LongTaskStat = {
  id: string
  startedAt: number
  durationMs: number
  name: string
}

type ChatSnapshot = {
  currentSessionId: string | null
  currentRunId: string | null
  isStreaming: boolean
  sessionCount: number
  messageCount: number
  streamBlockCount: number
  sessionFileCount: number
  subagentCount: number
  tokenFlushCount: number
  lastMessageLoadMs: number | null
  lastLoadedMessageCount: number | null
  lastSessionLoadMs: number | null
  lastLoadedSessionCount: number | null
}

type PerformanceMemoryLike = Performance & {
  memory?: {
    usedJSHeapSize: number
    totalJSHeapSize: number
    jsHeapSizeLimit: number
  }
}

const STORAGE_KEY = 'jaguarclaw.dev.performance.visible'
const MAX_LONG_TASKS = 12

const visible = ref(readStoredVisibility())
const collapsed = ref(false)
const started = ref(false)

const fps = ref(0)
const frameDeltaMs = ref(0)
const maxFrameDeltaMs = ref(0)
const slowFrameCount = ref(0)
const lastSlowFrameMs = ref(0)
const lastSlowFrameAt = ref<number | null>(null)

const pendingWsRequests = ref(0)
const heapUsedMb = ref<number | null>(null)
const heapTotalMb = ref<number | null>(null)
const heapLimitMb = ref<number | null>(null)

const wsEventStats = reactive<Record<string, CounterStat>>({})
const wsRequestStats = reactive<Record<string, CounterStat>>({})
const workStats = reactive<Record<string, CounterStat>>({})
const componentStats = reactive<Record<string, ComponentStat>>({})
const longTasks = ref<LongTaskStat[]>([])
const chatSnapshot = reactive<ChatSnapshot>({
  currentSessionId: null,
  currentRunId: null,
  isStreaming: false,
  sessionCount: 0,
  messageCount: 0,
  streamBlockCount: 0,
  sessionFileCount: 0,
  subagentCount: 0,
  tokenFlushCount: 0,
  lastMessageLoadMs: null,
  lastLoadedMessageCount: null,
  lastSessionLoadMs: null,
  lastLoadedSessionCount: null,
})

let rafId: number | null = null
let tickTimer: ReturnType<typeof setInterval> | null = null
let longTaskObserver: PerformanceObserver | null = null
let lastFrameAt = 0
let frameCount = 0
let frameWindowStartedAt = 0

function readStoredVisibility(): boolean {
  if (typeof window === 'undefined') return false
  try {
    return window.localStorage.getItem(STORAGE_KEY) === 'true'
  } catch {
    return false
  }
}

function persistVisibility(next: boolean) {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.setItem(STORAGE_KEY, String(next))
  } catch {
    // ignore persistence errors
  }
}

function createCounter(): CounterStat {
  return {
    total: 0,
    perSecond: 0,
    lastDurationMs: 0,
    maxDurationMs: 0,
    avgDurationMs: 0,
    _secondCount: 0,
    _totalDurationMs: 0,
  }
}

function getCounter(target: Record<string, CounterStat>, key: string): CounterStat {
  if (!target[key]) {
    target[key] = createCounter()
  }
  return target[key]
}

function recordCounter(target: Record<string, CounterStat>, key: string, durationMs?: number) {
  const stat = getCounter(target, key)
  stat.total += 1
  stat._secondCount += 1

  if (typeof durationMs === 'number' && Number.isFinite(durationMs)) {
    stat.lastDurationMs = durationMs
    stat.maxDurationMs = Math.max(stat.maxDurationMs, durationMs)
    stat._totalDurationMs += durationMs
    stat.avgDurationMs = stat._totalDurationMs / stat.total
  }
}

function flushPerSecondCounters(target: Record<string, CounterStat>) {
  for (const stat of Object.values(target)) {
    stat.perSecond = stat._secondCount
    stat._secondCount = 0
  }
}

function pushLongTask(task: LongTaskStat) {
  longTasks.value = [task, ...longTasks.value].slice(0, MAX_LONG_TASKS)
}

function sampleMemory() {
  const perf = window.performance as PerformanceMemoryLike
  if (!perf.memory) {
    heapUsedMb.value = null
    heapTotalMb.value = null
    heapLimitMb.value = null
    return
  }

  heapUsedMb.value = perf.memory.usedJSHeapSize / 1024 / 1024
  heapTotalMb.value = perf.memory.totalJSHeapSize / 1024 / 1024
  heapLimitMb.value = perf.memory.jsHeapSizeLimit / 1024 / 1024
}

function animationLoop(now: number) {
  if (lastFrameAt > 0) {
    const delta = now - lastFrameAt
    frameDeltaMs.value = delta
    maxFrameDeltaMs.value = Math.max(maxFrameDeltaMs.value, delta)

    if (delta >= 80) {
      slowFrameCount.value += 1
      lastSlowFrameMs.value = delta
      lastSlowFrameAt.value = Date.now()
    }
  }

  if (!frameWindowStartedAt) {
    frameWindowStartedAt = now
  }

  frameCount += 1
  const elapsed = now - frameWindowStartedAt
  if (elapsed >= 1000) {
    fps.value = Math.round((frameCount * 1000) / elapsed)
    frameCount = 0
    frameWindowStartedAt = now
  }

  lastFrameAt = now
  rafId = window.requestAnimationFrame(animationLoop)
}

function setupLongTaskObserver() {
  if (typeof window.PerformanceObserver === 'undefined') return

  const supported = PerformanceObserver.supportedEntryTypes || []
  if (!supported.includes('longtask')) return

  longTaskObserver = new PerformanceObserver((list) => {
    for (const entry of list.getEntries()) {
      const durationMs = entry.duration
      pushLongTask({
        id: `${entry.startTime}-${durationMs}`,
        startedAt: Date.now(),
        durationMs,
        name: entry.name || 'longtask',
      })
    }
  })

  try {
    longTaskObserver.observe({ entryTypes: ['longtask'] })
  } catch {
    longTaskObserver = null
  }
}

function exposeGlobal() {
  ;(window as Window & { __JAGUAR_DEV_PERF__?: unknown }).__JAGUAR_DEV_PERF__ = {
    toggle: toggleVisible,
    show: () => setVisible(true),
    hide: () => setVisible(false),
    reset: resetStats,
    snapshot: serializeSnapshot,
  }
}

function start() {
  if (started.value || typeof window === 'undefined') return

  started.value = true
  sampleMemory()
  rafId = window.requestAnimationFrame(animationLoop)
  tickTimer = window.setInterval(() => {
    flushPerSecondCounters(wsEventStats)
    flushPerSecondCounters(wsRequestStats)
    flushPerSecondCounters(workStats)
    sampleMemory()
  }, 1000)
  setupLongTaskObserver()
  exposeGlobal()
}

function stop() {
  if (!started.value || typeof window === 'undefined') return

  started.value = false
  if (rafId !== null) {
    window.cancelAnimationFrame(rafId)
    rafId = null
  }
  if (tickTimer) {
    clearInterval(tickTimer)
    tickTimer = null
  }
  if (longTaskObserver) {
    longTaskObserver.disconnect()
    longTaskObserver = null
  }
  lastFrameAt = 0
  frameCount = 0
  frameWindowStartedAt = 0
}

function setVisible(next: boolean) {
  visible.value = next
  persistVisibility(next)
}

function toggleVisible() {
  setVisible(!visible.value)
}

function toggleCollapsed() {
  collapsed.value = !collapsed.value
}

function recordWebSocketEvent(eventName: string, durationMs?: number) {
  recordCounter(wsEventStats, eventName, durationMs)
}

function recordWebSocketRequest(method: string, durationMs?: number) {
  recordCounter(wsRequestStats, method, durationMs)
}

function setPendingRequests(count: number) {
  pendingWsRequests.value = count
}

function recordComponentMount(name: string) {
  if (!componentStats[name]) {
    componentStats[name] = {
      mounts: 0,
      unmounts: 0,
      activeInstances: 0,
      updates: 0,
      lastUpdateAt: null,
      lastIntervalMs: null,
      avgIntervalMs: null,
    }
  }

  componentStats[name].mounts += 1
  componentStats[name].activeInstances += 1
}

function recordComponentUnmount(name: string) {
  const stat = componentStats[name]
  if (!stat) return
  stat.unmounts += 1
  stat.activeInstances = Math.max(0, stat.activeInstances - 1)
}

function recordComponentUpdate(name: string) {
  if (!componentStats[name]) {
    recordComponentMount(name)
  }

  const stat = componentStats[name]!
  const now = performance.now()
  if (stat.lastUpdateAt !== null) {
    const interval = now - stat.lastUpdateAt
    stat.lastIntervalMs = interval
    stat.avgIntervalMs =
      stat.avgIntervalMs === null ? interval : stat.avgIntervalMs * 0.8 + interval * 0.2
  }
  stat.lastUpdateAt = now
  stat.updates += 1
}

function recordWork(name: string, durationMs: number) {
  recordCounter(workStats, name, durationMs)
}

function measureSync<T>(name: string, fn: () => T): T {
  if (typeof performance === 'undefined') {
    return fn()
  }
  const startTime = performance.now()
  const result = fn()
  recordWork(name, performance.now() - startTime)
  return result
}

function setChatSnapshot(snapshot: Partial<ChatSnapshot>) {
  Object.assign(chatSnapshot, snapshot)
}

function incrementTokenFlush() {
  chatSnapshot.tokenFlushCount += 1
}

function recordMessageLoad(durationMs: number, messageCount: number) {
  chatSnapshot.lastMessageLoadMs = durationMs
  chatSnapshot.lastLoadedMessageCount = messageCount
}

function recordSessionLoad(durationMs: number, sessionCount: number) {
  chatSnapshot.lastSessionLoadMs = durationMs
  chatSnapshot.lastLoadedSessionCount = sessionCount
}

function resetStats() {
  fps.value = 0
  frameDeltaMs.value = 0
  maxFrameDeltaMs.value = 0
  slowFrameCount.value = 0
  lastSlowFrameMs.value = 0
  lastSlowFrameAt.value = null
  pendingWsRequests.value = 0
  heapUsedMb.value = null
  heapTotalMb.value = null
  heapLimitMb.value = null
  longTasks.value = []

  for (const target of [wsEventStats, wsRequestStats, workStats]) {
    for (const key of Object.keys(target)) {
      delete target[key]
    }
  }

  for (const key of Object.keys(componentStats)) {
    delete componentStats[key]
  }

  Object.assign(chatSnapshot, {
    currentSessionId: null,
    currentRunId: null,
    isStreaming: false,
    sessionCount: 0,
    messageCount: 0,
    streamBlockCount: 0,
    sessionFileCount: 0,
    subagentCount: 0,
    tokenFlushCount: 0,
    lastMessageLoadMs: null,
    lastLoadedMessageCount: null,
    lastSessionLoadMs: null,
    lastLoadedSessionCount: null,
  } satisfies ChatSnapshot)
}

function formatDateTime(timestamp: number | null): string {
  if (!timestamp) return 'n/a'
  return new Date(timestamp).toLocaleTimeString('zh-CN', { hour12: false })
}

const topWsEvents = computed(() =>
  Object.entries(wsEventStats)
    .sort((a, b) => {
      if (b[1].perSecond !== a[1].perSecond) return b[1].perSecond - a[1].perSecond
      return b[1].total - a[1].total
    })
    .slice(0, 8),
)

const topWsRequests = computed(() =>
  Object.entries(wsRequestStats)
    .sort((a, b) => {
      if (b[1].maxDurationMs !== a[1].maxDurationMs) return b[1].maxDurationMs - a[1].maxDurationMs
      return b[1].total - a[1].total
    })
    .slice(0, 8),
)

const topWorkItems = computed(() =>
  Object.entries(workStats)
    .sort((a, b) => {
      if (b[1].maxDurationMs !== a[1].maxDurationMs) return b[1].maxDurationMs - a[1].maxDurationMs
      return b[1].total - a[1].total
    })
    .slice(0, 8),
)

const topComponents = computed(() =>
  Object.entries(componentStats)
    .sort((a, b) => {
      if (b[1].updates !== a[1].updates) return b[1].updates - a[1].updates
      return b[1].activeInstances - a[1].activeInstances
    })
    .slice(0, 8),
)

const bottleneckHints = computed(() => {
  const hints: string[] = []
  const latestLongTask = longTasks.value[0]

  if (fps.value > 0 && fps.value < 30) {
    hints.push(`帧率偏低（${fps.value} FPS），主线程可能被同步渲染或布局阻塞。`)
  }

  if (latestLongTask && latestLongTask.durationMs >= 120) {
    hints.push(`最近出现 ${latestLongTask.durationMs.toFixed(1)}ms 长任务，优先看 JS 或 markdown 渲染。`)
  }

  const busiestComponent = topComponents.value[0]
  if (busiestComponent && busiestComponent[1].updates > 200) {
    hints.push(`${busiestComponent[0]} 更新次数很高，可能存在大列表重复渲染。`)
  }

  const hottestWork = topWorkItems.value[0]
  if (hottestWork && hottestWork[1].maxDurationMs >= 12) {
    hints.push(`${hottestWork[0]} 单次耗时较高，先看这个热点。`)
  }

  if (chatSnapshot.messageCount >= 200) {
    hints.push(`当前会话消息数 ${chatSnapshot.messageCount}，列表和 markdown 全量渲染风险较高。`)
  }

  if (chatSnapshot.sessionCount >= 100) {
    hints.push(`会话数 ${chatSnapshot.sessionCount} 较多，侧边栏列表更新可能放大卡顿。`)
  }

  if (hints.length === 0) {
    hints.push('当前没有明显单点异常，继续观察 WS 事件密度、组件更新频率和长任务。')
  }

  return hints
})

function serializeSnapshot() {
  return {
    visible: visible.value,
    collapsed: collapsed.value,
    fps: fps.value,
    frameDeltaMs: frameDeltaMs.value,
    maxFrameDeltaMs: maxFrameDeltaMs.value,
    slowFrameCount: slowFrameCount.value,
    lastSlowFrameMs: lastSlowFrameMs.value,
    lastSlowFrameAt: formatDateTime(lastSlowFrameAt.value),
    pendingWsRequests: pendingWsRequests.value,
    memory: {
      usedMb: heapUsedMb.value,
      totalMb: heapTotalMb.value,
      limitMb: heapLimitMb.value,
    },
    chat: { ...chatSnapshot },
    wsEvents: Object.fromEntries(topWsEvents.value),
    wsRequests: Object.fromEntries(topWsRequests.value),
    work: Object.fromEntries(topWorkItems.value),
    components: Object.fromEntries(topComponents.value),
    longTasks: longTasks.value.map((task) => ({
      ...task,
      startedAt: formatDateTime(task.startedAt),
    })),
    hints: bottleneckHints.value,
  }
}

export function useDevPerformance() {
  return {
    visible: readonly(visible),
    collapsed: readonly(collapsed),
    started: readonly(started),
    fps: readonly(fps),
    frameDeltaMs: readonly(frameDeltaMs),
    maxFrameDeltaMs: readonly(maxFrameDeltaMs),
    slowFrameCount: readonly(slowFrameCount),
    lastSlowFrameMs: readonly(lastSlowFrameMs),
    lastSlowFrameAt: readonly(lastSlowFrameAt),
    pendingWsRequests: readonly(pendingWsRequests),
    heapUsedMb: readonly(heapUsedMb),
    heapTotalMb: readonly(heapTotalMb),
    heapLimitMb: readonly(heapLimitMb),
    longTasks: readonly(longTasks),
    chatSnapshot: readonly(chatSnapshot),
    topWsEvents,
    topWsRequests,
    topWorkItems,
    topComponents,
    bottleneckHints,
    start,
    stop,
    setVisible,
    toggleVisible,
    toggleCollapsed,
    recordWebSocketEvent,
    recordWebSocketRequest,
    setPendingRequests,
    recordComponentMount,
    recordComponentUnmount,
    recordComponentUpdate,
    recordWork,
    measureSync,
    setChatSnapshot,
    incrementTokenFlush,
    recordMessageLoad,
    recordSessionLoad,
    resetStats,
    serializeSnapshot,
  }
}
