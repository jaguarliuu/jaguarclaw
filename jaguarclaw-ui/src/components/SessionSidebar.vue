<script setup lang="ts">
import type { Session, AgentProfile } from '@/types'
import { ref, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useConfirm } from '@/composables/useConfirm'
import { useI18n } from '@/i18n'
import { useHeartbeat } from '@/composables/useHeartbeat'
import { useWebSocket } from '@/composables/useWebSocket'

const { confirm } = useConfirm()
const { t } = useI18n()
const { notifications, unreadCount, markAsRead, markAllAsRead, clearAll, selectNotification } =
  useHeartbeat()
const { state: connectionState } = useWebSocket()
const route = useRoute()
const router = useRouter()

const activeSection = computed(() => {
  if (route.path.startsWith('/documents')) return 'documents'
  if (route.path.startsWith('/settings')) return 'settings'
  return 'chat'
})

function navTo(section: 'chat' | 'documents') {
  if (section === 'chat') {
    router.push('/')
    collapsed.value = false
  } else {
    router.push('/documents')
  }
}

const props = defineProps<{
  sessions: Session[]
  currentId: string | null
  agents?: AgentProfile[]
  forceCollapsed?: boolean
}>()

const emit = defineEmits<{
  select: [id: string]
  create: []
  delete: [id: string]
}>()

const collapsed = ref(localStorage.getItem('sidebar-collapsed') === 'true')
const notificationsOpen = ref(false)

const agentLabelMap = computed(() => {
  const map = new Map<string, string>()
  for (const agent of props.agents ?? []) {
    map.set(agent.id, agent.displayName || agent.name || agent.id)
  }
  return map
})

watch(collapsed, (v) => localStorage.setItem('sidebar-collapsed', String(v)))

function toggleNotifications() {
  if (collapsed.value) {
    collapsed.value = false
    notificationsOpen.value = true
  } else {
    notificationsOpen.value = !notificationsOpen.value
  }
}

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()

  if (diff < 86400000 && date.getDate() === now.getDate()) {
    return date.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })
  }
  if (diff < 604800000) {
    return date.toLocaleDateString('en-US', { weekday: 'short' })
  }
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

async function handleDelete(e: Event, sessionId: string) {
  e.stopPropagation()
  const confirmed = await confirm({
    title: t('session.deleteTitle'),
    message: t('session.deleteMessage'),
    confirmText: t('common.delete'),
    cancelText: t('common.cancel'),
    danger: true,
  })
  if (confirmed) {
    emit('delete', sessionId)
  }
}

function sessionAgentLabel(session: Session): string {
  const id = session.agentId || 'main'
  return agentLabelMap.value.get(id) ?? id
}
</script>

<template>
  <aside class="sidebar-root">
    <!-- Icon Rail (56px) -->
    <div class="app-rail">
      <!-- Logo -->
      <div class="rail-top">
        <div class="rail-logo" :class="`conn-${connectionState}`">M</div>
      </div>

      <!-- Primary nav icons -->
      <div class="rail-nav">
        <!-- Chat -->
        <button
          class="rail-btn"
          :class="{ 'nav-active': activeSection === 'chat' }"
          @click="navTo('chat')"
          title="会话"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
            <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"
              stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>

        <!-- Documents -->
        <button
          class="rail-btn"
          :class="{ 'nav-active': activeSection === 'documents' }"
          @click="navTo('documents')"
          title="文档"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
            <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"
              stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            <polyline points="14 2 14 8 20 8" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            <line x1="16" y1="13" x2="8" y2="13" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
            <line x1="16" y1="17" x2="8" y2="17" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
        </button>
      </div>

      <!-- Bottom utilities -->
      <div class="rail-bottom">
        <!-- Notification Bell -->
        <button
          class="rail-btn notif-trigger"
          :class="{ active: notificationsOpen }"
          @click="toggleNotifications"
          :title="t('session.notifications')"
        >
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
            <line x1="8" y1="1.5" x2="8" y2="3" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
            <path d="M4 11V8a4 4 0 0 0 8 0v3" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>
            <line x1="2" y1="11" x2="14" y2="11" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
            <path d="M6.5 13a1.5 1.5 0 0 0 3 0" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
          </svg>
          <span v-if="unreadCount > 0" class="notif-badge">{{ unreadCount > 9 ? '9+' : unreadCount }}</span>
        </button>

        <RouterLink to="/settings/llm" class="rail-btn" :class="{ 'nav-active': activeSection === 'settings' }" :title="t('common.settings')">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
            <path d="M12.22 2h-.44a2 2 0 00-2 2v.18a2 2 0 01-1 1.73l-.43.25a2 2 0 01-2 0l-.15-.08a2 2 0 00-2.73.73l-.22.38a2 2 0 00.73 2.73l.15.1a2 2 0 011 1.72v.51a2 2 0 01-1 1.74l-.15.09a2 2 0 00-.73 2.73l.22.38a2 2 0 002.73.73l.15-.08a2 2 0 012 0l.43.25a2 2 0 011 1.73V20a2 2 0 002 2h.44a2 2 0 002-2v-.18a2 2 0 011-1.73l.43-.25a2 2 0 012 0l.15.08a2 2 0 002.73-.73l.22-.39a2 2 0 00-.73-2.73l-.15-.08a2 2 0 01-1-1.74v-.5a2 2 0 011-1.74l.15-.09a2 2 0 00.73-2.73l-.22-.38a2 2 0 00-2.73-.73l-.15.08a2 2 0 01-2 0l-.43-.25a2 2 0 01-1-1.73V4a2 2 0 00-2-2z"
              stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="1.5"/>
          </svg>
        </RouterLink>
      </div>
    </div>

    <!-- Session / Notification Panel (220px, collapsible) -->
    <div class="session-panel" :class="{ collapsed: collapsed || forceCollapsed }">
      <!-- Session mode header -->
      <div class="panel-header" v-if="!notificationsOpen">
        <button class="new-session-btn" @click="emit('create')" :title="t('session.new')">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path
              d="M7 2V12M2 7H12"
              stroke="currentColor"
              stroke-width="1.5"
              stroke-linecap="round"
            />
          </svg>
          {{ t('session.new') }}
        </button>
      </div>

      <!-- Notification mode header -->
      <div class="panel-header notif-header" v-else>
        <span class="notif-title">{{ t('session.notifications') }}</span>
        <div class="notif-header-actions">
          <button
            v-if="unreadCount > 0"
            class="notif-action-btn"
            @click="markAllAsRead"
            :title="t('session.markAllRead')"
          >
            {{ t('session.markAllRead') }}
          </button>
          <button
            v-if="notifications.length > 0"
            class="notif-action-btn notif-clear-btn"
            @click="clearAll"
            :title="t('session.clearAll')"
          >
            <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
              <path
                d="M2 2L10 10M10 2L2 10"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
              />
            </svg>
          </button>
        </div>
      </div>

      <!-- Session list -->
      <nav class="session-list" v-if="!notificationsOpen">
        <div v-if="sessions.length === 0" class="empty-state">
          {{ t('session.empty') }}
        </div>
        <div
          v-for="session in sessions"
          :key="session.id"
          class="session-item"
          :class="{ active: session.id === currentId }"
          @click="emit('select', session.id)"
        >
          <div class="session-content">
            <span class="session-title">{{ session.name || t('session.untitled') }}</span>
            <span class="session-agent">{{ sessionAgentLabel(session) }}</span>
            <span class="session-date">{{ formatDate(session.createdAt) }}</span>
          </div>
          <button
            class="delete-btn"
            @click="(e) => handleDelete(e, session.id)"
            :title="t('common.delete')"
          >
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <path
                d="M3 3L11 11M11 3L3 11"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
              />
            </svg>
          </button>
        </div>
      </nav>

      <!-- Notification list -->
      <div class="notif-list" v-else>
        <div v-if="notifications.length === 0" class="empty-state">
          {{ t('session.noNotifications') }}
        </div>
        <div
          v-for="n in notifications"
          :key="n.id"
          class="notif-item"
          :class="{ unread: !n.read }"
          @click="selectNotification(n.id)"
        >
          <div class="notif-dot" v-if="!n.read"></div>
          <div class="notif-dot notif-dot-read" v-else></div>
          <div class="notif-body">
            <div class="notif-content">{{ n.content }}</div>
            <div class="notif-time">{{ formatDate(n.createdAt) }}</div>
          </div>
        </div>
      </div>

      <div class="panel-footer">
        <button class="collapse-toggle" @click="collapsed = true" :title="t('session.collapse')">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path
              d="M9 2L5 7L9 12"
              stroke="currentColor"
              stroke-width="1.5"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </button>
      </div>
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
  border-right: 1px solid var(--sidebar-panel-border);
  background: var(--sidebar-rail-bg);
  padding: 10px 0;
}

.rail-top {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-bottom: 4px;
}

.rail-nav {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 4px 0;
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
  color: var(--sidebar-rail-logo-fg);
  margin-bottom: 6px;
  border-radius: var(--radius-md);
  transition: box-shadow 0.6s ease;
}

.rail-logo.conn-connected {
  box-shadow:
    0 0 0 2.5px rgba(var(--color-primary-rgb), 0.2),
    0 0 10px 3px rgba(var(--color-primary-rgb), 0.1);
}

.rail-logo.conn-disconnected,
.rail-logo.conn-error {
  box-shadow:
    0 0 0 2.5px rgba(212, 64, 64, 0.3),
    0 0 10px 3px rgba(212, 64, 64, 0.18);
}

.rail-logo.conn-connecting {
  box-shadow: none;
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
  color: var(--sidebar-rail-fg);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-in-out);
  text-decoration: none;
}

.rail-btn:hover {
  background: var(--sidebar-rail-hover-bg);
  color: var(--sidebar-rail-logo-fg);
}

.rail-btn.nav-active {
  background: var(--sidebar-rail-hover-bg);
  color: var(--sidebar-rail-logo-fg);
  position: relative;
}

.rail-btn.nav-active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 20px;
  background: var(--sidebar-rail-logo-fg);
  border-radius: 0 2px 2px 0;
}

.rail-bottom {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding-bottom: 4px;
}

/* Notification trigger button */
.notif-trigger {
  position: relative;
}

.notif-trigger.active {
  background: var(--sidebar-rail-hover-bg);
  color: var(--sidebar-rail-logo-fg);
}

.notif-badge {
  position: absolute;
  top: 3px;
  right: 3px;
  min-width: 14px;
  height: 14px;
  padding: 0 3px;
  background: var(--color-error, #ef4444);
  color: #fff;
  border-radius: 7px;
  font-size: 9px;
  font-family: var(--font-mono);
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
  pointer-events: none;
}

/* Session Panel */
.session-panel {
  width: 220px;
  height: 100%;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--sidebar-panel-border);
  background: var(--sidebar-panel-bg);
  overflow: hidden;
  transition:
    width 0.25s var(--ease-in-out),
    opacity 0.2s var(--ease-in-out);
}

.session-panel.collapsed {
  width: 0;
  opacity: 0;
  border-right: none;
  pointer-events: none;
}

.panel-header {
  padding: 10px 8px 4px;
  flex-shrink: 0;
}

/* Notification header */
.notif-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 12px 8px;
  border-bottom: 1px solid var(--sidebar-panel-border);
}

.notif-title {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-gray-500);
}

.notif-header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.notif-action-btn {
  padding: 2px 6px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-gray-500);
  font-family: var(--font-ui);
  font-size: 11px;
  cursor: pointer;
  transition: all var(--duration-fast);
  display: flex;
  align-items: center;
}

.notif-action-btn:hover {
  background: var(--sidebar-item-hover-bg);
  color: var(--color-gray-700);
}

.notif-clear-btn {
  padding: 3px;
}

/* Session list */
.new-session-btn {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid var(--sidebar-panel-border);
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-primary);
  font-family: var(--font-ui);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-in-out);
}

.new-session-btn:hover {
  background: var(--sidebar-item-hover-bg);
  border-color: var(--color-primary);
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
  background: var(--sidebar-item-hover-bg);
}

.session-item.active {
  background: var(--color-primary);
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

.session-agent {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  max-width: 100%;
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  background: rgba(0, 0, 0, 0.05);
  font-family: var(--font-mono);
  font-size: 10px;
  line-height: 1.3;
  color: var(--color-gray-600);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-date {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-gray-400);
}

.session-item.active .session-agent {
  background: rgba(255, 255, 255, 0.18);
  color: rgba(255, 255, 255, 0.88);
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
  background: rgba(0, 0, 0, 0.08);
  color: var(--color-error);
}

.session-item.active .delete-btn {
  color: rgba(255, 255, 255, 0.6);
}

.session-item.active .delete-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #ffaaaa;
}

/* Notification list */
.notif-list {
  flex: 1;
  overflow-y: auto;
  padding: 6px 8px;
}

.notif-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 10px;
  border-radius: var(--radius-md);
  cursor: pointer;
  margin-bottom: 2px;
  transition: background var(--duration-fast);
}

.notif-item:hover {
  background: var(--sidebar-item-hover-bg);
}

.notif-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-primary);
  flex-shrink: 0;
  margin-top: 5px;
}

.notif-dot-read {
  background: transparent;
}

.notif-body {
  flex: 1;
  min-width: 0;
}

.notif-content {
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-gray-600);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.notif-item.unread .notif-content {
  color: var(--color-gray-900, var(--color-black));
  font-weight: 500;
}

.notif-time {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--color-gray-400);
  margin-top: 4px;
}

/* Footer */
.panel-footer {
  padding: 8px;
  border-top: 1px solid var(--sidebar-panel-border);
  flex-shrink: 0;
}

.collapse-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  padding: 7px 0;
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-gray-400);
  cursor: pointer;
  transition:
    background var(--duration-fast) var(--ease-in-out),
    color var(--duration-fast) var(--ease-in-out);
}

.collapse-toggle:hover {
  background: var(--sidebar-item-hover-bg);
  color: var(--color-gray-600);
}
</style>
