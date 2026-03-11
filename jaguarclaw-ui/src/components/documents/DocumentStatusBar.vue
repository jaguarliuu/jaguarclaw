<!-- src/components/documents/DocumentStatusBar.vue -->
<script setup lang="ts">
defineProps<{ statusText: string }>()
defineEmits<{ stop: [] }>()
</script>

<template>
  <div class="doc-status-bar">
    <div class="doc-status-bar__left">
      <span class="doc-status-bar__dot" />
      <span class="doc-status-bar__text">{{ statusText || 'AI 正在写作…' }}</span>
    </div>
    <button class="doc-status-bar__stop" @click="$emit('stop')">■ 停止</button>
  </div>
</template>

<style scoped>
.doc-status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 20px;
  background: var(--color-gray-50);
  border-bottom: var(--border);
  flex-shrink: 0;
  gap: 12px;
}
.doc-status-bar__left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.doc-status-bar__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #4ade80;
  animation: pulse 1.2s ease-in-out infinite;
  flex-shrink: 0;
}
@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(0.85); }
}
.doc-status-bar__text {
  font-size: 12px;
  color: var(--color-gray-600);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 480px;
  font-family: var(--font-ui);
}
.doc-status-bar__stop {
  padding: 3px 10px;
  font-size: 11px;
  font-family: var(--font-ui);
  border: var(--border);
  border-radius: var(--radius-full);
  background: var(--color-white);
  color: var(--color-gray-700);
  cursor: pointer;
  flex-shrink: 0;
}
.doc-status-bar__stop:hover {
  background: var(--color-gray-100);
  border-color: var(--color-gray-300);
}
</style>
