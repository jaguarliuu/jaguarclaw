<!-- src/components/documents/DocumentAiIndicator.vue -->
<script setup lang="ts">
defineProps<{ streaming: boolean }>()
defineEmits<{ keep: []; discard: [] }>()
</script>

<template>
  <div class="ai-indicator">
    <span class="ai-indicator__label">
      <span v-if="streaming" class="ai-indicator__dot" />
      <svg v-else width="14" height="14" viewBox="0 0 14 14" fill="none" class="ai-indicator__check">
        <circle cx="7" cy="7" r="6" fill="#4ade80" opacity="0.2"/>
        <path d="M4 7l2 2 4-4" stroke="#4ade80" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
      {{ streaming ? 'AI 正在写作' : '完成' }}
    </span>
    <div class="ai-indicator__actions">
      <button class="keep" :disabled="streaming" @click="$emit('keep')">&#x2713; 保留</button>
      <button class="discard" @click="$emit('discard')">&#x2715; 撤销</button>
    </div>
  </div>
</template>

<style scoped>
.ai-indicator {
  position: fixed;
  bottom: 40px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px 8px 16px;
  background: rgba(17, 17, 17, 0.88);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  color: white;
  border-radius: 100px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.24), 0 2px 8px rgba(0, 0, 0, 0.12);
  font-size: 12.5px;
  white-space: nowrap;
  z-index: 200;
  animation: slideUp 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateX(-50%) translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
  }
}

.ai-indicator__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #4ade80;
  animation: pulse 1.2s ease-in-out infinite;
  flex-shrink: 0;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.45; transform: scale(0.8); }
}

.ai-indicator__check {
  flex-shrink: 0;
}

.ai-indicator__label {
  display: flex;
  align-items: center;
  gap: 7px;
  color: rgba(255, 255, 255, 0.88);
  font-weight: 500;
}

.ai-indicator__actions {
  display: flex;
  gap: 5px;
  margin-left: 4px;
}

.ai-indicator__actions button {
  padding: 4px 11px;
  font-size: 11.5px;
  font-family: var(--font-ui);
  font-weight: 500;
  border-radius: 100px;
  cursor: pointer;
  transition: all 100ms ease;
  line-height: 1;
}

.keep {
  background: white;
  color: #111;
  border: 1.5px solid transparent;
}

.keep:hover:not(:disabled) {
  background: #f0f0f0;
}

.keep:disabled {
  opacity: 0.4;
  cursor: default;
  pointer-events: none;
}

.discard {
  background: transparent;
  color: rgba(255, 255, 255, 0.75);
  border: 1.5px solid rgba(255, 255, 255, 0.2);
}

.discard:hover {
  border-color: rgba(255, 255, 255, 0.5);
  color: white;
}
</style>
