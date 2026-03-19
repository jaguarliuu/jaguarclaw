<script setup lang="ts">
import { computed } from 'vue'
import { useDevPerformance } from '@/composables/useDevPerformance'

const {
  visible,
  collapsed,
  fps,
  frameDeltaMs,
  maxFrameDeltaMs,
  slowFrameCount,
  lastSlowFrameMs,
  lastSlowFrameAt,
  pendingWsRequests,
  heapUsedMb,
  heapTotalMb,
  heapLimitMb,
  longTasks,
  chatSnapshot,
  topWsEvents,
  topWsRequests,
  topWorkItems,
  topComponents,
  bottleneckHints,
  toggleVisible,
  toggleCollapsed,
  resetStats,
  serializeSnapshot,
} = useDevPerformance()

const canCopy = computed(() => typeof navigator !== 'undefined' && !!navigator.clipboard)

function formatMs(value: number | null | undefined) {
  if (typeof value !== 'number' || Number.isNaN(value)) return 'n/a'
  return `${value.toFixed(value >= 100 ? 0 : 1)}ms`
}

function formatMb(value: number | null | undefined) {
  if (typeof value !== 'number' || Number.isNaN(value)) return 'n/a'
  return `${value.toFixed(1)} MB`
}

function formatTimestamp(value: number | null) {
  if (!value) return 'n/a'
  return new Date(value).toLocaleTimeString('zh-CN', { hour12: false })
}

async function copySnapshot() {
  if (!canCopy.value) return
  await navigator.clipboard.writeText(JSON.stringify(serializeSnapshot(), null, 2))
}
</script>

<template>
  <aside v-if="visible" class="perf-panel" :class="{ collapsed }">
    <header class="perf-header">
      <div>
        <p class="eyebrow">Dev Performance</p>
        <p class="shortcut">Toggle: Ctrl/Cmd + Shift + D</p>
      </div>
      <div class="header-actions">
        <button class="panel-btn" @click="resetStats">Reset</button>
        <button class="panel-btn" :disabled="!canCopy" @click="copySnapshot">Copy</button>
        <button class="panel-btn" @click="toggleCollapsed">{{ collapsed ? 'Expand' : 'Fold' }}</button>
        <button class="panel-btn danger" @click="toggleVisible">Close</button>
      </div>
    </header>

    <div v-if="!collapsed" class="perf-body">
      <section class="perf-section">
        <h3>Summary</h3>
        <div class="metric-grid">
          <div class="metric-card">
            <span class="metric-label">FPS</span>
            <strong>{{ fps || '...' }}</strong>
          </div>
          <div class="metric-card">
            <span class="metric-label">Frame</span>
            <strong>{{ formatMs(frameDeltaMs) }}</strong>
          </div>
          <div class="metric-card">
            <span class="metric-label">Worst Frame</span>
            <strong>{{ formatMs(maxFrameDeltaMs) }}</strong>
          </div>
          <div class="metric-card">
            <span class="metric-label">Slow Frames</span>
            <strong>{{ slowFrameCount }}</strong>
          </div>
          <div class="metric-card">
            <span class="metric-label">Pending WS</span>
            <strong>{{ pendingWsRequests }}</strong>
          </div>
          <div class="metric-card">
            <span class="metric-label">Heap</span>
            <strong>{{ formatMb(heapUsedMb) }}</strong>
          </div>
        </div>
        <p class="detail-line">
          Last slow frame: {{ formatMs(lastSlowFrameMs) }} at {{ formatTimestamp(lastSlowFrameAt) }}
        </p>
        <p class="detail-line">
          Heap total / limit: {{ formatMb(heapTotalMb) }} / {{ formatMb(heapLimitMb) }}
        </p>
      </section>

      <section class="perf-section">
        <h3>Hints</h3>
        <p v-for="hint in bottleneckHints" :key="hint" class="hint-line">{{ hint }}</p>
      </section>

      <section class="perf-section">
        <h3>Chat State</h3>
        <div class="metric-grid compact">
          <div class="metric-card">
            <span class="metric-label">Sessions</span>
            <strong>{{ chatSnapshot.sessionCount }}</strong>
          </div>
          <div class="metric-card">
            <span class="metric-label">Messages</span>
            <strong>{{ chatSnapshot.messageCount }}</strong>
          </div>
          <div class="metric-card">
            <span class="metric-label">Stream Blocks</span>
            <strong>{{ chatSnapshot.streamBlockCount }}</strong>
          </div>
          <div class="metric-card">
            <span class="metric-label">Session Files</span>
            <strong>{{ chatSnapshot.sessionFileCount }}</strong>
          </div>
          <div class="metric-card">
            <span class="metric-label">Subagents</span>
            <strong>{{ chatSnapshot.subagentCount }}</strong>
          </div>
          <div class="metric-card">
            <span class="metric-label">Token Flushes</span>
            <strong>{{ chatSnapshot.tokenFlushCount }}</strong>
          </div>
        </div>
        <p class="detail-line">
          Streaming: {{ chatSnapshot.isStreaming ? 'yes' : 'no' }} | Session:
          {{ chatSnapshot.currentSessionId || 'n/a' }} | Run: {{ chatSnapshot.currentRunId || 'n/a' }}
        </p>
        <p class="detail-line">
          Last message load: {{ formatMs(chatSnapshot.lastMessageLoadMs) }} / {{
            chatSnapshot.lastLoadedMessageCount ?? 'n/a'
          }} msgs
        </p>
        <p class="detail-line">
          Last session load: {{ formatMs(chatSnapshot.lastSessionLoadMs) }} / {{
            chatSnapshot.lastLoadedSessionCount ?? 'n/a'
          }} sessions
        </p>
      </section>

      <section class="perf-section">
        <h3>WS Events</h3>
        <div v-if="topWsEvents.length === 0" class="empty-line">No events yet.</div>
        <div v-for="[name, stat] in topWsEvents" :key="name" class="stat-row">
          <span class="stat-name">{{ name }}</span>
          <span class="stat-values">{{ stat.perSecond }}/s · total {{ stat.total }} · max {{ formatMs(stat.maxDurationMs) }}</span>
        </div>
      </section>

      <section class="perf-section">
        <h3>WS Requests</h3>
        <div v-if="topWsRequests.length === 0" class="empty-line">No requests yet.</div>
        <div v-for="[name, stat] in topWsRequests" :key="name" class="stat-row">
          <span class="stat-name">{{ name }}</span>
          <span class="stat-values">avg {{ formatMs(stat.avgDurationMs) }} · max {{ formatMs(stat.maxDurationMs) }} · total {{ stat.total }}</span>
        </div>
      </section>

      <section class="perf-section">
        <h3>Work Hotspots</h3>
        <div v-if="topWorkItems.length === 0" class="empty-line">No timed work yet.</div>
        <div v-for="[name, stat] in topWorkItems" :key="name" class="stat-row">
          <span class="stat-name">{{ name }}</span>
          <span class="stat-values">avg {{ formatMs(stat.avgDurationMs) }} · max {{ formatMs(stat.maxDurationMs) }} · total {{ stat.total }}</span>
        </div>
      </section>

      <section class="perf-section">
        <h3>Component Updates</h3>
        <div v-if="topComponents.length === 0" class="empty-line">No component lifecycle data yet.</div>
        <div v-for="[name, stat] in topComponents" :key="name" class="stat-row">
          <span class="stat-name">{{ name }}</span>
          <span class="stat-values">
            active {{ stat.activeInstances }} · updates {{ stat.updates }} · avg interval
            {{ formatMs(stat.avgIntervalMs) }}
          </span>
        </div>
      </section>

      <section class="perf-section">
        <h3>Long Tasks</h3>
        <div v-if="longTasks.length === 0" class="empty-line">No long tasks observed.</div>
        <div v-for="task in longTasks" :key="task.id" class="stat-row">
          <span class="stat-name">{{ task.name }}</span>
          <span class="stat-values">{{ formatMs(task.durationMs) }} · {{ formatTimestamp(task.startedAt) }}</span>
        </div>
      </section>
    </div>
  </aside>
</template>

<style scoped>
.perf-panel {
  position: fixed;
  right: 20px;
  bottom: 20px;
  z-index: 1200;
  width: min(420px, calc(100vw - 40px));
  max-height: calc(100vh - 40px);
  overflow: hidden;
  border: 1px solid rgba(17, 17, 17, 0.12);
  border-radius: 16px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.97), rgba(247, 247, 247, 0.97)),
    rgba(255, 255, 255, 0.96);
  box-shadow: 0 20px 48px rgba(0, 0, 0, 0.16);
  backdrop-filter: blur(18px);
}

.perf-panel.collapsed {
  width: 300px;
}

.perf-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid rgba(17, 17, 17, 0.08);
}

.eyebrow {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 600;
  color: var(--color-primary);
}

.shortcut {
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-gray-500);
}

.header-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.panel-btn {
  border: 1px solid rgba(17, 17, 17, 0.12);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.9);
  color: var(--color-gray-700);
  padding: 6px 10px;
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
}

.panel-btn:disabled {
  opacity: 0.45;
  cursor: default;
}

.panel-btn.danger {
  color: #7f1d1d;
  background: #fff4f4;
}

.perf-body {
  max-height: calc(100vh - 112px);
  overflow-y: auto;
  padding: 12px 16px 16px;
}

.perf-section + .perf-section {
  margin-top: 16px;
}

.perf-section h3 {
  margin-bottom: 10px;
  font-size: 12px;
  font-family: var(--font-mono);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--color-gray-500);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.metric-grid.compact {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.metric-card {
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(17, 17, 17, 0.04);
}

.metric-label {
  display: block;
  margin-bottom: 6px;
  font-size: 11px;
  color: var(--color-gray-500);
}

.metric-card strong {
  font-size: 15px;
  line-height: 1.1;
  color: var(--color-black);
}

.detail-line,
.hint-line,
.empty-line,
.stat-values {
  font-size: 12px;
  color: var(--color-gray-600);
}

.detail-line + .detail-line,
.hint-line + .hint-line {
  margin-top: 6px;
}

.stat-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 7px 0;
  border-top: 1px solid rgba(17, 17, 17, 0.06);
}

.stat-row:first-of-type {
  border-top: none;
}

.stat-name {
  font-size: 12px;
  font-family: var(--font-mono);
  color: var(--color-black);
  min-width: 0;
  word-break: break-word;
}

.stat-values {
  text-align: right;
  flex-shrink: 0;
}

@media (max-width: 720px) {
  .perf-panel {
    right: 12px;
    bottom: 12px;
    width: calc(100vw - 24px);
  }

  .metric-grid,
  .metric-grid.compact {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .perf-header {
    flex-direction: column;
  }
}
</style>
