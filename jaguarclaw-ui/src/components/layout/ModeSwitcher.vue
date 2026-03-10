<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const currentMode = computed(() => {
  if (route.path.startsWith('/settings')) return 'settings'
  if (route.path.startsWith('/documents')) return 'documents'
  return 'workspace'
})

function switchTo(mode: 'workspace' | 'settings' | 'documents') {
  if (mode === 'workspace') router.push('/')
  else if (mode === 'settings') router.push('/settings')
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
</style>
