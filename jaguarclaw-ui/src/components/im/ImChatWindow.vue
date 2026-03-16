<script setup lang="ts">
import { ref, computed, nextTick, watch } from 'vue'
import { useI18n } from '@/i18n'
import { useMarkdown } from '@/composables/useMarkdown'
import ImPreviewModal from './ImPreviewModal.vue'
import ImEmojiPicker from './ImEmojiPicker.vue'
import DiceBearAvatar from './DiceBearAvatar.vue'
import type { ImMessage } from '@/types'

const { t } = useI18n()
const { render } = useMarkdown()

const props = defineProps<{
  conversationId: string
  messages: ImMessage[]
  selfNodeId: string
  contactName?: string
  contactAvatarStyle?: string
  contactAvatarSeed?: string
  selfAvatarStyle?: string
  selfAvatarSeed?: string
}>()

const emit = defineEmits<{
  send: [text: string]
  sendFile: [file: File]
  clearChat: []
}>()

const inputText  = ref('')
const msgListRef = ref<HTMLElement | null>(null)
const fileInput  = ref<HTMLInputElement | null>(null)
const textareaRef = ref<HTMLTextAreaElement | null>(null)
const confirmClear = ref(false)
const showEmojiPicker = ref(false)

// ── Preview Modal ──
interface ModalState {
  open:     boolean
  type:     'text' | 'image' | 'pdf' | 'html' | 'file'
  url?:     string
  content?: string
  filename?: string
}
const modal = ref<ModalState>({ open: false, type: 'text' })

function openModal(state: Omit<ModalState, 'open'>) {
  modal.value = { open: true, ...state }
}
function closeModal() {
  modal.value = { ...modal.value, open: false }
}

// ── Auto-scroll ──
watch(
  () => props.messages.length,
  async () => {
    await nextTick()
    if (msgListRef.value)
      msgListRef.value.scrollTop = msgListRef.value.scrollHeight
  },
  { immediate: true }
)

// ── Emoji picker ──
function onEmojiSelect(native: string) {
  // Empty string means "click outside — close picker"
  if (!native) { showEmojiPicker.value = false; return }

  const el = textareaRef.value
  if (!el) {
    inputText.value += native
    showEmojiPicker.value = false
    return
  }
  // Insert emoji at current cursor position
  const start = el.selectionStart ?? inputText.value.length
  const end   = el.selectionEnd   ?? inputText.value.length
  inputText.value = inputText.value.slice(0, start) + native + inputText.value.slice(end)

  // Restore focus and move cursor after the inserted emoji
  nextTick(() => {
    el.focus()
    const pos = start + native.length
    el.setSelectionRange(pos, pos)
    // Re-trigger auto-resize
    el.style.height = 'auto'
    el.style.height = Math.min(el.scrollHeight, 128) + 'px'
  })
  // Keep picker open so user can pick multiple emojis
}

// ── Send text ──
function send() {
  const text = inputText.value.trim()
  if (!text) return
  emit('send', text)
  inputText.value = ''
  const el = document.querySelector('.msg-ta') as HTMLTextAreaElement | null
  if (el) el.style.height = 'auto'
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send() }
}

function onInput(e: Event) {
  const el = e.target as HTMLTextAreaElement
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 128) + 'px'
}

// ── File attach ──
function openFilePicker() {
  fileInput.value?.click()
}

function onFileSelected(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  emit('sendFile', file)
  input.value = ''
}

// ── Message helpers ──
const MAX_BUBBLE_CHARS = 400

function extractText(content: string): string {
  try { return JSON.parse(content).text ?? content } catch { return content }
}

function renderedText(content: string): string {
  return render(extractText(content))
}

function isLong(content: string): boolean {
  return extractText(content).length > MAX_BUBBLE_CHARS
}

function truncatedText(content: string): string {
  const t = extractText(content)
  return t.slice(0, MAX_BUBBLE_CHARS) + '…'
}

function fileSizeLabel(bytes?: number): string {
  if (!bytes) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function modalTypeForMsg(msg: ImMessage): 'image' | 'pdf' | 'html' | 'file' {
  const mt = msg.mimeType ?? ''
  if (mt.startsWith('image/'))       return 'image'
  if (mt === 'application/pdf')      return 'pdf'
  if (mt === 'text/html')            return 'html'
  return 'file'
}

function fileIcon(mimeType?: string): string {
  if (!mimeType) return '📎'
  if (mimeType.startsWith('image/'))  return '🖼'
  if (mimeType === 'application/pdf') return '📄'
  if (mimeType.includes('word'))      return '📝'
  if (mimeType.startsWith('text/'))   return '📃'
  if (mimeType.startsWith('audio/'))  return '🎵'
  if (mimeType.startsWith('video/'))  return '🎬'
  return '📎'
}

function timeStr(ts: string): string {
  return new Date(ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

function dateLabel(ts: string): string {
  const d = new Date(ts)
  const now = new Date()
  const y = new Date(now); y.setDate(now.getDate() - 1)
  if (d.toDateString() === now.toDateString()) return t('im.today')
  if (d.toDateString() === y.toDateString())   return t('im.yesterday')
  return d.toLocaleDateString([], { weekday: 'short', month: 'short', day: 'numeric' })
}

// ── Row grouping ──
interface Row {
  kind: 'date' | 'msg'
  label?: string
  msg?: ImMessage
  isFirst: boolean
  isLast: boolean
}

const rows = computed((): Row[] => {
  const out: Row[] = []
  let lastDate   = ''
  let lastSender = ''

  for (let i = 0; i < props.messages.length; i++) {
    const m = props.messages[i]!
    const d = new Date(m.createdAt).toDateString()

    if (d !== lastDate) {
      out.push({ kind: 'date', label: dateLabel(m.createdAt), isFirst: false, isLast: false })
      lastDate   = d
      lastSender = ''
    }

    const next    = props.messages[i + 1]
    const isFirst = m.senderNodeId !== lastSender
    const isLast  =
      !next ||
      next.senderNodeId !== m.senderNodeId ||
      new Date(next.createdAt).toDateString() !== d

    out.push({ kind: 'msg', msg: m, isFirst, isLast })
    lastSender = m.senderNodeId
  }
  return out
})
</script>

<template>
  <div class="chat-shell">
    <!-- ── Top bar ──────────────────────────────────────────────────── -->
    <header class="chat-header">
      <div v-if="contactName" class="hdr-avatar">
        <DiceBearAvatar :avatar-style="contactAvatarStyle" :avatar-seed="contactAvatarSeed || conversationId" :size="34" />
      </div>
      <div class="hdr-info">
        <span class="hdr-name">{{ contactName ?? '…' }}</span>
        <span class="hdr-status">{{ t('im.lastSeen') }}</span>
      </div>
      <div class="hdr-actions">
        <button class="hdr-btn" :title="t('im.clearChat')" @click="confirmClear = true">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="3 6 5 6 21 6"/>
            <path d="M19 6l-1 14a2 2 0 01-2 2H8a2 2 0 01-2-2L5 6"/>
            <path d="M10 11v6M14 11v6"/>
            <path d="M9 6V4a1 1 0 011-1h4a1 1 0 011 1v2"/>
          </svg>
        </button>
      </div>
    </header>

    <!-- ── Confirm clear dialog ─────────────────────────────────────── -->
    <div v-if="confirmClear" class="clear-overlay">
      <div class="clear-dialog">
        <p class="clear-title">{{ t('im.clearChatConfirmTitle') }}</p>
        <p class="clear-body">{{ t('im.clearChatConfirmBody') }}</p>
        <div class="clear-btns">
          <button class="clear-cancel" @click="confirmClear = false">{{ t('common.cancel') }}</button>
          <button class="clear-confirm" @click="() => { confirmClear = false; emit('clearChat') }">{{ t('im.clearConfirm') }}</button>
        </div>
      </div>
    </div>

    <!-- ── Message list ─────────────────────────────────────────────── -->
    <div class="msg-list" ref="msgListRef">
      <template v-for="(row, i) in rows" :key="i">

        <!-- Date separator -->
        <div v-if="row.kind === 'date'" class="date-sep">
          <span class="date-pill">{{ row.label }}</span>
        </div>

        <!-- Message -->
        <div
          v-else-if="row.kind === 'msg' && row.msg"
          class="msg-wrap"
          :class="{
            me: row.msg.isMe,
            'mt-tight': !row.isFirst,
          }"
        >
          <!-- Incoming avatar (left, contact) -->
          <div v-if="!row.msg.isMe" class="msg-avatar">
            <DiceBearAvatar
              v-if="row.isLast"
              :avatar-style="contactAvatarStyle"
              :avatar-seed="contactAvatarSeed || conversationId"
              :size="28"
              class="mini-avatar"
            />
            <div v-else class="mini-avatar-spacer" />
          </div>

          <div class="bubble-col" :class="{ me: row.msg.isMe }">

            <!-- ── IMAGE bubble ── -->
            <template v-if="row.msg.type === 'IMAGE' && row.msg.fileUrl">
              <div
                class="bubble img-bubble"
                :class="{ me: row.msg.isMe, 'tail-out': row.msg.isMe && row.isLast, 'tail-in': !row.msg.isMe && row.isLast }"
                @click="openModal({ type: 'image', url: row.msg.fileUrl, filename: row.msg.fileName })"
              >
                <img :src="row.msg.fileUrl" class="thumb-img" :alt="row.msg.fileName" />
                <div class="img-overlay">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
                    <circle cx="11" cy="11" r="8" stroke="currentColor" stroke-width="2"/>
                    <line x1="21" y1="21" x2="16.65" y2="16.65" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                    <line x1="11" y1="8" x2="11" y2="14" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                    <line x1="8" y1="11" x2="14" y2="11" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                  </svg>
                </div>
              </div>
            </template>

            <!-- ── FILE bubble ── -->
            <template v-else-if="row.msg.type === 'FILE' && row.msg.fileUrl">
              <div
                class="bubble file-bubble"
                :class="{ me: row.msg.isMe, 'tail-out': row.msg.isMe && row.isLast, 'tail-in': !row.msg.isMe && row.isLast }"
                @click="openModal({ type: modalTypeForMsg(row.msg), url: row.msg.fileUrl, filename: row.msg.fileName })"
              >
                <span class="file-icon">{{ fileIcon(row.msg.mimeType) }}</span>
                <span class="file-meta">
                  <span class="file-name">{{ row.msg.fileName ?? 'File' }}</span>
                  <span class="file-size">{{ fileSizeLabel(row.msg.fileSize) }}</span>
                </span>
                <svg class="file-open-ico" width="14" height="14" viewBox="0 0 24 24" fill="none">
                  <path d="M18 13v6a2 2 0 01-2 2H5a2 2 0 01-2-2V8a2 2 0 012-2h6M15 3h6v6M10 14L21 3"
                    stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
              </div>
            </template>

            <!-- ── TEXT bubble ── -->
            <template v-else>
              <!-- Long text: show truncated + expand button -->
              <template v-if="isLong(row.msg.content)">
                <div
                  class="bubble"
                  :class="{ me: row.msg.isMe, 'tail-out': row.msg.isMe && row.isLast, 'tail-in': !row.msg.isMe && row.isLast }"
                >
                  <span class="bubble-text">{{ truncatedText(row.msg.content) }}</span>
                  <button
                    class="expand-btn"
                    :class="{ me: row.msg.isMe }"
                    @click="openModal({ type: 'text', content: extractText(row.msg.content) })"
                  >{{ t('im.expand') }}</button>
                </div>
              </template>
              <template v-else>
                <div
                  class="bubble md-bubble"
                  :class="{ me: row.msg.isMe, 'tail-out': row.msg.isMe && row.isLast, 'tail-in': !row.msg.isMe && row.isLast }"
                  v-html="renderedText(row.msg.content)"
                />
              </template>
            </template>

            <!-- Footer: time + ticks -->
            <div v-if="row.isLast" class="msg-foot" :class="{ me: row.msg.isMe }">
              <span class="msg-time">{{ timeStr(row.msg.createdAt) }}</span>
              <svg v-if="row.msg.isMe && row.msg.status === 'delivered'"
                class="tick delivered" width="14" height="9" viewBox="0 0 14 9" fill="none">
                <polyline points="1,4.5 3.8,7.5 8.5,1.5" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>
                <polyline points="5.5,4.5 8.3,7.5 13,1.5" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
              <svg v-else-if="row.msg.isMe"
                class="tick sent" width="10" height="9" viewBox="0 0 10 9" fill="none">
                <polyline points="1,4.5 3.8,7.5 9,1.5" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </div>
          </div>

          <!-- Outgoing avatar (right, self) -->
          <div v-if="row.msg.isMe" class="msg-avatar">
            <DiceBearAvatar
              v-if="row.isLast"
              :avatar-style="selfAvatarStyle"
              :avatar-seed="selfAvatarSeed || selfNodeId"
              :size="28"
              class="mini-avatar"
            />
            <div v-else class="mini-avatar-spacer" />
          </div>
        </div>
      </template>

      <!-- Empty state -->
      <div v-if="messages.length === 0" class="chat-blank">
        <div class="blank-ring">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
            <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"
              stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <p class="blank-hint">{{ t('im.typeMessage') }}</p>
      </div>
    </div>

    <!-- ── Input bar ─────────────────────────────────────────────────── -->
    <div class="input-bar">
      <!-- Hidden file input -->
      <input ref="fileInput" type="file" hidden @change="onFileSelected" />

      <!-- Attach button -->
      <button class="attach-btn" @click="openFilePicker" :title="t('im.attach')">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
          <path d="M21.44 11.05l-9.19 9.19a6 6 0 01-8.49-8.49l9.19-9.19a4 4 0 015.66 5.66l-9.2 9.19a2 2 0 01-2.83-2.83l8.49-8.48"
            stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>

      <!-- Emoji button + picker -->
      <div class="emoji-anchor">
        <button
          class="attach-btn"
          :class="{ active: showEmojiPicker }"
          :title="t('im.emoji')"
          @click.stop="showEmojiPicker = !showEmojiPicker"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="1.8"/>
            <path d="M8 14s1.5 2 4 2 4-2 4-2" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
            <circle cx="9" cy="10" r="1" fill="currentColor"/>
            <circle cx="15" cy="10" r="1" fill="currentColor"/>
          </svg>
        </button>
        <ImEmojiPicker v-if="showEmojiPicker" @select="onEmojiSelect" />
      </div>

      <textarea
        ref="textareaRef"
        v-model="inputText"
        class="msg-ta"
        :placeholder="t('im.typeMessage')"
        rows="1"
        @keydown="onKeydown"
        @input="onInput"
      />
      <button class="send-btn" @click="send" :disabled="!inputText.trim()">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
          <path d="M22 2L11 13M22 2L15 22L11 13L2 9L22 2Z"
            stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
    </div>

    <!-- ── Preview Modal ─────────────────────────────────────────────── -->
    <ImPreviewModal
      v-if="modal.open"
      :type="modal.type"
      :url="modal.url"
      :content="modal.content"
      :filename="modal.filename"
      @close="closeModal"
    />
  </div>
</template>

<style scoped>
/* ── Shell ─────────────────────────────────────────────────────────── */
.chat-shell {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--content-bg);
  position: relative;
}

/* ── Header ─────────────────────────────────────────────────────────── */
.chat-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 13px 20px;
  border-bottom: var(--border);
  background: var(--content-bg);
  flex-shrink: 0;
}
.hdr-avatar {
  width: 34px; height: 34px; min-width: 34px;
  border-radius: var(--radius-full);
  overflow: hidden;
  flex-shrink: 0;
}
.hdr-info { flex: 1; min-width: 0; }
.hdr-name {
  display: block; font-size: 14px; font-weight: 600;
  color: var(--color-black); letter-spacing: -0.2px; line-height: 1.2;
}
.hdr-status { display: block; font-size: 11.5px; color: var(--color-gray-400); }

.hdr-actions { display: flex; gap: 4px; align-items: center; margin-left: auto; }
.hdr-btn {
  width: 30px; height: 30px; border-radius: var(--radius-md);
  border: var(--border); background: transparent;
  color: var(--color-gray-500); display: flex; align-items: center;
  justify-content: center; cursor: pointer;
  transition: background var(--duration-fast), color var(--duration-fast);
}
.hdr-btn:hover { background: var(--color-gray-100); color: var(--color-black); }

/* Confirm clear dialog */
.clear-overlay {
  position: absolute; inset: 0; z-index: 20;
  background: rgba(0,0,0,0.25);
  display: flex; align-items: center; justify-content: center;
}
.clear-dialog {
  background: var(--color-white); border: var(--border);
  border-radius: var(--radius-lg); padding: 20px 24px;
  width: 280px; box-shadow: var(--shadow-lg);
}
.clear-title { font-size: 14px; font-weight: 600; margin: 0 0 6px; color: var(--color-black); }
.clear-body  { font-size: 13px; color: var(--color-gray-600); margin: 0 0 16px; }
.clear-btns  { display: flex; gap: 8px; justify-content: flex-end; }
.clear-cancel {
  padding: 6px 14px; border-radius: var(--radius-md);
  border: var(--border); background: transparent;
  font-size: 13px; cursor: pointer; color: var(--color-gray-700);
  transition: background var(--duration-fast);
}
.clear-cancel:hover { background: var(--color-gray-100); }
.clear-confirm {
  padding: 6px 14px; border-radius: var(--radius-md);
  border: none; background: #ef4444; color: #fff;
  font-size: 13px; font-weight: 500; cursor: pointer;
  transition: opacity var(--duration-fast);
}
.clear-confirm:hover { opacity: 0.85; }

/* ── Message list ─────────────────────────────────────────────────── */
.msg-list {
  flex: 1; overflow-y: auto;
  padding: 20px 20px 8px;
  display: flex; flex-direction: column; gap: 0;
  background: var(--color-gray-50);
}

/* Date separator */
.date-sep { display: flex; justify-content: center; margin: 16px 0 10px; }
.date-pill {
  padding: 3px 12px; border-radius: var(--radius-full);
  background: var(--color-gray-200); font-size: 11px;
  color: var(--color-gray-600); font-weight: 500;
}

/* Message wrapper */
.msg-wrap { display: flex; align-items: flex-end; gap: 8px; margin-top: 10px; }
.msg-wrap.me { flex-direction: row-reverse; }
.msg-wrap.mt-tight { margin-top: 3px; }

/* Avatar slots (both sides) */
.msg-avatar { width: 28px; flex-shrink: 0; display: flex; align-items: flex-end; }
.mini-avatar {
  width: 28px; height: 28px; border-radius: var(--radius-full);
  overflow: hidden;
}
.mini-avatar-spacer { width: 28px; height: 28px; }

/* Bubble column */
.bubble-col { display: flex; flex-direction: column; align-items: flex-start; max-width: 62%; }
.bubble-col.me { align-items: flex-end; }

/* Text bubble */
.bubble {
  padding: 9px 14px; border-radius: 18px; font-size: 14px;
  line-height: 1.5; white-space: pre-wrap; word-break: break-word;
  background: var(--color-white); color: var(--color-black);
  box-shadow: var(--shadow-xs); border: var(--border);
}
.bubble.me {
  background: var(--color-primary); color: var(--color-white);
  border-color: transparent; box-shadow: none;
}
.bubble.tail-in  { border-bottom-left-radius: 5px; }
.bubble.tail-out { border-bottom-right-radius: 5px; }

/* Markdown bubble overrides */
.md-bubble { white-space: normal; }
.md-bubble :deep(p)  { margin: 0 0 0.4em; }
.md-bubble :deep(p:last-child) { margin-bottom: 0; }
.md-bubble :deep(strong) { font-weight: 600; }
.md-bubble :deep(em) { font-style: italic; }
.md-bubble :deep(code) {
  background: rgba(0,0,0,0.08); padding: 1px 5px;
  border-radius: 4px; font-family: var(--font-mono); font-size: 0.85em;
}
.md-bubble.me :deep(code) { background: rgba(255,255,255,0.2); }
.md-bubble :deep(pre) {
  background: rgba(0,0,0,0.06); padding: 10px 12px;
  border-radius: 8px; overflow-x: auto; margin: 6px 0;
}
.md-bubble.me :deep(pre) { background: rgba(255,255,255,0.15); }
.md-bubble :deep(ul), .md-bubble :deep(ol) {
  padding-left: 18px; margin: 4px 0;
}
.md-bubble :deep(li) { margin: 2px 0; }
.md-bubble :deep(a) { color: inherit; text-decoration: underline; }
.md-bubble :deep(blockquote) {
  border-left: 3px solid currentColor; opacity: 0.7;
  margin: 4px 0 4px 2px; padding-left: 10px; font-style: italic;
}

/* Expand button */
.bubble-text { display: block; }
.expand-btn {
  display: inline-block; margin-top: 6px; padding: 2px 8px;
  font-size: 12px; font-weight: 500;
  background: rgba(0,0,0,0.08); color: inherit;
  border: none; border-radius: var(--radius-full);
  cursor: pointer; transition: background var(--duration-fast);
}
.expand-btn:hover { background: rgba(0,0,0,0.14); }
.expand-btn.me { background: rgba(255,255,255,0.2); }
.expand-btn.me:hover { background: rgba(255,255,255,0.3); }

/* Image bubble */
.img-bubble {
  padding: 4px; cursor: pointer; position: relative; overflow: hidden;
  white-space: normal; max-width: 220px;
}
.thumb-img {
  display: block; width: 100%; max-width: 200px; max-height: 200px;
  object-fit: cover; border-radius: 14px;
}
.img-overlay {
  position: absolute; inset: 4px; border-radius: 14px;
  background: rgba(0,0,0,0); display: flex; align-items: center;
  justify-content: center; color: white; opacity: 0;
  transition: background var(--duration-fast), opacity var(--duration-fast);
}
.img-bubble:hover .img-overlay { background: rgba(0,0,0,0.3); opacity: 1; }

/* File bubble */
.file-bubble {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 14px; cursor: pointer; min-width: 200px;
  white-space: normal;
}
.file-icon { font-size: 24px; line-height: 1; flex-shrink: 0; }
.file-meta {
  flex: 1; min-width: 0; display: flex;
  flex-direction: column; gap: 2px;
}
.file-name {
  font-size: 13px; font-weight: 600;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.file-size { font-size: 11px; opacity: 0.65; }
.file-open-ico { flex-shrink: 0; opacity: 0.6; }

/* Message footer */
.msg-foot {
  display: flex; align-items: center; gap: 3px;
  margin-top: 3px; padding: 0 3px;
}
.msg-time { font-size: 11px; color: var(--color-gray-400); }
.tick { color: var(--color-gray-400); }
.tick.delivered { color: var(--color-primary); }
.msg-foot.me .msg-time { color: var(--color-gray-500); }

/* Chat blank */
.chat-blank {
  flex: 1; display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  gap: 10px; padding-bottom: 60px;
}
.blank-ring {
  width: 60px; height: 60px; border-radius: var(--radius-full);
  background: var(--color-gray-200);
  display: flex; align-items: center; justify-content: center;
  color: var(--color-gray-400);
}
.blank-hint { font-size: 13px; color: var(--color-gray-400); }

/* ── Input bar ────────────────────────────────────────────────────── */
.input-bar {
  display: flex; align-items: flex-end; gap: 8px;
  padding: 12px 16px; border-top: var(--border);
  background: var(--content-bg); flex-shrink: 0;
}
.attach-btn {
  width: 36px; height: 36px; min-width: 36px;
  border-radius: var(--radius-full); border: var(--border);
  background: transparent; color: var(--color-gray-500);
  display: flex; align-items: center; justify-content: center;
  cursor: pointer; flex-shrink: 0;
  transition: background var(--duration-fast), color var(--duration-fast);
}
.attach-btn:hover { background: var(--color-gray-100); color: var(--color-black); }
.attach-btn.active { background: var(--color-gray-100); color: var(--color-black); }

.emoji-anchor { position: relative; flex-shrink: 0; }

.msg-ta {
  flex: 1; resize: none; border: var(--border);
  border-radius: 20px; padding: 9px 14px;
  font-family: var(--font-ui); font-size: 14px; line-height: 1.45;
  max-height: 128px; overflow-y: auto; outline: none;
  background: var(--color-gray-50); color: var(--color-black);
  transition: border-color var(--duration-fast), background var(--duration-fast);
}
.msg-ta:focus { border-color: var(--color-primary); background: var(--color-white); }
.msg-ta::placeholder { color: var(--color-gray-400); }

.send-btn {
  width: 38px; height: 38px; min-width: 38px;
  border-radius: var(--radius-full); border: none;
  background: var(--color-primary); color: var(--color-white);
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  transition: opacity var(--duration-fast);
}
.send-btn:hover:not(:disabled) { opacity: 0.82; }
.send-btn:disabled { opacity: 0.28; cursor: not-allowed; }
</style>
