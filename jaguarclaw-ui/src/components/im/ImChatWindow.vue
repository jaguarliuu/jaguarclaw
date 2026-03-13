<script setup lang="ts">
import { ref, nextTick, watch } from 'vue'
import type { ImMessage } from '@/types'

const props = defineProps<{
  conversationId: string
  messages: ImMessage[]
  selfNodeId: string
}>()

const emit = defineEmits<{ send: [text: string] }>()

const inputText = ref('')
const containerRef = ref<HTMLElement | null>(null)

watch(() => props.messages.length, async () => {
  await nextTick()
  if (containerRef.value) containerRef.value.scrollTop = containerRef.value.scrollHeight
})

function handleSend() {
  const text = inputText.value.trim()
  if (!text) return
  emit('send', text)
  inputText.value = ''
}

function extractText(content: string): string {
  try { return JSON.parse(content).text ?? content } catch { return content }
}
</script>

<template>
  <div class="chat-window">
    <div class="messages" ref="containerRef">
      <div
        v-for="msg in messages"
        :key="msg.id"
        class="bubble-row"
        :class="{ me: msg.isMe }"
      >
        <div class="bubble" :class="{ me: msg.isMe }">
          {{ extractText(msg.content) }}
        </div>
        <div class="bubble-time">{{ new Date(msg.createdAt).toLocaleTimeString() }}</div>
      </div>
    </div>

    <div class="input-bar">
      <textarea
        v-model="inputText"
        class="msg-input"
        placeholder="Type a message…"
        rows="1"
        @keydown.enter.exact.prevent="handleSend"
      />
      <button class="send-btn" @click="handleSend">Send</button>
    </div>
  </div>
</template>

<style scoped>
.chat-window { flex: 1; display: flex; flex-direction: column; }
.messages { flex: 1; overflow-y: auto; padding: 20px; display: flex; flex-direction: column; gap: 8px; }
.bubble-row { display: flex; flex-direction: column; }
.bubble-row.me { align-items: flex-end; }
.bubble {
  max-width: 70%; padding: 8px 12px;
  border-radius: var(--radius-lg);
  background: var(--color-gray-100);
  font-size: 14px; line-height: 1.5;
  white-space: pre-wrap; word-break: break-word;
}
.bubble.me { background: var(--color-primary); color: white; }
.bubble-time { font-size: 11px; color: var(--color-gray-400); margin-top: 2px; }
.input-bar { border-top: var(--border); padding: 12px 16px; display: flex; gap: 8px; }
.msg-input {
  flex: 1; resize: none;
  border: var(--border); border-radius: var(--radius-md);
  padding: 8px 12px; font-family: var(--font-ui); font-size: 14px;
  outline: none;
}
.msg-input:focus { border-color: var(--color-primary); }
.send-btn {
  padding: 8px 16px;
  background: var(--color-primary); color: white;
  border: none; border-radius: var(--radius-md);
  font-size: 13px; font-weight: 500; cursor: pointer;
}
.send-btn:hover { opacity: 0.85; }
</style>
