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
      @click="dismissToast(toast.id)"
    >
      <div v-if="toast.title" class="toast-title">{{ toast.title }}</div>
      <div class="toast-message">{{ toast.message }}</div>
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
  cursor: pointer;
  pointer-events: auto;
}

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

