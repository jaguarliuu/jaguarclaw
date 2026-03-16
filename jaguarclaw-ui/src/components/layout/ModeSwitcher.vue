<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useIm } from '@/composables/useIm'

const route = useRoute()
const router = useRouter()
const { totalUnreadCount } = useIm()

const currentMode = computed(() => {
  if (route.path.startsWith('/settings')) return 'settings'
  if (route.path.startsWith('/documents')) return 'documents'
  if (route.path.startsWith('/im')) return 'im'
  return 'workspace'
})

function switchTo(mode: string) {
  if (mode === 'workspace') router.push('/')
  else if (mode === 'settings') router.push('/settings')
  else if (mode === 'im') router.push('/im')
  else router.push('/documents')
}
</script>

<template>
  <div class="mode-switcher">
    <button
      class="mode-btn"
      :class="{ active: currentMode === 'workspace' }"
      @click="switchTo('workspace')"
      title="Workspace"
    >
      <span class="icon">&#9671;</span>
    </button>
    <button class="mode-btn" :class="{ active: currentMode === 'documents' }"
            @click="switchTo('documents')" title="Documents">
      <span class="icon">&#9783;</span>
    </button>
    <button class="mode-btn im-btn" :class="{ active: currentMode === 'im' }"
            @click="switchTo('im')" title="IM">
      <svg width="14" height="14" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round">
        <path d="M2 3a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v7a1 1 0 0 1-1 1H5.5L2 14V3z"/>
      </svg>
      <span v-if="totalUnreadCount > 0 && currentMode !== 'im'" class="unread-badge">
        {{ totalUnreadCount > 99 ? '99+' : totalUnreadCount }}
      </span>
    </button>
    <button
      class="mode-btn"
      :class="{ active: currentMode === 'settings' }"
      @click="switchTo('settings')"
      title="Settings"
    >
      <span class="icon">&#9881;</span>
    </button>
  </div>
</template>

<style scoped>
.mode-switcher {
  display: flex;
  gap: 4px;
}

.mode-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: var(--border);
  background: var(--color-white);
  font-size: 14px;
  cursor: pointer;
  border-radius: var(--radius-md);
  transition: all var(--duration-fast) var(--ease-in-out);
}

.mode-btn:hover {
  background: var(--color-gray-bg);
}

.mode-btn.active {
  background: var(--color-black);
  color: var(--color-white);
}

.icon {
  line-height: 1;
}

.im-btn {
  position: relative;
}

.unread-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: #ef4444;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
  pointer-events: none;
}
</style>
