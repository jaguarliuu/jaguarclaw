<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useI18n } from '@/i18n'

const { t } = useI18n()

const isElectron = computed(() => typeof window !== 'undefined' && !!(window as any).electron?.selectFolder)

const props = defineProps<{
  paths: string[]
}>()

const emit = defineEmits<{
  close: []
  save: [paths: string[]]
}>()

const editPaths = ref<string[]>([...props.paths])
const newPath = ref('')

async function browsePath() {
  const selected = await (window as any).electron?.selectFolder()
  if (selected) {
    addPathValue(selected)
  }
}

function addPathValue(value: string) {
  const p = value.trim()
  if (!p) return
  if (editPaths.value.includes(p)) return
  editPaths.value.push(p)
  newPath.value = ''
}

function addPath() {
  addPathValue(newPath.value)
}

function removePath(path: string) {
  editPaths.value = editPaths.value.filter(p => p !== path)
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter') {
    e.preventDefault()
    addPath()
  }
}

function handleSave() {
  emit('save', editPaths.value)
}

watch(() => props.paths, (newVal) => {
  editPaths.value = [...newVal]
}, { deep: true })
</script>

<template>
  <div class="modal-overlay" @click.self="emit('close')">
    <div class="modal-content">
      <div class="modal-header">
        <div>
          <h3 class="modal-title">{{ t('sections.trustedPaths.title') }}</h3>
          <p class="modal-subtitle">{{ t('sections.trustedPaths.subtitle') }}</p>
        </div>
        <button class="btn-close" @click="emit('close')">✕</button>
      </div>

      <div class="modal-body">
        <div class="path-group">
          <label class="form-label">{{ t('sections.trustedPaths.pathsLabel') }}</label>
          <p class="form-help">{{ t('sections.trustedPaths.pathsHelp') }}</p>
          <div class="pill-list" v-if="editPaths.length > 0">
            <span v-for="p in editPaths" :key="p" class="pill pill-path">
              {{ p }}
              <button class="pill-remove" @click="removePath(p)">&times;</button>
            </span>
          </div>
          <div v-else class="empty-hint">{{ t('sections.trustedPaths.empty') }}</div>
          <div class="path-add-row">
            <input
              v-model="newPath"
              class="form-input path-input"
              :placeholder="t('sections.trustedPaths.placeholder')"
              spellcheck="false"
              @keydown="handleKeydown"
            />
            <button class="add-btn" @click="addPath" :disabled="!newPath.trim()">{{ t('sections.trustedPaths.addBtn') }}</button>
            <button v-if="isElectron" class="browse-btn" @click="browsePath">{{ t('sections.trustedPaths.browseBtn') }}</button>
          </div>
        </div>
      </div>

      <div class="modal-footer">
        <button class="btn-secondary" @click="emit('close')">{{ t('common.cancel') }}</button>
        <button class="btn-primary" @click="handleSave">{{ t('sections.trustedPaths.saveBtn') }}</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: var(--color-white);
  border-radius: var(--radius-lg);
  width: 90%;
  max-width: 600px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-lg);
}

.modal-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: var(--border);
  gap: 20px;
}

.modal-title {
  font-family: var(--font-mono);
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 4px 0;
}

.modal-subtitle {
  font-size: 13px;
  color: var(--color-gray-600);
  margin: 0;
}

.btn-close {
  background: none;
  border: none;
  font-size: 20px;
  cursor: pointer;
  color: var(--color-gray-400);
  padding: 4px;
  line-height: 1;
  flex-shrink: 0;
}

.btn-close:hover {
  color: var(--color-black);
}

.modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.path-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.form-label {
  display: block;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-gray-700);
}

.form-help {
  font-size: 12px;
  color: var(--color-gray-600);
  margin: 0;
}

.pill-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: var(--radius-sm);
  font-family: var(--font-mono);
  font-size: 12px;
}

.pill-path {
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  color: #1e40af;
}

.pill-remove {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
  color: inherit;
  padding: 0 2px;
  opacity: 0.6;
}

.pill-remove:hover {
  opacity: 1;
}

.empty-hint {
  font-size: 12px;
  color: var(--color-gray-dark);
  font-style: italic;
}

.path-add-row {
  display: flex;
  gap: 8px;
}

.path-input {
  flex: 1;
}

.form-input {
  padding: 8px 10px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 13px;
  width: 100%;
  box-sizing: border-box;
}

.form-input:focus {
  outline: none;
  border-color: var(--color-black);
  box-shadow: 0 0 0 3px rgba(0,0,0,0.03);
}

.add-btn,
.browse-btn {
  padding: 8px 14px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
}

.add-btn:hover:not(:disabled),
.browse-btn:hover {
  background: var(--color-gray-bg);
  border-color: var(--color-black);
}

.add-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.modal-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 16px 24px;
  border-top: var(--border);
  gap: 12px;
}

.btn-secondary {
  padding: 10px 16px;
  background: var(--color-white);
  border: var(--border);
  border-radius: var(--radius-md);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast);
}

.btn-secondary:hover {
  background: var(--color-gray-100);
}

.btn-primary {
  padding: 10px 16px;
  background: var(--color-black);
  color: var(--color-white);
  border: none;
  border-radius: var(--radius-md);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast);
}

.btn-primary:hover {
  background: var(--color-gray-800);
}
</style>
