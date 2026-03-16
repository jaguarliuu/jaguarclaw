<script setup lang="ts">
import { ref, computed } from 'vue'
import { useIm } from '@/composables/useIm'
import { useI18n } from '@/i18n'
import type { ImContact, ImConversation } from '@/types'
import DiceBearAvatar from './DiceBearAvatar.vue'

const { t } = useI18n()

const props = defineProps<{
  contacts: ImContact[]
  conversations: ImConversation[]
  activeId: string | null
}>()

const emit = defineEmits<{
  select: [conversationId: string]
  'start-chat': [targetNodeId: string]
}>()

const { nodes, refreshNodes } = useIm()
const searchQuery = ref('')
const showDiscover = ref(false)
const discovering = ref(false)

const contactMap = computed(() => {
  const m: Record<string, ImContact> = {}
  for (const c of props.contacts) m[c.nodeId] = c
  return m
})

const filteredConversations = computed(() => {
  const q = searchQuery.value.toLowerCase()
  if (!q) return props.conversations
  return props.conversations.filter(c => c.displayName?.toLowerCase().includes(q))
})

async function discover() {
  discovering.value = true
  await refreshNodes()
  discovering.value = false
  showDiscover.value = true
}

function formatTime(ts: string | null): string {
  if (!ts) return ''
  const d = new Date(ts)
  const now = new Date()
  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  if (d.toDateString() === now.toDateString())
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  if (d.toDateString() === yesterday.toDateString()) return t('im.yesterday')
  return d.toLocaleDateString([], { month: 'short', day: 'numeric' })
}
</script>

<template>
  <aside class="sidebar">
    <!-- ── Header ── -->
    <div class="sidebar-head">
      <h2 class="sidebar-title">{{ t('im.messages') }}</h2>
      <button
        class="icon-pill"
        :class="{ active: showDiscover }"
        @click="showDiscover ? (showDiscover = false) : discover()"
        :title="t('im.discover')"
      >
        <svg v-if="!showDiscover" width="13" height="13" viewBox="0 0 13 13" fill="none">
          <line x1="6.5" y1="1" x2="6.5" y2="12" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
          <line x1="1" y1="6.5" x2="12" y2="6.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
        </svg>
        <svg v-else width="13" height="13" viewBox="0 0 13 13" fill="none">
          <line x1="1.5" y1="1.5" x2="11.5" y2="11.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
          <line x1="11.5" y1="1.5" x2="1.5" y2="11.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
        </svg>
      </button>
    </div>

    <!-- ── Search ── -->
    <div class="search-row">
      <svg class="search-ico" width="13" height="13" viewBox="0 0 13 13" fill="none">
        <circle cx="5.5" cy="5.5" r="4" stroke="currentColor" stroke-width="1.5"/>
        <line x1="8.7" y1="8.7" x2="12" y2="12" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
      </svg>
      <input v-model="searchQuery" class="search-input" :placeholder="t('im.search')" />
    </div>

    <!-- ── Discover Panel ── -->
    <Transition name="slide">
      <div v-if="showDiscover" class="discover-wrap">
        <div class="section-cap">{{ t('im.onlineNodes') }}</div>
        <div v-if="nodes.length === 0" class="discover-empty">{{ t('im.noNodesOnline') }}</div>
        <button
          v-for="node in nodes"
          :key="node.nodeId"
          class="conv-row"
          @click="emit('start-chat', node.nodeId); showDiscover = false"
        >
          <span class="avatar-wrap">
            <DiceBearAvatar :avatar-style="node.avatarStyle" :avatar-seed="node.avatarSeed || node.nodeId" :size="42" />
            <span class="online-ring"></span>
          </span>
          <span class="conv-meta">
            <span class="conv-name">{{ node.displayName }}</span>
            <span class="conv-sub online-label">{{ t('im.online') }}</span>
          </span>
        </button>
        <div class="sep" />
      </div>
    </Transition>

    <!-- ── Conversation list ── -->
    <div class="list-scroll">
      <button
        v-for="conv in filteredConversations"
        :key="conv.id"
        class="conv-row"
        :class="{ selected: conv.id === activeId }"
        @click="emit('select', conv.id)"
      >
        <span class="avatar-wrap">
            <DiceBearAvatar
              :avatar-style="contactMap[conv.id]?.avatarStyle"
              :avatar-seed="contactMap[conv.id]?.avatarSeed || conv.id"
              :size="42"
            />
          </span>
        <span class="conv-meta">
          <span class="conv-top-row">
            <span class="conv-name">{{ conv.displayName }}</span>
            <span class="conv-time">{{ formatTime(conv.lastMsgAt) }}</span>
          </span>
          <span class="conv-btm-row">
            <span class="conv-sub">{{ conv.lastMsg || '…' }}</span>
            <span v-if="conv.unreadCount > 0" class="badge">
              {{ conv.unreadCount > 99 ? '99+' : conv.unreadCount }}
            </span>
          </span>
        </span>
      </button>

      <!-- Empty state -->
      <div v-if="filteredConversations.length === 0 && !showDiscover" class="empty-pane">
        <div class="empty-bubble">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none">
            <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"
              stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <p class="empty-label">{{ t('im.noConversations') }}</p>
        <button class="empty-cta" @click="discover">{{ t('im.discover') }}</button>
      </div>
    </div>
  </aside>
</template>

<style scoped>
/* ── Shell ─────────────────────────────────────────────────────── */
.sidebar {
  width: 272px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--sidebar-panel-bg);
  border-right: var(--border);
  overflow: hidden;
}

/* ── Header ─────────────────────────────────────────────────────── */
.sidebar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 18px 12px;
}
.sidebar-title {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: -0.4px;
  color: var(--color-black);
}
.icon-pill {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-full);
  border: var(--border);
  background: var(--color-white);
  color: var(--color-gray-600);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background var(--duration-fast) var(--ease-in-out),
              color var(--duration-fast) var(--ease-in-out),
              border-color var(--duration-fast) var(--ease-in-out);
}
.icon-pill:hover,
.icon-pill.active {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-white);
}

/* ── Search ─────────────────────────────────────────────────────── */
.search-row {
  position: relative;
  margin: 0 14px 10px;
}
.search-ico {
  position: absolute;
  left: 10px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--color-gray-400);
  pointer-events: none;
}
.search-input {
  width: 100%;
  box-sizing: border-box;
  height: 32px;
  padding: 0 10px 0 30px;
  background: var(--color-gray-100);
  border: 1px solid transparent;
  border-radius: var(--radius-full);
  font-family: var(--font-ui);
  font-size: 13px;
  color: var(--color-black);
  outline: none;
  transition: border-color var(--duration-fast);
}
.search-input:focus { border-color: var(--color-primary); background: var(--color-white); }
.search-input::placeholder { color: var(--color-gray-400); }

/* ── Discover panel ─────────────────────────────────────────────── */
.discover-wrap { background: var(--sidebar-panel-bg); }
.section-cap {
  padding: 4px 18px 6px;
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--color-gray-400);
}
.discover-empty {
  padding: 6px 18px 10px;
  font-size: 13px;
  color: var(--color-gray-400);
}
.online-label { color: var(--color-success) !important; }
.online-ring {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--color-success);
  border: 2px solid var(--sidebar-panel-bg);
}
.sep {
  height: 1px;
  background: var(--color-gray-200);
  margin: 6px 14px;
}

.slide-enter-active, .slide-leave-active { transition: opacity 0.15s, transform 0.15s; }
.slide-enter-from, .slide-leave-to { opacity: 0; transform: translateY(-8px); }

/* ── List scroll ──────────────────────────────────────────────── */
.list-scroll { flex: 1; overflow-y: auto; padding: 4px 0; }

/* ── Conversation row ─────────────────────────────────────────── */
.conv-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 9px 14px;
  cursor: pointer;
  background: transparent;
  border: none;
  text-align: left;
  border-radius: var(--radius-lg);
  margin: 0 0;
  transition: background var(--duration-fast);
}
.conv-row:hover { background: var(--sidebar-item-hover-bg); }
.conv-row.selected {
  background: rgba(var(--color-primary-rgb), 0.08);
}

/* Avatar */
.avatar-wrap {
  width: 42px;
  height: 42px;
  min-width: 42px;
  border-radius: var(--radius-full);
  position: relative;
  overflow: hidden;
}

/* Meta block */
.conv-meta { flex: 1; min-width: 0; }
.conv-top-row, .conv-btm-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 6px;
}
.conv-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-black);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  letter-spacing: -0.1px;
}
.conv-time {
  font-size: 11px;
  color: var(--color-gray-400);
  white-space: nowrap;
  flex-shrink: 0;
}
.conv-sub {
  font-size: 12.5px;
  color: var(--color-gray-500);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  min-width: 0;
}
.badge {
  background: var(--color-primary);
  color: var(--color-white);
  border-radius: var(--radius-full);
  font-size: 11px;
  font-weight: 600;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

/* ── Empty state ──────────────────────────────────────────────── */
.empty-pane {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 56px 24px 24px;
  gap: 10px;
}
.empty-bubble {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-full);
  background: var(--color-gray-100);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-gray-400);
  margin-bottom: 4px;
}
.empty-label {
  font-size: 13px;
  color: var(--color-gray-500);
  text-align: center;
}
.empty-cta {
  margin-top: 2px;
  padding: 7px 18px;
  background: var(--color-primary);
  color: var(--color-white);
  border: none;
  border-radius: var(--radius-full);
  font-family: var(--font-ui);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity var(--duration-fast);
}
.empty-cta:hover { opacity: 0.8; }
</style>
