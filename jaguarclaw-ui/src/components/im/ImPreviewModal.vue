<script setup lang="ts">
import { computed } from 'vue'
import { useMarkdown } from '@/composables/useMarkdown'

const props = defineProps<{
  type: 'text' | 'image' | 'pdf' | 'html' | 'file'
  url?: string
  content?: string
  filename?: string
}>()

const emit = defineEmits<{ close: [] }>()

const { render } = useMarkdown()
const renderedContent = computed(() => (props.type === 'text' && props.content) ? render(props.content) : '')

const title = computed(() => props.filename ?? (props.type === 'text' ? 'Message' : 'File'))

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('close')
}
</script>

<template>
  <Teleport to="body">
    <div class="modal-backdrop" @click.self="emit('close')" @keydown.esc="emit('close')" tabindex="-1">
      <div class="modal-shell" role="dialog" :aria-label="title">
        <!-- Header -->
        <header class="modal-header">
          <span class="modal-title">{{ title }}</span>
          <div class="modal-actions">
            <a v-if="url && type !== 'text'" :href="url" download class="modal-dl-btn" title="Download">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none">
                <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4M7 10l5 5 5-5M12 15V3"
                  stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </a>
            <button class="modal-close-btn" @click="emit('close')">
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                <line x1="1.5" y1="1.5" x2="12.5" y2="12.5" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                <line x1="12.5" y1="1.5" x2="1.5" y2="12.5" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
              </svg>
            </button>
          </div>
        </header>

        <!-- Body -->
        <div class="modal-body">
          <!-- Image -->
          <img v-if="type === 'image'" :src="url" class="preview-image" :alt="filename" />

          <!-- PDF -->
          <iframe
            v-else-if="type === 'pdf'"
            :src="url"
            class="preview-frame"
            title="PDF preview"
          />

          <!-- HTML (sandboxed) -->
          <iframe
            v-else-if="type === 'html'"
            :src="url"
            class="preview-frame"
            sandbox="allow-scripts allow-same-origin"
            title="HTML preview"
          />

          <!-- Long text / markdown -->
          <div
            v-else-if="type === 'text'"
            class="preview-text markdown-body"
            v-html="renderedContent"
          />

          <!-- Generic file download -->
          <div v-else class="preview-file-card">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none">
              <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"
                stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
              <polyline points="14,2 14,8 20,8" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            <p class="file-card-name">{{ filename }}</p>
            <a :href="url" download class="file-card-download">Download</a>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  padding: 24px;
}

.modal-shell {
  background: var(--content-bg);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 900px;
  max-height: 90vh;
  overflow: hidden;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: var(--border);
  flex-shrink: 0;
  gap: 12px;
}

.modal-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-black);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.modal-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.modal-dl-btn,
.modal-close-btn {
  width: 30px;
  height: 30px;
  border-radius: var(--radius-full);
  border: var(--border);
  background: transparent;
  color: var(--color-gray-500);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  text-decoration: none;
  transition: background var(--duration-fast), color var(--duration-fast);
  flex-shrink: 0;
}

.modal-dl-btn:hover,
.modal-close-btn:hover {
  background: var(--color-gray-100);
  color: var(--color-black);
}

.modal-body {
  flex: 1;
  overflow: auto;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  background: var(--color-gray-50);
}

.preview-image {
  max-width: 100%;
  max-height: 80vh;
  object-fit: contain;
  display: block;
}

.preview-frame {
  width: 100%;
  height: 80vh;
  border: none;
  display: block;
}

.preview-text {
  width: 100%;
  height: 100%;
  padding: 24px 32px;
  overflow-y: auto;
  font-size: 14px;
  line-height: 1.7;
  color: var(--color-black);
  background: var(--content-bg);
  box-sizing: border-box;
}

.preview-text :deep(h1), .preview-text :deep(h2), .preview-text :deep(h3) {
  margin: 1em 0 0.4em;
  font-weight: 600;
}
.preview-text :deep(p) { margin: 0.4em 0; }
.preview-text :deep(code) {
  background: var(--color-gray-100);
  padding: 1px 5px;
  border-radius: 4px;
  font-family: var(--font-mono);
  font-size: 0.88em;
}
.preview-text :deep(pre) {
  background: var(--color-gray-100);
  padding: 12px;
  border-radius: var(--radius-md);
  overflow-x: auto;
}

.preview-file-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 48px;
  color: var(--color-gray-500);
}

.file-card-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-black);
  text-align: center;
}

.file-card-download {
  padding: 9px 22px;
  background: var(--color-primary);
  color: var(--color-white);
  border-radius: var(--radius-full);
  font-size: 13px;
  font-weight: 500;
  text-decoration: none;
  transition: opacity var(--duration-fast);
}
.file-card-download:hover { opacity: 0.8; }
</style>
