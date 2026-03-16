<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { Picker, EmojiIndex } from 'emoji-mart-vue-fast/src'
import data from 'emoji-mart-vue-fast/data/all.json'
import 'emoji-mart-vue-fast/css/emoji-mart.css'

const emit = defineEmits<{ select: [native: string] }>()

// Build the index once — shared across all instances via module scope is fine,
// but keeping it local keeps the component self-contained.
const emojiIndex = new EmojiIndex(data)

const pickerRef = ref<HTMLElement | null>(null)

function onSelect(emoji: { native: string }) {
  emit('select', emoji.native)
}

function onClickOutside(e: MouseEvent) {
  if (pickerRef.value && !pickerRef.value.contains(e.target as Node)) {
    emit('select', '') // signal parent to close (empty string = no emoji chosen)
  }
}

onMounted(() => document.addEventListener('mousedown', onClickOutside, true))
onBeforeUnmount(() => document.removeEventListener('mousedown', onClickOutside, true))
</script>

<template>
  <div ref="pickerRef" class="emoji-picker-wrap">
    <Picker
      :data="emojiIndex"
      set="native"
      :show-preview="false"
      :show-skin-tones="false"
      :native="true"
      color="#111"
      emoji="point_up"
      title=""
      @select="onSelect"
    />
  </div>
</template>

<style scoped>
.emoji-picker-wrap {
  position: absolute;
  bottom: calc(100% + 6px);
  left: 0;
  z-index: 200;
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: var(--shadow-lg);
  border: var(--border);
}
/* Trim the default picker padding slightly */
.emoji-picker-wrap :deep(.emoji-mart) {
  border: none !important;
  border-radius: 0 !important;
}
</style>
