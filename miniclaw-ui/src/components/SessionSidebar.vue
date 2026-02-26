<script setup lang="ts">
import type { Session } from '@/types'
import { useConfirm } from '@/composables/useConfirm'

const { confirm } = useConfirm()

defineProps<{
  sessions: Session[]
  currentId: string | null
}>()

const emit = defineEmits<{
  select: [id: string]
  create: []
  delete: [id: string]
}>()

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()

  // Today
  if (diff < 86400000 && date.getDate() === now.getDate()) {
    return date.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })
  }

  // This week
  if (diff < 604800000) {
    return date.toLocaleDateString('en-US', { weekday: 'short' })
  }

  // Older
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

async function handleDelete(e: Event, sessionId: string) {
  e.stopPropagation()
  const confirmed = await confirm({
    title: 'Delete Session',
    message: 'This will permanently delete the session and all its messages. This action cannot be undone.',
    confirmText: 'Delete',
    cancelText: 'Cancel',
    danger: true
  })
  if (confirmed) {
    emit('delete', sessionId)
  }
}
</script>

<template>
  <aside class="sidebar-root">
    <!-- Icon Rail (56px) -->
    <div class="app-rail">
      <div class="rail-top">
        <div class="rail-logo">M</div>
      </div>
      <div class="rail-bottom">
        <RouterLink to="/settings/llm" class="rail-btn" title="Settings">
          <svg width="18" height="18" viewBox="0 0 18 18" fill="none">
            <circle cx="9" cy="9" r="2.5" stroke="currentColor" stroke-width="1.4"/>
            <path d="M9 1.5v2M9 14.5v2M1.5 9h2M14.5 9h2M3.4 3.4l1.42 1.42M13.18 13.18l1.42 1.42M14.6 3.4l-1.42 1.42M4.82 13.18L3.4 14.6" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
          </svg>
        </RouterLink>
        <div class="rail-status">
          <slot name="footer"></slot>
        </div>
      </div>
    </div>

    <!-- Session Panel (220px) -->
    <div class="session-panel">
      <div class="panel-header">
        <button class="new-session-btn" @click="emit('create')" title="New session">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M7 2V12M2 7H12" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
          New chat
        </button>
      </div>
      <nav class="session-list">
        <div v-if="sessions.length === 0" class="empty-state">
          No sessions yet
        </div>

        <div
          v-for="session in sessions"
          :key="session.id"
          class="session-item"
          :class="{ active: session.id === currentId }"
          @click="emit('select', session.id)"
        >
          <div class="session-content">
            <span class="session-title">{{ session.name || 'Untitled' }}</span>
            <span class="session-date">{{ formatDate(session.createdAt) }}</span>
          </div>
          <button class="delete-btn" @click="(e) => handleDelete(e, session.id)" title="Delete session">
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <path d="M3 3L11 11M11 3L3 11" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
            </svg>
          </button>
        </div>
      </nav>
    </div>
  </aside>
</template>

<style scoped>
.sidebar-root {
  display: flex;
  height: 100%;
  flex-shrink: 0;
}

/* Icon Rail */
.app-rail {
  width: 56px;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  border-right: var(--border);
  background: var(--color-white);
  padding: 10px 0;
}

.rail-top {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.rail-logo {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-mono);
  font-size: 17px;
  font-weight: 700;
  letter-spacing: -0.03em;
  color: var(--color-black);
  margin-bottom: 6px;
}

.rail-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-gray-500);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-in-out);
  text-decoration: none;
}

.rail-btn:hover {
  background: var(--color-gray-100);
  color: var(--color-black);
}

.rail-bottom {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding-bottom: 4px;
}

.rail-status {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
}

/* Session Panel */
.session-panel {
  width: 220px;
  height: 100%;
  display: flex;
  flex-direction: column;
  border-right: var(--border);
  background: var(--color-white);
}

.panel-header {
  padding: 10px 8px 4px;
}

.new-session-btn {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid var(--color-gray-200);
  border-radius: var(--radius-md);
  background: var(--color-white);
  color: var(--color-gray-600);
  font-family: var(--font-ui);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-in-out);
}

.new-session-btn:hover {
  background: var(--color-gray-50);
  border-color: var(--color-gray-300);
  color: var(--color-black);
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.empty-state {
  padding: 24px 12px;
  font-size: 13px;
  color: var(--color-gray-400);
  text-align: center;
}

.session-item {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 10px 12px;
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  text-align: left;
  cursor: pointer;
  margin-bottom: 2px;
  transition: background var(--duration-fast) var(--ease-in-out);
}

.session-item:hover {
  background: var(--color-gray-50);
}

.session-item.active {
  background: var(--color-black);
  color: var(--color-white);
  box-shadow: var(--shadow-sm);
}

.session-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.session-title {
  font-size: 13px;
  font-weight: 500;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-date {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-gray-400);
}

.session-item.active .session-date {
  color: rgba(255, 255, 255, 0.5);
}

.delete-btn {
  opacity: 0;
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: var(--color-gray-400);
  cursor: pointer;
  border-radius: var(--radius-sm);
  transition: all var(--duration-fast) var(--ease-in-out);
  flex-shrink: 0;
  padding: 0;
}

.session-item:hover .delete-btn {
  opacity: 1;
}

.delete-btn:hover {
  background: var(--color-gray-100);
  color: var(--color-error);
}

.session-item.active .delete-btn {
  color: rgba(255, 255, 255, 0.6);
}

.session-item.active .delete-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #ffaaaa;
}
</style>
