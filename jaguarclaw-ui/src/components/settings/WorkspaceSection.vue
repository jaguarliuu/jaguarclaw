<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useSoulConfig } from '@/composables/useSoulConfig'
import { useAgents } from '@/composables/useAgents'
import { useMarkdown } from '@/composables/useMarkdown'
import Select from '@/components/common/Select.vue'
import type { SelectOption } from '@/components/common/Select.vue'

const route = useRoute()
const { persona, loading, error, fetchPersona, saveFile, watchPersonaUpdates } = useSoulConfig()
const { agents, defaultAgent, loadAgents } = useAgents()
const { render: renderMarkdown } = useMarkdown()

type FileKey = 'soul' | 'rule' | 'profile'
type TabKey = 'content' | 'preview'

const selectedAgentId = ref('main')
const activeFile = ref<FileKey>('soul')
const activeTab = ref<TabKey>('content')
const editContent = ref('')
const saving = ref(false)
const saveSuccess = ref(false)
const saveError = ref<string | null>(null)

let stopWatcher: (() => void) | null = null

const fileList: { key: FileKey; label: string; desc: string }[] = [
  { key: 'soul', label: 'SOUL.md', desc: 'Identity & personality' },
  { key: 'rule', label: 'RULE.md', desc: 'Behavioral constraints' },
  { key: 'profile', label: 'PROFILE.md', desc: 'User preferences' },
]

const sortedAgents = computed(() => {
  return [...agents.value].sort((a, b) => {
    if (a.isDefault && !b.isDefault) return -1
    if (!a.isDefault && b.isDefault) return 1
    return a.createdAt.localeCompare(b.createdAt)
  })
})

const agentSelectOptions = computed<SelectOption[]>(() =>
  sortedAgents.value.map(a => ({ label: a.displayName || a.name, value: a.id }))
)

const previewHtml = computed(() => renderMarkdown(editContent.value))

function selectFile(key: FileKey) {
  activeFile.value = key
  activeTab.value = 'content'
  if (persona.value) {
    editContent.value = persona.value[key] || ''
  }
}

function resolveRouteAgentId() {
  const fromQuery = route.query.agentId
  if (typeof fromQuery === 'string' && fromQuery.trim().length > 0) {
    return fromQuery.trim()
  }
  return defaultAgent.value?.id || 'main'
}

async function loadPersona(agentId: string) {
  await fetchPersona(agentId)
  if (persona.value) {
    editContent.value = persona.value[activeFile.value] || ''
  }
}

async function handleSave() {
  saving.value = true
  saveError.value = null
  saveSuccess.value = false
  try {
    await saveFile(selectedAgentId.value, activeFile.value, editContent.value)
    saveSuccess.value = true
    setTimeout(() => { saveSuccess.value = false }, 3000)
  } catch (e) {
    saveError.value = e instanceof Error ? e.message : 'Failed to save'
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadAgents()
  selectedAgentId.value = resolveRouteAgentId()
  await loadPersona(selectedAgentId.value)
  stopWatcher = watchPersonaUpdates(selectedAgentId.value, () => {
    void loadPersona(selectedAgentId.value)
  })
})

onUnmounted(() => {
  stopWatcher?.()
})

watch(selectedAgentId, async (agentId) => {
  await loadPersona(agentId)
})

watch(
  () => route.query.agentId,
  (value) => {
    if (typeof value !== 'string' || !value.trim()) return
    if (value !== selectedAgentId.value) {
      selectedAgentId.value = value
    }
  },
)

watch(activeFile, (key) => {
  if (persona.value) {
    editContent.value = persona.value[key] || ''
  }
})
</script>

<template>
  <div class="workspace-section">
    <header class="section-header">
      <div class="header-left">
        <h2 class="section-title">Persona</h2>
        <p class="section-subtitle">Edit SOUL.md, RULE.md, and PROFILE.md directly</p>
      </div>
      <div class="header-agent-select" v-if="agentSelectOptions.length > 1">
        <label class="form-label">Agent</label>
        <Select v-model="selectedAgentId" :options="agentSelectOptions" />
      </div>
    </header>

    <div v-if="loading && !persona" class="loading-state">Loading...</div>

    <div v-if="error && !persona" class="error-state">
      <p>{{ error }}</p>
      <button class="retry-btn" @click="loadPersona(selectedAgentId)">Retry</button>
    </div>

    <div v-if="persona" class="editor-layout">
      <!-- Left: file list -->
      <aside class="file-list">
        <button
          v-for="f in fileList"
          :key="f.key"
          class="file-card"
          :class="{ active: activeFile === f.key }"
          @click="selectFile(f.key)"
        >
          <span class="file-name">{{ f.label }}</span>
          <span class="file-desc">{{ f.desc }}</span>
          <span class="file-size">{{ persona[f.key]?.length ?? 0 }} chars</span>
        </button>
      </aside>

      <!-- Right: editor -->
      <div class="editor-panel">
        <div class="editor-header">
          <span class="editor-filename">{{ fileList.find(f => f.key === activeFile)?.label }}</span>
        </div>

        <div class="tab-bar">
          <button
            class="tab-btn"
            :class="{ active: activeTab === 'content' }"
            @click="activeTab = 'content'"
          >Content</button>
          <button
            class="tab-btn"
            :class="{ active: activeTab === 'preview' }"
            @click="activeTab = 'preview'"
          >Preview</button>
        </div>

        <div class="editor-body">
          <textarea
            v-if="activeTab === 'content'"
            v-model="editContent"
            class="md-editor"
            spellcheck="false"
          />
          <div
            v-else
            class="md-preview prose"
            v-html="previewHtml"
          />
        </div>

        <div class="editor-footer">
          <div class="save-feedback">
            <span v-if="saveSuccess" class="save-success">Saved</span>
            <span v-if="saveError" class="save-error">{{ saveError }}</span>
          </div>
          <button
            class="save-btn"
            :disabled="saving"
            @click="handleSave"
          >
            {{ saving ? 'Saving...' : 'Save' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.workspace-section {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 24px 32px 0;
  box-sizing: border-box;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 20px;
  flex-shrink: 0;
}

.header-left {
  min-width: 0;
}

.header-agent-select {
  min-width: 200px;
}

.section-title {
  font-family: var(--font-mono);
  font-size: 20px;
  font-weight: 500;
  margin-bottom: 4px;
}

.section-subtitle {
  font-size: 13px;
  color: var(--color-gray-dark);
}

.form-label {
  display: block;
  font-size: 12px;
  font-weight: 500;
  color: var(--color-gray-500);
  margin-bottom: 4px;
}

.loading-state,
.error-state {
  padding: 40px;
  text-align: center;
  color: var(--color-gray-dark);
}

.retry-btn {
  margin-top: 12px;
  padding: 8px 16px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  cursor: pointer;
  font-family: var(--font-mono);
  font-size: 13px;
}

.retry-btn:hover {
  background: var(--color-gray-bg);
}

/* Layout */
.editor-layout {
  flex: 1;
  display: flex;
  gap: 16px;
  overflow: hidden;
  padding-bottom: 24px;
}

/* File list */
.file-list {
  width: 180px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.file-card {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 12px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  cursor: pointer;
  text-align: left;
  transition: all var(--duration-fast);
}

.file-card:hover {
  border-color: var(--color-gray-400);
  background: var(--color-gray-bg);
}

.file-card.active {
  border-color: var(--color-black);
  background: var(--color-black);
  color: var(--color-white);
}

.file-name {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 600;
}

.file-desc {
  font-size: 11px;
  opacity: 0.65;
}

.file-size {
  font-family: var(--font-mono);
  font-size: 10px;
  opacity: 0.45;
  margin-top: 4px;
}

.file-card.active .file-size,
.file-card.active .file-desc {
  color: var(--color-white);
}

/* Editor panel */
.editor-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  border: var(--border);
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--color-white);
}

.editor-header {
  padding: 10px 14px 8px;
  border-bottom: var(--border);
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.editor-filename {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 600;
  color: var(--color-gray-700);
}

.tab-bar {
  display: flex;
  gap: 0;
  border-bottom: var(--border);
  flex-shrink: 0;
  padding: 0 14px;
}

.tab-btn {
  padding: 7px 12px;
  border: none;
  background: transparent;
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  color: var(--color-gray-500);
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  transition: all var(--duration-fast);
}

.tab-btn:hover {
  color: var(--color-black);
}

.tab-btn.active {
  color: var(--color-black);
  border-bottom-color: var(--color-black);
}

.editor-body {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.md-editor {
  flex: 1;
  width: 100%;
  height: 100%;
  padding: 14px 16px;
  border: none;
  outline: none;
  resize: none;
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.7;
  background: var(--color-white);
  color: var(--color-black);
  box-sizing: border-box;
}

.md-preview {
  flex: 1;
  overflow-y: auto;
  padding: 14px 16px;
  font-size: 14px;
  line-height: 1.7;
}

/* Prose styles for preview */
.prose :deep(h1) {
  font-size: 18px;
  font-weight: 700;
  margin: 0 0 12px;
  font-family: var(--font-mono);
}

.prose :deep(h2) {
  font-size: 15px;
  font-weight: 600;
  margin: 16px 0 8px;
  font-family: var(--font-mono);
}

.prose :deep(h3) {
  font-size: 13px;
  font-weight: 600;
  margin: 12px 0 6px;
}

.prose :deep(p) {
  margin: 0 0 10px;
}

.prose :deep(ul), .prose :deep(ol) {
  padding-left: 20px;
  margin: 0 0 10px;
}

.prose :deep(li) {
  margin-bottom: 4px;
}

.prose :deep(code) {
  font-family: var(--font-mono);
  font-size: 12px;
  background: var(--color-gray-bg);
  padding: 1px 5px;
  border-radius: 3px;
}

/* Footer */
.editor-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-top: var(--border);
  flex-shrink: 0;
}

.save-feedback {
  font-size: 13px;
}

.save-success {
  color: #166534;
}

.save-error {
  color: #dc2626;
}

.save-btn {
  padding: 8px 18px;
  border: none;
  border-radius: var(--radius-md);
  background: var(--color-black);
  color: var(--color-white);
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast);
}

.save-btn:hover:not(:disabled) {
  background: var(--color-gray-800);
}

.save-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
