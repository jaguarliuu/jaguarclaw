<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import type { Message, StreamBlock } from '@/types'
import { useMarkdown } from '@/composables/useMarkdown'
import { useI18n } from '@/i18n'
import MessageItem from './MessageItem.vue'
import ToolCallCard from './ToolCallCard.vue'
import SubagentCard from './SubagentCard.vue'
import FileCard from './FileCard.vue'
import ConversationIndex from './ConversationIndex.vue'

const props = defineProps<{
  messages: Message[]
  streamBlocks: StreamBlock[]
  isStreaming: boolean
  assistantName?: string
  activeSubagentId?: string | null
  currentSessionId?: string | null
}>()

const emit = defineEmits<{
  confirm: [callId: string, decision: 'approve' | 'reject']
  'select-subagent': [subRunId: string]
}>()

const containerRef = ref<HTMLElement | null>(null)

const { render } = useMarkdown()
const { t } = useI18n()

const assistantAvatarInitial = computed(() => {
  const label = props.assistantName?.trim()
  if (!label) return 'M'
  return label[0]?.toUpperCase() || 'M'
})

// 渲染文本块的 Markdown — 仅用于非流式场景
function renderTextBlock(content: string | undefined): string {
  return render(content || '')
}

// 检查是否有内容（用于显示 thinking 状态）
const hasContent = computed(() => {
  return props.streamBlocks.some(block =>
    (block.type === 'text' && block.content) || block.type === 'tool' || block.type === 'subagent' || block.type === 'file'
  )
})

// Build index entries from user messages
const indexEntries = computed(() => {
  let turn = 0
  return props.messages
    .filter(m => m.role === 'user')
    .map(m => {
      turn++
      return {
        id: m.id,
        index: turn,
        preview: m.content.trim().slice(0, 60) || '…'
      }
    })
})

// Auto-scroll on new content — debounced to one scroll per animation frame
// Uses a simple length-based watch instead of an expensive .map().join() comparison
const lastTextBlockLength = computed(() => {
  const blocks = props.streamBlocks
  for (let i = blocks.length - 1; i >= 0; i--) {
    const block = blocks[i]
    if (block && block.type === 'text') return block.content?.length ?? 0
  }
  return 0
})

let rafScrollPending = false
watch(
  [() => props.messages.length, () => props.streamBlocks.length, lastTextBlockLength],
  () => {
    if (rafScrollPending) return
    rafScrollPending = true
    requestAnimationFrame(() => {
      rafScrollPending = false
      if (containerRef.value) {
        containerRef.value.scrollTop = containerRef.value.scrollHeight
      }
    })
  }
)
</script>

<template>
  <div class="message-list" ref="containerRef">
    <div class="message-layout">
      <div class="message-container">
        <!-- Empty state -->
        <div v-if="messages.length === 0 && !isStreaming" class="empty-state">
          <p class="empty-title">{{ t('message.emptyTitle') }}</p>
          <p class="empty-hint">{{ t('message.emptyHint') }}</p>
        </div>

        <!-- Messages -->
        <MessageItem
          v-for="message in messages"
          :key="message.id"
          :message="message"
          :assistant-name="assistantName"
          :anchor-id="message.role === 'user' ? `msg-${message.id}` : undefined"
          :active-subagent-id="activeSubagentId"
          @confirm="(callId, decision) => emit('confirm', callId, decision)"
          @select-subagent="(subRunId) => emit('select-subagent', subRunId)"
        />

        <!-- Streaming message with interleaved blocks -->
        <article v-if="isStreaming" class="message assistant streaming">
          <div class="message-inner">
            <div class="message-meta">
              <span class="msg-avatar assistant">{{ assistantAvatarInitial }}</span>
              <span class="role">{{ assistantName || t('message.assistant') }}</span>
              <span class="streaming-indicator">
                <span class="streaming-dot"></span>
                <span class="streaming-dot"></span>
                <span class="streaming-dot"></span>
              </span>
            </div>

            <!-- Thinking state when no content yet -->
            <div v-if="!hasContent" class="message-content">
              <p class="thinking">...</p>
            </div>

            <!-- Interleaved blocks -->
            <template v-for="block in streamBlocks" :key="block.id">
            <!-- Text block: plain text during streaming (no markdown parsing on every token) -->
            <div
              v-if="block.type === 'text' && block.content"
              class="message-content streaming-text"
            >{{ block.content }}</div>

            <!-- Tool block -->
            <ToolCallCard
              v-else-if="block.type === 'tool' && block.toolCall"
              :tool-call="block.toolCall"
              :session-id="currentSessionId ?? undefined"
              @confirm="(callId, decision) => emit('confirm', callId, decision)"
            />

            <!-- SubAgent block -->
            <SubagentCard
              v-else-if="block.type === 'subagent' && block.subagent"
              :subagent="block.subagent"
              :active-subagent-id="activeSubagentId"
              @select="(subRunId) => emit('select-subagent', subRunId)"
            />

            <!-- File block -->
            <FileCard
              v-else-if="block.type === 'file' && block.file"
              :file="block.file"
              :session-id="currentSessionId ?? undefined"
            />
          </template>
          </div>
        </article>
      </div>

      <!-- Conversation Index (show when ≥ 2 user turns) -->
      <ConversationIndex
        v-if="indexEntries.length >= 2"
        :entries="indexEntries"
        :scroll-container="containerRef"
        class="index-col"
      />
    </div>
  </div>
</template>

<style scoped>
.message-list {
  flex: 1;
  overflow-y: auto;
}

.message-layout {
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 0 48px;
  min-height: 100%;
}

.message-container {
  flex: 1;
  max-width: 720px;
  min-width: 0;
  padding: 48px 0;
}

.index-col {
  flex-shrink: 0;
  position: sticky;
  top: 24px;
  align-self: flex-start;
  padding-top: 52px;
  margin-left: 24px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 55vh;
  text-align: center;
}

.empty-title {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -0.02em;
  color: var(--color-black);
  margin-bottom: 10px;
}

.empty-hint {
  font-size: 14px;
  color: var(--color-gray-400);
}

/* Streaming message styles */
.message {
  padding: 20px 0;
  border-bottom: 1px solid var(--color-gray-100);
}

.message-inner {
  display: flex;
  flex-direction: column;
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.role {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 600;
  color: var(--color-gray-600);
}

.msg-avatar {
  width: 22px;
  height: 22px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  flex-shrink: 0;
}

.msg-avatar.assistant {
  background: var(--color-primary);
  color: white;
}

.streaming-indicator {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.streaming-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--color-primary);
  animation: stream-bounce 1.4s ease-in-out infinite;
}
.streaming-dot:nth-child(2) { animation-delay: 0.16s; }
.streaming-dot:nth-child(3) { animation-delay: 0.32s; }
@keyframes stream-bounce {
  0%, 80%, 100% { opacity: 0.2; transform: scale(0.8); }
  40% { opacity: 1; transform: scale(1); }
}

.message-content {
  font-size: 15px;
  line-height: 1.7;
  padding-left: 30px;
}

/* Streaming text: plain pre-wrap, no markdown parsing overhead */
.streaming-text {
  white-space: pre-wrap;
  word-break: break-word;
}

.message-content p {
  margin-bottom: 1em;
  white-space: pre-wrap;
}

.message-content p:last-child {
  margin-bottom: 0;
}

.thinking {
  color: var(--color-gray-dark);
  animation: fade 1s ease-in-out infinite;
}

@keyframes fade {
  0%, 100% {
    opacity: 0.3;
  }
  50% {
    opacity: 1;
  }
}
</style>

<!-- Markdown styles (unscoped to apply to v-html content) -->
<style>
@import '@/styles/markdown.css';
</style>
