<!-- src/components/documents/DocumentAiSettingsPopover.vue -->
<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{ modelValue: boolean; systemPrompt: string }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  save: [systemPrompt: string]
}>()

const localPrompt = ref(props.systemPrompt)

watch(() => props.systemPrompt, (v) => { localPrompt.value = v })
watch(() => props.modelValue, (v) => { if (v) localPrompt.value = props.systemPrompt })

function handleSave() {
  emit('save', localPrompt.value)
  emit('update:modelValue', false)
}
</script>

<template>
  <Teleport to="body">
    <div v-if="modelValue" class="ai-settings-overlay" @click.self="$emit('update:modelValue', false)">
      <div class="ai-settings-popover">
        <div class="ai-settings-popover__header">
          <span>AI 写作设置</span>
          <button class="ai-settings-popover__close" @click="$emit('update:modelValue', false)">✕</button>
        </div>
        <div class="ai-settings-popover__body">
          <label class="ai-settings-popover__label">全局系统提示词</label>
          <p class="ai-settings-popover__hint">这个提示词会告诉 AI 如何写作。修改后对所有文档生效。</p>
          <textarea
            v-model="localPrompt"
            class="ai-settings-popover__textarea"
            rows="12"
            placeholder="输入系统提示词…"
          />
        </div>
        <div class="ai-settings-popover__footer">
          <button class="ai-settings-popover__cancel" @click="$emit('update:modelValue', false)">取消</button>
          <button class="ai-settings-popover__save" @click="handleSave">保存</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.ai-settings-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.3);
  z-index: 300;
  display: flex;
  align-items: center;
  justify-content: center;
}
.ai-settings-popover {
  background: var(--color-white);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  width: 560px;
  max-width: 90vw;
  display: flex;
  flex-direction: column;
  max-height: 80vh;
}
.ai-settings-popover__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: var(--border);
  font-size: 14px;
  font-weight: 600;
  color: var(--color-gray-900);
}
.ai-settings-popover__close {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 14px;
  color: var(--color-gray-500);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
}
.ai-settings-popover__close:hover { background: var(--color-gray-100); }
.ai-settings-popover__body {
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  overflow-y: auto;
}
.ai-settings-popover__label {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-gray-700);
}
.ai-settings-popover__hint {
  font-size: 12px;
  color: var(--color-gray-500);
  margin: 0;
}
.ai-settings-popover__textarea {
  width: 100%;
  font-size: 12px;
  font-family: var(--font-mono);
  border: var(--border);
  border-radius: var(--radius-md);
  padding: 10px;
  resize: vertical;
  line-height: 1.6;
  color: var(--color-gray-900);
  box-sizing: border-box;
}
.ai-settings-popover__textarea:focus { outline: none; border-color: var(--color-primary, #6366f1); }
.ai-settings-popover__footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px;
  border-top: var(--border);
}
.ai-settings-popover__cancel {
  padding: 6px 16px;
  font-size: 13px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  color: var(--color-gray-700);
  cursor: pointer;
}
.ai-settings-popover__save {
  padding: 6px 16px;
  font-size: 13px;
  border: none;
  border-radius: var(--radius-md);
  background: var(--color-gray-900);
  color: white;
  cursor: pointer;
  font-weight: 500;
}
.ai-settings-popover__save:hover { background: var(--color-gray-700); }
</style>
