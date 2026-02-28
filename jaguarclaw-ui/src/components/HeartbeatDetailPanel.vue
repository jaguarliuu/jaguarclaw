<script setup lang="ts">
import { computed } from 'vue'
import type { HeartbeatNotification } from '@/composables/useHeartbeat'
import { useHeartbeat } from '@/composables/useHeartbeat'
import { useMarkdown } from '@/composables/useMarkdown'

defineProps<{
  notification: HeartbeatNotification
}>()

const { selectNotification } = useHeartbeat()
const { render } = useMarkdown()

function formatTime(dateStr: string): string {
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()

  if (diff < 86400000 && date.getDate() === now.getDate()) {
    return date.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })
  }
  if (diff < 604800000) {
    return date.toLocaleDateString('en-US', { weekday: 'short', hour: '2-digit', minute: '2-digit', hour12: false })
  }
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false })
}
</script>

<template>
  <aside class="heartbeat-panel">
    <div class="panel-header">
      <div class="panel-title-row">
        <svg width="14" height="14" viewBox="0 0 16 16" fill="none" class="panel-icon">
          <line x1="8" y1="1.5" x2="8" y2="3" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
          <path d="M4 11V8a4 4 0 0 0 8 0v3" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>
          <line x1="2" y1="11" x2="14" y2="11" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
          <path d="M6.5 13a1.5 1.5 0 0 0 3 0" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
        </svg>
        <span class="panel-title">Notification</span>
        <span class="panel-time">{{ formatTime(notification.createdAt) }}</span>
        <button class="panel-close" @click="selectNotification(null)" title="Close">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M3 3L11 11M11 3L3 11" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
        </button>
      </div>
    </div>

    <div class="panel-body">
      <div
        class="panel-text markdown-body"
        v-html="render(notification.content)"
      />
    </div>
  </aside>
</template>

<style scoped>
.heartbeat-panel {
  width: var(--detail-panel-width, 480px);
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--color-white);
  border-left: var(--border);
  flex-shrink: 0;
  position: relative;
  z-index: 51;
}

.panel-header {
  padding: 14px 20px;
  border-bottom: var(--border);
  flex-shrink: 0;
}

.panel-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.panel-icon {
  color: var(--color-gray-500);
  flex-shrink: 0;
}

.panel-title {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 600;
  color: var(--color-gray-800);
  flex: 1;
}

.panel-time {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-gray-400);
  flex-shrink: 0;
}

.panel-close {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: var(--border);
  background: var(--color-white);
  color: var(--color-gray-500);
  cursor: pointer;
  border-radius: var(--radius-md);
  transition: all 0.15s ease;
  flex-shrink: 0;
  padding: 0;
}

.panel-close:hover {
  background: var(--color-gray-bg);
  color: var(--color-black);
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}

.panel-text {
  font-size: 14px;
  line-height: 1.7;
  color: var(--color-gray-800);
}
</style>
