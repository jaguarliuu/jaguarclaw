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
  position: absolute;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  display: flex; align-items: center; gap: 12px;
  padding: 8px 16px;
  background: var(--color-gray-900);
  color: white;
  border-radius: var(--radius-full);
  box-shadow: var(--shadow-lg);
  font-size: 12px;
  white-space: nowrap;
  z-index: 50;
}
.ai-indicator__dot {
  width: 6px; height: 6px; border-radius: 50%;
  background: #4ade80;
  animation: pulse 1.2s ease-in-out infinite;
  flex-shrink: 0;
}
@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(0.85); }
}
.ai-indicator__label { color: rgba(255,255,255,0.85); }
.ai-indicator__actions { display: flex; gap: 6px; }
.ai-indicator__actions button {
  padding: 3px 10px; font-size: 11px; font-family: var(--font-ui);
  border-radius: var(--radius-full); cursor: pointer; font-weight: 500;
  transition: all var(--duration-fast) var(--ease-in-out);
}
.keep {
  background: white; color: var(--color-gray-900);
  border: 1.5px solid transparent;
}
.keep:hover { background: #f0f0f0; }
.discard {
  background: transparent; color: rgba(255,255,255,0.7);
  border: 1.5px solid rgba(255,255,255,0.25);
}
.discard:hover { border-color: rgba(255,255,255,0.5); color: white; }
</style>
