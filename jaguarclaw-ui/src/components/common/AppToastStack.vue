<script setup lang="ts">
import { useToast } from '@/composables/useToast'

const { toasts, dismissToast } = useToast()
</script>

<template>
  <div class="toast-stack" aria-live="polite" aria-atomic="true">
    <div
      v-for="toast in toasts"
      :key="toast.id"
      class="toast"
      :class="`toast-${toast.type}`"
    >
      <div class="toast-body">
        <div v-if="toast.title" class="toast-title">{{ toast.title }}</div>
        <div class="toast-message">{{ toast.message }}</div>
      </div>
      <button class="toast-close" @click="dismissToast(toast.id)" aria-label="Dismiss">×</button>
    </div>
  </div>
</template>

<style scoped>
.toast-stack {
  position: fixed;
  top: 16px;
  right: 16px;
  z-index: 4000;
  display: flex;
  flex-direction: column;
  gap: 10px;
  pointer-events: none;
}

.toast {
  min-width: 260px;
  max-width: 420px;
  border-radius: 8px;
  border: 1px solid var(--color-gray-200);
  background: var(--color-white);
  box-shadow: var(--shadow-md);
  padding: 10px 12px;
  pointer-events: auto;
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.toast-body { flex: 1; min-width: 0; }

.toast-close {
  flex-shrink: 0;
  width: 20px; height: 20px;
  border: none; background: none;
  color: var(--color-gray-400);
  font-size: 16px; line-height: 1;
  cursor: pointer; padding: 0;
  display: flex; align-items: center; justify-content: center;
  border-radius: var(--radius-sm);
  transition: color var(--duration-fast), background var(--duration-fast);
}
.toast-close:hover { color: var(--color-black); background: var(--color-gray-100); }

.toast-title {
  font-weight: 600;
  margin-bottom: 4px;
}

.toast-message {
  font-size: 13px;
  color: var(--color-gray-700);
}

.toast-info {
  border-left: 4px solid var(--color-info);
}

.toast-success {
  border-left: 4px solid var(--color-success);
}

.toast-warning {
  border-left: 4px solid var(--color-warning);
}

.toast-error {
  border-left: 4px solid var(--color-error);
}
</style>

