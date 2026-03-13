<script setup lang="ts">
import type { ImPairRequestEvent } from '@/types'
defineProps<{ request: ImPairRequestEvent }>()
const emit = defineEmits<{ accept: []; reject: [] }>()
</script>

<template>
  <div class="pair-toast">
    <div class="pair-info">
      <strong>「{{ request.fromDisplayName }}」</strong> wants to connect
      <span class="fingerprint">{{ request.fromNodeId.slice(0, 8) }}…</span>
    </div>
    <div class="pair-actions">
      <button class="btn-accept" @click="emit('accept')">Accept</button>
      <button class="btn-reject" @click="emit('reject')">Reject</button>
    </div>
  </div>
</template>

<style scoped>
.pair-toast {
  position: fixed; top: 16px; right: 16px; z-index: 9999;
  background: white; border: var(--border); border-radius: var(--radius-lg);
  padding: 14px 16px; box-shadow: var(--shadow-lg);
  display: flex; align-items: center; gap: 12px;
  max-width: 340px;
}
.pair-info { flex: 1; font-size: 13px; }
.fingerprint { font-family: var(--font-mono); font-size: 11px; color: var(--color-gray-500); margin-left: 4px; }
.pair-actions { display: flex; gap: 6px; }
.btn-accept {
  padding: 5px 12px; background: var(--color-primary); color: white;
  border: none; border-radius: var(--radius-md); font-size: 12px; cursor: pointer;
}
.btn-reject {
  padding: 5px 12px; border: var(--border); border-radius: var(--radius-md);
  background: white; font-size: 12px; cursor: pointer;
}
</style>
