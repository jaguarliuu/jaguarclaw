<script setup lang="ts">
import { ref } from 'vue'
import { useIm } from '@/composables/useIm'
import type { ImContact, ImConversation } from '@/types'

const props = defineProps<{
  contacts: ImContact[]
  conversations: ImConversation[]
  activeId: string | null
}>()

const emit = defineEmits<{
  select: [conversationId: string]
  'pair-request': [targetNodeId: string]
}>()

const { nodes, refreshNodes } = useIm()
const showDiscover = ref(false)
const discovering = ref(false)

async function discover() {
  discovering.value = true
  await refreshNodes()
  discovering.value = false
  showDiscover.value = true
}
</script>

<template>
  <div class="contact-list">
    <div class="contact-header">
      <span class="title">Messages</span>
      <button class="discover-btn" @click="discover" :disabled="discovering" title="Find contacts">
        +
      </button>
    </div>

    <!-- Existing conversations -->
    <div
      v-for="conv in conversations"
      :key="conv.id"
      class="conv-item"
      :class="{ active: conv.id === activeId }"
      @click="emit('select', conv.id)"
    >
      <div class="conv-avatar">{{ conv.displayName?.[0]?.toUpperCase() ?? '?' }}</div>
      <div class="conv-info">
        <div class="conv-name">{{ conv.displayName }}</div>
        <div class="conv-last">{{ conv.lastMsg }}</div>
      </div>
      <div v-if="conv.unreadCount > 0" class="unread-badge">{{ conv.unreadCount }}</div>
    </div>

    <!-- Discover panel -->
    <div v-if="showDiscover" class="discover-panel">
      <div class="discover-title">Online nodes</div>
      <div v-if="nodes.length === 0" class="discover-empty">No other nodes online</div>
      <div
        v-for="node in nodes"
        :key="node.nodeId"
        class="node-item"
        @click="emit('pair-request', node.nodeId); showDiscover = false"
      >
        <div class="conv-avatar">{{ node.displayName?.[0]?.toUpperCase() ?? '?' }}</div>
        <div class="conv-info">
          <div class="conv-name">{{ node.displayName }}</div>
          <div class="conv-last">{{ node.nodeId.slice(0, 8) }}…</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.contact-list {
  width: 260px;
  border-right: var(--border);
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  background: var(--sidebar-panel-bg);
}
.contact-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: var(--border);
  font-weight: 600;
  font-size: 14px;
}
.discover-btn {
  width: 24px; height: 24px;
  border: var(--border);
  background: var(--color-white);
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
}
.conv-item, .node-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  cursor: pointer;
  border-bottom: var(--border-light);
}
.conv-item:hover, .node-item:hover { background: var(--sidebar-item-hover-bg); }
.conv-item.active { background: var(--color-gray-100); }
.conv-avatar {
  width: 36px; height: 36px;
  border-radius: var(--radius-full);
  background: var(--color-primary);
  color: white;
  display: flex; align-items: center; justify-content: center;
  font-weight: 600; font-size: 14px;
  flex-shrink: 0;
}
.conv-info { flex: 1; min-width: 0; }
.conv-name { font-size: 13px; font-weight: 600; }
.conv-last { font-size: 12px; color: var(--color-gray-500); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.unread-badge {
  background: var(--color-primary);
  color: white;
  border-radius: var(--radius-full);
  font-size: 11px;
  min-width: 18px; height: 18px;
  display: flex; align-items: center; justify-content: center;
  padding: 0 4px;
}
.discover-panel { border-top: var(--border); padding: 8px 0; }
.discover-title { padding: 4px 16px; font-size: 11px; color: var(--color-gray-500); text-transform: uppercase; letter-spacing: 0.05em; }
.discover-empty { padding: 8px 16px; font-size: 13px; color: var(--color-gray-400); }
</style>
