<script setup lang="ts">
import { computed, ref, watchEffect } from 'vue'
import { useArtifact } from '@/composables/useArtifact'
import type { AttachmentDescriptor } from '@/utils/attachments'
import { formatAttachmentSize } from '@/utils/attachments'

const props = defineProps<{
  attachment: AttachmentDescriptor
}>()

const { openArtifact } = useArtifact()

const previewContent = ref('')
const previewLoading = ref(false)
const previewLoaded = ref(false)
const previewError = ref(false)

const isImage = computed(() => props.attachment.kind === 'image')
const isText = computed(() => props.attachment.kind === 'text')
const isPdf = computed(() => props.attachment.kind === 'pdf')
const isFolder = computed(() => props.attachment.kind === 'folder')
const canOpen = computed(() => Boolean(props.attachment.previewUrl))
const canDownload = computed(() => Boolean(props.attachment.downloadUrl))
const formattedSize = computed(() => formatAttachmentSize(props.attachment.size))
const summaryMeta = computed(() =>
  [props.attachment.typeLabel, props.attachment.size ? formattedSize.value : null]
    .filter(Boolean)
    .join(' · '),
)
const displayPath = computed(() => props.attachment.displayPath || props.attachment.filePath)
const excerpt = computed(() => {
  if (!previewContent.value) return ''
  const normalized = previewContent.value.replace(/\r\n/g, '\n').trim()
  const lines = normalized
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
  const sample = lines.slice(0, 5).join('\n') || normalized.slice(0, 220)
  return sample.length > 280 ? `${sample.slice(0, 280)}…` : sample
})

async function ensureTextPreviewLoaded() {
  if (!isText.value || previewLoading.value || previewLoaded.value || !props.attachment.previewUrl)
    return
  previewLoading.value = true
  previewError.value = false
  try {
    const response = await fetch(props.attachment.previewUrl)
    if (!response.ok) {
      throw new Error(`Failed to fetch preview: ${response.status}`)
    }
    previewContent.value = await response.text()
    previewLoaded.value = true
  } catch {
    previewError.value = true
  } finally {
    previewLoading.value = false
  }
}

async function handleOpenTextPreview() {
  await ensureTextPreviewLoaded()
  if (!previewContent.value) return
  openArtifact(props.attachment.name, previewContent.value)
}

function openExternal() {
  if (!props.attachment.previewUrl) return
  window.open(props.attachment.previewUrl, '_blank', 'noopener')
}

watchEffect(() => {
  previewContent.value = ''
  previewLoaded.value = false
  previewError.value = false
  if (props.attachment.kind === 'text') {
    void ensureTextPreviewLoaded()
  }
})
</script>

<template>
  <article class="attachment-card" :class="`kind-${attachment.kind}`">
    <template v-if="isImage">
      <button type="button" class="image-frame" @click="openExternal">
        <img :src="attachment.previewUrl" :alt="attachment.name" loading="lazy" />
      </button>

      <div class="attachment-footer">
        <div class="attachment-main">
          <span class="type-pill image">{{ attachment.typeLabel }}</span>
          <span class="attachment-name" :title="attachment.name">{{ attachment.name }}</span>
          <span class="attachment-meta">{{ formattedSize }}</span>
        </div>
        <div class="attachment-actions">
          <button v-if="canOpen" type="button" class="action-btn" @click="openExternal">
            Open
          </button>
          <a
            v-if="canDownload"
            :href="attachment.downloadUrl"
            :download="attachment.name"
            class="action-btn"
            >Download</a
          >
        </div>
      </div>
    </template>

    <template v-else-if="isText">
      <div class="text-header">
        <div class="attachment-main">
          <span class="type-pill text">{{ attachment.typeLabel }}</span>
          <span class="attachment-name" :title="attachment.name">{{ attachment.name }}</span>
          <span class="attachment-meta">{{ formattedSize }}</span>
        </div>
      </div>

      <button type="button" class="text-preview" @click="handleOpenTextPreview">
        <span v-if="previewLoading" class="preview-state">Loading preview…</span>
        <span v-else-if="previewError" class="preview-state muted"
          >Preview unavailable. Click to retry.</span
        >
        <pre v-else-if="excerpt" class="preview-content">{{ excerpt }}</pre>
        <span v-else class="preview-state muted">No preview content</span>
      </button>

      <div class="attachment-footer compact">
        <span class="attachment-meta">{{ summaryMeta }}</span>
        <div class="attachment-actions">
          <button v-if="canOpen" type="button" class="action-btn" @click="handleOpenTextPreview">
            Preview
          </button>
          <a
            v-if="canDownload"
            :href="attachment.downloadUrl"
            :download="attachment.name"
            class="action-btn"
            >Download</a
          >
        </div>
      </div>
    </template>

    <template v-else-if="isPdf">
      <div class="feature-shell pdf-shell">
        <div class="feature-icon pdf-icon">PDF</div>
        <div class="attachment-main grow">
          <span class="type-pill pdf">{{ attachment.typeLabel }}</span>
          <span class="attachment-name" :title="attachment.name">{{ attachment.name }}</span>
          <span class="attachment-meta">{{ summaryMeta }}</span>
          <span v-if="displayPath" class="attachment-path" :title="displayPath">{{
            displayPath
          }}</span>
        </div>
        <div class="attachment-actions vertical">
          <button v-if="canOpen" type="button" class="action-btn solid" @click="openExternal">
            Open PDF
          </button>
          <a
            v-if="canDownload"
            :href="attachment.downloadUrl"
            :download="attachment.name"
            class="action-btn"
            >Download</a
          >
        </div>
      </div>
    </template>

    <template v-else-if="isFolder">
      <div class="feature-shell folder-shell">
        <div class="feature-icon folder-icon">📁</div>
        <div class="attachment-main grow">
          <span class="type-pill folder">{{ attachment.typeLabel }}</span>
          <span class="attachment-name" :title="attachment.name">{{ attachment.name }}</span>
          <span class="attachment-meta">Workspace folder context</span>
          <span v-if="displayPath" class="attachment-path" :title="displayPath">{{
            displayPath
          }}</span>
        </div>
      </div>
    </template>

    <template v-else>
      <div class="feature-shell generic-shell">
        <div class="generic-icon">{{ attachment.typeLabel }}</div>
        <div class="attachment-main grow">
          <span class="attachment-name" :title="attachment.name">{{ attachment.name }}</span>
          <span class="attachment-meta">{{ summaryMeta }}</span>
          <span v-if="displayPath" class="attachment-path" :title="displayPath">{{
            displayPath
          }}</span>
        </div>
        <div class="attachment-actions vertical">
          <button v-if="canOpen" type="button" class="action-btn" @click="openExternal">
            Open
          </button>
          <a
            v-if="canDownload"
            :href="attachment.downloadUrl"
            :download="attachment.name"
            class="action-btn"
            >Download</a
          >
        </div>
      </div>
    </template>
  </article>
</template>

<style scoped>
.attachment-card {
  width: min(360px, 100%);
  border: var(--border);
  border-radius: 18px;
  background: var(--color-white);
  overflow: hidden;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.06);
}

.image-frame {
  display: block;
  width: 100%;
  max-height: 280px;
  padding: 0;
  border: none;
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.02), rgba(15, 23, 42, 0.06));
  cursor: pointer;
}

.image-frame img {
  display: block;
  width: 100%;
  max-height: 280px;
  object-fit: cover;
}

.text-header {
  padding: 14px 14px 0;
}

.text-preview {
  width: calc(100% - 24px);
  margin: 12px;
  padding: 14px;
  border: 1px solid var(--color-gray-200);
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.9), rgba(241, 245, 249, 0.9));
  text-align: left;
  cursor: pointer;
}

.preview-content {
  margin: 0;
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  color: var(--color-gray-700);
}

.preview-state {
  font-size: 13px;
  color: var(--color-gray-700);
}

.preview-state.muted {
  color: var(--color-gray-500);
}

.feature-shell {
  display: flex;
  align-items: stretch;
  gap: 12px;
  padding: 14px;
}

.pdf-shell {
  background: linear-gradient(180deg, rgba(254, 242, 242, 0.8), rgba(255, 255, 255, 1));
}

.folder-shell {
  background: linear-gradient(180deg, rgba(255, 251, 235, 0.9), rgba(255, 255, 255, 1));
}

.generic-shell {
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.8), rgba(255, 255, 255, 1));
}

.feature-icon,
.generic-icon {
  width: 52px;
  min-width: 52px;
  height: 52px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.05em;
}

.pdf-icon {
  background: linear-gradient(135deg, rgba(220, 38, 38, 0.12), rgba(248, 113, 113, 0.22));
  color: #b91c1c;
}

.folder-icon {
  background: linear-gradient(135deg, rgba(245, 158, 11, 0.16), rgba(251, 191, 36, 0.24));
}

.generic-icon {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.12), rgba(59, 130, 246, 0.18));
  color: var(--color-gray-700);
}

.attachment-footer {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 14px;
}

.attachment-footer.compact {
  align-items: center;
  padding-top: 0;
}

.attachment-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.attachment-main.grow {
  flex: 1;
}

.type-pill {
  align-self: flex-start;
  padding: 4px 8px;
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.05em;
}

.type-pill.image {
  background: rgba(37, 99, 235, 0.1);
  color: #1d4ed8;
}

.type-pill.text {
  background: rgba(15, 23, 42, 0.06);
  color: var(--color-gray-700);
}

.type-pill.pdf {
  background: rgba(220, 38, 38, 0.1);
  color: #b91c1c;
}

.type-pill.folder {
  background: rgba(245, 158, 11, 0.14);
  color: #b45309;
}

.attachment-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-black);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-meta,
.attachment-path {
  font-size: 12px;
  color: var(--color-gray-500);
}

.attachment-path {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.attachment-actions.vertical {
  flex-direction: column;
  align-items: stretch;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 82px;
  height: 32px;
  padding: 0 12px;
  border: 1px solid var(--color-gray-200);
  border-radius: 10px;
  background: var(--color-white);
  color: var(--color-gray-700);
  text-decoration: none;
  font-size: 12px;
  font-weight: 600;
  transition: all 0.15s ease;
  cursor: pointer;
}

.action-btn.solid {
  background: #111827;
  border-color: #111827;
  color: white;
}

.action-btn:hover {
  border-color: var(--color-gray-400);
  background: var(--color-gray-50);
  color: var(--color-black);
}

.action-btn.solid:hover {
  background: #1f2937;
  border-color: #1f2937;
  color: white;
}
</style>
