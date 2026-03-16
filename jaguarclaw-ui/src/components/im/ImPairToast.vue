<script setup lang="ts">
import { useI18n } from '@/i18n'
import type { ImPairRequestEvent } from '@/types'

const { t } = useI18n()
defineProps<{ request: ImPairRequestEvent; index: number }>()
const emit = defineEmits<{ accept: []; reject: [] }>()
</script>

<template>
  <div class="pair-toast" :style="{ top: `${16 + index * 92}px` }">
    <div class="toast-avatar">{{ request.fromDisplayName?.[0]?.toUpperCase() ?? '?' }}</div>
    <div class="toast-body">
      <div class="toast-title">
        <strong>{{ request.fromDisplayName }}</strong>
        <span class="toast-subtitle">{{ t('im.pairRequestWants') }}</span>
      </div>
      <div class="toast-node">{{ request.fromNodeId.slice(0, 12) }}…</div>
      <div class="toast-actions">
        <button class="btn-accept" @click="emit('accept')">{{ t('im.accept') }}</button>
        <button class="btn-reject" @click="emit('reject')">{{ t('im.reject') }}</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pair-toast {
  position: fixed;
  right: 20px;
  z-index: 9999;
  background: var(--color-white);
  border: var(--border);
  border-radius: 14px;
  padding: 14px 16px;
  box-shadow: var(--shadow-lg);
  display: flex;
  align-items: flex-start;
  gap: 12px;
  width: 300px;
  animation: toast-in 0.2s ease;
}
@keyframes toast-in {
  from { opacity: 0; transform: translateX(20px); }
  to   { opacity: 1; transform: translateX(0); }
}

.toast-avatar {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-full);
  background: var(--color-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 600;
  flex-shrink: 0;
}

.toast-body { flex: 1; min-width: 0; }
.toast-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-black);
  margin-bottom: 2px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: baseline;
}
.toast-subtitle {
  font-weight: 400;
  color: var(--color-gray-500);
}
.toast-node {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-gray-400);
  margin-bottom: 10px;
}
.toast-actions {
  display: flex;
  gap: 8px;
}
.btn-accept {
  flex: 1;
  padding: 6px 0;
  background: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: var(--radius-md);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s;
}
.btn-accept:hover { opacity: 0.85; }
.btn-reject {
  flex: 1;
  padding: 6px 0;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-size: 13px;
  cursor: pointer;
  color: var(--color-gray-600, #4b5563);
}
.btn-reject:hover { background: var(--color-gray-100); }
</style>
