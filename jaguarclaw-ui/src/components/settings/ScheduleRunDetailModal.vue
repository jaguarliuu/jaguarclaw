<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from '@/i18n'
import MessageItem from '@/components/MessageItem.vue'
import type { ScheduleRunDetail, ScheduleRunLog, ScheduleRunTraceItem } from '@/types'

const props = defineProps<{
  show: boolean
  run: ScheduleRunLog | null
  detail: ScheduleRunDetail | null
  loading: boolean
  errorMessage?: string | null
}>()

const emit = defineEmits<{
  close: []
}>()

const { t } = useI18n()

const traceItems = computed(() => props.detail?.trace || [])
const messages = computed(() => props.detail?.messages || [])

function formatTime(dateStr?: string | null): string {
  if (!dateStr) return '-'
  const value = new Date(dateStr)
  return Number.isNaN(value.getTime()) ? dateStr : value.toLocaleString()
}

function formatDuration(ms?: number | null): string {
  if (ms == null) return '-'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

function eventSummary(item: ScheduleRunTraceItem): string {
  const data = item.data || {}
  switch (item.eventType) {
    case 'run.created':
      return 'Run created'
    case 'run.completed':
      return 'Run completed'
    case 'run.failed':
      return String(data.error || 'Run failed')
    case 'step.completed':
      return `Step ${data.step || '?'} / ${data.maxSteps || '?'}`
    case 'tool.call':
      return `Tool call: ${data.toolName || 'unknown'}`
    case 'tool.result':
      return `Tool result: ${data.success ? 'success' : 'failed'}`
    case 'tool.confirm_request':
      return `Awaiting confirm: ${data.toolName || 'unknown'}`
    case 'file.created':
      return `File created: ${data.fileName || data.path || 'unknown'}`
    case 'skill.activated':
      return `Skill activated: ${data.skillName || 'unknown'}`
    case 'subagent.spawned':
      return `Subagent spawned: ${data.agentId || data.subRunId || 'unknown'}`
    case 'subagent.started':
      return `Subagent started: ${data.subRunId || 'unknown'}`
    case 'subagent.announced':
      return `Subagent announced: ${data.status || 'unknown'}`
    case 'subagent.failed':
      return `Subagent failed: ${data.error || 'unknown'}`
    case 'lifecycle.error':
      return String(data.error || 'Lifecycle error')
    case 'context.compacted':
      return 'Context compacted'
    case 'run.outcome':
      return `Run outcome: ${data.status || 'unknown'}`
    default:
      return item.eventType
  }
}

function eventDetail(item: ScheduleRunTraceItem): string {
  const data = item.data || {}
  switch (item.eventType) {
    case 'tool.call':
    case 'tool.confirm_request':
      return stringify(data.arguments)
    case 'tool.result':
      return stringify(data.content ?? data)
    case 'file.created':
      return stringify({
        path: data.path,
        fileName: data.fileName,
        size: data.size
      })
    case 'step.completed':
      return stringify({
        step: data.step,
        maxSteps: data.maxSteps,
        elapsedSeconds: data.elapsedSeconds
      })
    case 'run.failed':
      return stringify({ error: data.error })
    default:
      return stringify(data)
  }
}

function stringify(value: unknown): string {
  if (value == null) return ''
  if (typeof value === 'string') return value
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}
</script>

<template>
  <div v-if="show" class="modal-overlay" @click.self="emit('close')">
    <div class="modal-content">
      <div class="modal-header">
        <div>
          <h3 class="modal-title">{{ t('sections.schedules.detailModal.title') }}</h3>
          <p class="modal-subtitle">
            {{ run?.taskName || run?.taskId || t('sections.schedules.detailModal.subtitleFallback') }}
          </p>
        </div>
        <button class="close-btn" @click="emit('close')">✕</button>
      </div>

      <div v-if="run" class="meta-grid">
        <div class="meta-item">
          <span class="meta-label">{{ t('sections.schedules.detailModal.meta.status') }}</span>
          <span class="meta-value">{{ run.status }}</span>
        </div>
        <div class="meta-item">
          <span class="meta-label">{{ t('sections.schedules.detailModal.meta.trigger') }}</span>
          <span class="meta-value">{{ run.triggeredBy }}</span>
        </div>
        <div class="meta-item">
          <span class="meta-label">{{ t('sections.schedules.detailModal.meta.startedAt') }}</span>
          <span class="meta-value">{{ formatTime(run.startedAt) }}</span>
        </div>
        <div class="meta-item">
          <span class="meta-label">{{ t('sections.schedules.detailModal.meta.duration') }}</span>
          <span class="meta-value">{{ formatDuration(run.durationMs) }}</span>
        </div>
        <div class="meta-item">
          <span class="meta-label">{{ t('sections.schedules.detailModal.meta.sessionId') }}</span>
          <span class="meta-value mono">{{ run.sessionId || '-' }}</span>
        </div>
        <div class="meta-item">
          <span class="meta-label">{{ t('sections.schedules.detailModal.meta.runId') }}</span>
          <span class="meta-value mono">{{ run.runId || '-' }}</span>
        </div>
      </div>

      <div v-if="loading" class="state-block">{{ t('sections.schedules.detailModal.loading') }}</div>
      <div v-else-if="errorMessage" class="state-block error">{{ errorMessage }}</div>
      <div v-else class="modal-body">
        <section class="pane">
          <div class="pane-head">
            <h4>{{ t('sections.schedules.detailModal.traceTitle') }}</h4>
            <span class="pane-badge">{{ traceItems.length }}</span>
          </div>
          <div v-if="traceItems.length === 0" class="empty-block">
            {{ t('sections.schedules.detailModal.emptyTrace') }}
          </div>
          <div v-else class="trace-list">
            <article v-for="(item, index) in traceItems" :key="`${item.eventType}-${index}-${item.timestamp}`" class="trace-item">
              <div class="trace-top">
                <div>
                  <div class="trace-summary">{{ eventSummary(item) }}</div>
                  <div class="trace-event mono">{{ item.eventType }}</div>
                </div>
                <div class="trace-time">{{ formatTime(item.timestamp) }}</div>
              </div>
              <pre v-if="eventDetail(item)" class="trace-detail">{{ eventDetail(item) }}</pre>
            </article>
          </div>
        </section>

        <section class="pane">
          <div class="pane-head">
            <h4>{{ t('sections.schedules.detailModal.conversationTitle') }}</h4>
            <span v-if="detail?.session" class="pane-badge mono">{{ detail.session.id }}</span>
          </div>
          <div v-if="messages.length === 0" class="empty-block">
            {{ t('sections.schedules.detailModal.emptyMessages') }}
          </div>
          <div v-else class="conversation-list">
            <MessageItem
              v-for="message in messages"
              :key="message.id"
              :message="message"
            />
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.42);
  backdrop-filter: blur(6px);
}

.modal-content {
  width: min(1320px, calc(100vw - 48px));
  max-height: calc(100vh - 48px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: var(--border);
  border-radius: 20px;
  background: var(--color-white);
  box-shadow: 0 24px 80px rgba(15, 23, 42, 0.16);
}

.modal-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 22px 14px;
  border-bottom: var(--border);
}

.modal-title {
  margin: 0;
  font-family: var(--font-mono);
  font-size: 16px;
  font-weight: 600;
}

.modal-subtitle {
  margin: 6px 0 0;
  color: var(--color-gray-dark);
  font-size: 13px;
}

.close-btn {
  width: 32px;
  height: 32px;
  border: var(--border);
  border-radius: 10px;
  background: var(--color-white);
  cursor: pointer;
}

.close-btn:hover {
  background: var(--color-gray-bg);
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  padding: 14px 22px 0;
}

.meta-item {
  padding: 10px 12px;
  border: var(--border);
  border-radius: 12px;
  background: var(--color-gray-bg);
}

.meta-label {
  display: block;
  margin-bottom: 4px;
  color: var(--color-gray-dark);
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.meta-value {
  font-size: 13px;
}

.mono {
  font-family: var(--font-mono);
}

.state-block {
  padding: 28px 22px;
  color: var(--color-gray-dark);
}

.state-block.error {
  color: #dc2626;
}

.modal-body {
  display: grid;
  grid-template-columns: minmax(0, 0.95fr) minmax(0, 1.05fr);
  gap: 0;
  min-height: 0;
  flex: 1;
}

.pane {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.pane + .pane {
  border-left: var(--border);
}

.pane-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 18px;
  border-bottom: var(--border);
}

.pane-head h4 {
  margin: 0;
  font-family: var(--font-mono);
  font-size: 13px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.pane-badge {
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--color-gray-bg);
  color: var(--color-gray-dark);
  font-size: 11px;
}

.empty-block {
  padding: 18px;
  color: var(--color-gray-dark);
  font-size: 13px;
}

.trace-list,
.conversation-list {
  min-height: 0;
  overflow: auto;
}

.trace-item {
  padding: 14px 18px;
  border-bottom: var(--border-light);
}

.trace-top {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.trace-summary {
  font-size: 13px;
  font-weight: 600;
}

.trace-event {
  margin-top: 4px;
  color: var(--color-gray-dark);
  font-size: 11px;
}

.trace-time {
  color: var(--color-gray-dark);
  font-size: 11px;
  white-space: nowrap;
}

.trace-detail {
  margin: 10px 0 0;
  padding: 10px 12px;
  overflow: auto;
  border-radius: 12px;
  background: #0f172a;
  color: #e2e8f0;
  font-family: var(--font-mono);
  font-size: 11px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 1100px) {
  .modal-body {
    grid-template-columns: 1fr;
  }

  .pane + .pane {
    border-left: none;
    border-top: var(--border);
  }

  .meta-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 720px) {
  .modal-overlay {
    padding: 10px;
  }

  .modal-content {
    width: calc(100vw - 20px);
    max-height: calc(100vh - 20px);
  }

  .meta-grid {
    grid-template-columns: 1fr;
  }
}
</style>
