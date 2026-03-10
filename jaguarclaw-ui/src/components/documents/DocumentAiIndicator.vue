<!-- src/components/documents/DocumentAiIndicator.vue -->
<script setup lang="ts">
defineProps<{ streaming: boolean }>()
defineEmits<{ keep: []; discard: [] }>()
</script>

<template>
  <div class="ai-indicator">
    <span class="ai-indicator__label">
      <span v-if="streaming" class="ai-indicator__dot" />
      {{ streaming ? 'AI 正在写作…' : 'AI 写作完成' }}
    </span>
    <div v-if="!streaming" class="ai-indicator__actions">
      <button class="keep" @click="$emit('keep')">✓ 保留</button>
      <button class="discard" @click="$emit('discard')">↩ 撤销</button>
    </div>
  </div>
</template>

<style scoped>
.ai-indicator {
  display: flex; align-items: center; justify-content: space-between;
  padding: var(--space-2) var(--space-4);
  background: var(--color-gray-50); border-top: var(--border);
  font-size: 12px; color: var(--color-gray-600);
  flex-shrink: 0;
}
.ai-indicator__label { display: flex; align-items: center; gap: var(--space-2); }
.ai-indicator__dot {
  width: 6px; height: 6px; border-radius: 50%;
  background: var(--color-primary, #4f46e5);
  animation: pulse 1s infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}
.ai-indicator__actions { display: flex; gap: var(--space-2); }
.ai-indicator__actions button {
  padding: var(--space-1) var(--space-3); font-size: 12px;
  border-radius: var(--radius-md); border: var(--border); cursor: pointer;
  font-family: var(--font-ui);
}
.keep { background: var(--color-gray-900); color: white; border-color: transparent; }
.discard { background: white; color: var(--color-gray-700); }
</style>
