<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'

const props = defineProps<{
  visible: boolean
  action: string
  x: number
  y: number
}>()

const emit = defineEmits<{
  confirm: [userPrompt: string]
  cancel: []
}>()

const inputRef = ref<HTMLInputElement | null>(null)
const inputValue = ref('')

const actionLabels: Record<string, string> = {
  continue: 'AI 续写',
  optimize: 'AI 润色',
  summarize: 'AI 总结',
  rewrite: 'AI 改写',
  translate: 'AI 翻译',
}

watch(() => props.visible, async (v) => {
  if (v) {
    inputValue.value = ''
    await nextTick()
    inputRef.value?.focus()
  }
})

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    emit('confirm', inputValue.value.trim())
  }
  if (e.key === 'Escape') {
    emit('cancel')
  }
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="visible"
      class="ai-prompt-backdrop"
      @click.self="$emit('cancel')"
    >
      <div
        class="ai-prompt-bubble"
        :style="{ top: `${y}px`, left: `${x}px` }"
      >
        <div class="ai-prompt-bubble__label">{{ actionLabels[action] ?? action }}</div>
        <input
          ref="inputRef"
          v-model="inputValue"
          class="ai-prompt-bubble__input"
          :placeholder="`告诉 AI 你的需求（直接回车可跳过）…`"
          @keydown="handleKeydown"
        />
        <div class="ai-prompt-bubble__hint">↵ 确认 &nbsp; Esc 取消</div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.ai-prompt-backdrop {
  position: fixed;
  inset: 0;
  z-index: 200;
  background: transparent;
}
.ai-prompt-bubble {
  position: fixed;
  background: var(--color-white);
  border: 1px solid var(--color-gray-200);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  padding: 12px 14px;
  min-width: 320px;
  max-width: 480px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  z-index: 201;
}
.ai-prompt-bubble__label {
  font-size: 11px;
  font-weight: 600;
  color: var(--color-gray-500);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-family: var(--font-ui);
}
.ai-prompt-bubble__input {
  width: 100%;
  border: none;
  outline: none;
  font-size: 14px;
  font-family: var(--font-ui);
  color: var(--color-gray-900);
  background: transparent;
  box-sizing: border-box;
}
.ai-prompt-bubble__input::placeholder { color: var(--color-gray-400); }
.ai-prompt-bubble__hint {
  font-size: 11px;
  color: var(--color-gray-400);
  font-family: var(--font-ui);
}
</style>
