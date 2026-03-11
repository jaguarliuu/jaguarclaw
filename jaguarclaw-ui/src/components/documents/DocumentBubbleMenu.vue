<script setup lang="ts">
defineProps<{ aiStreaming: boolean }>()
defineEmits<{ action: [action: string] }>()
</script>

<template>
  <div class="bubble-menu" @mousedown.prevent>
    <button :disabled="aiStreaming" @click="$emit('action', 'rewrite')">改写</button>
    <button :disabled="aiStreaming" @click="$emit('action', 'optimize')">优化</button>
    <button :disabled="aiStreaming" @click="$emit('action', 'translate')">翻译</button>
    <button :disabled="aiStreaming" @click="$emit('action', 'continue')">续写</button>
  </div>
</template>

<style scoped>
.bubble-menu {
  display: flex; gap: 2px;
  background: var(--color-gray-900); border-radius: var(--radius-md);
  padding: 4px; box-shadow: var(--shadow-md);
}
.bubble-menu button {
  padding: 4px 10px; font-size: 12px; color: var(--color-white);
  background: transparent; border: none; border-radius: var(--radius-sm); cursor: pointer;
}
.bubble-menu button:hover:not(:disabled) { background: rgba(255,255,255,0.15); }
.bubble-menu button:disabled { opacity: 0.4; cursor: default; }
</style>
