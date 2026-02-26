<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from '@/i18n'

const { t } = useI18n()

const props = defineProps<{
  alwaysConfirmTools: string[]
  dangerousKeywords: string[]
}>()

const emit = defineEmits<{
  close: []
  save: [data: { alwaysConfirmTools: string[]; dangerousKeywords: string[] }]
}>()

const editAlwaysConfirmTools = ref<string[]>([...props.alwaysConfirmTools])
const editDangerousKeywords = ref<string[]>([...props.dangerousKeywords])
const newConfirmTool = ref('')
const newDangerousKeyword = ref('')

function addConfirmTool() {
  const tool = newConfirmTool.value.trim()
  if (!tool) return
  if (editAlwaysConfirmTools.value.some(t => t.toLowerCase() === tool.toLowerCase())) return
  editAlwaysConfirmTools.value.push(tool)
  newConfirmTool.value = ''
}

function removeConfirmTool(tool: string) {
  editAlwaysConfirmTools.value = editAlwaysConfirmTools.value.filter(t => t !== tool)
}

function handleConfirmToolKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter') {
    e.preventDefault()
    addConfirmTool()
  }
}

function addDangerousKeyword() {
  const keyword = newDangerousKeyword.value.trim()
  if (!keyword) return
  if (editDangerousKeywords.value.some(k => k.toLowerCase() === keyword.toLowerCase())) return
  editDangerousKeywords.value.push(keyword)
  newDangerousKeyword.value = ''
}

function removeDangerousKeyword(keyword: string) {
  editDangerousKeywords.value = editDangerousKeywords.value.filter(k => k !== keyword)
}

function handleDangerousKeywordKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter') {
    e.preventDefault()
    addDangerousKeyword()
  }
}

function handleSave() {
  emit('save', {
    alwaysConfirmTools: editAlwaysConfirmTools.value,
    dangerousKeywords: editDangerousKeywords.value
  })
}

watch(() => [props.alwaysConfirmTools, props.dangerousKeywords], () => {
  editAlwaysConfirmTools.value = [...props.alwaysConfirmTools]
  editDangerousKeywords.value = [...props.dangerousKeywords]
}, { deep: true })
</script>

<template>
  <div class="modal-overlay" @click.self="emit('close')">
    <div class="modal-content">
      <div class="modal-header">
        <div>
          <h3 class="modal-title">{{ t('sections.commandSafety.title') }}</h3>
          <p class="modal-subtitle">{{ t('sections.commandSafety.subtitle') }}</p>
        </div>
        <button class="btn-close" @click="emit('close')">✕</button>
      </div>

      <div class="modal-body">
        <div class="domain-group">
          <label class="form-label">{{ t('sections.commandSafety.alwaysConfirmLabel') }}</label>
          <p class="field-desc">{{ t('sections.commandSafety.alwaysConfirmDesc') }}</p>
          <div class="pill-list" v-if="editAlwaysConfirmTools.length > 0">
            <span v-for="tool in editAlwaysConfirmTools" :key="tool" class="pill pill-warn">
              {{ tool }}
              <button class="pill-remove" @click="removeConfirmTool(tool)">&times;</button>
            </span>
          </div>
          <div v-else class="empty-hint">{{ t('sections.commandSafety.alwaysConfirmEmpty') }}</div>
          <div class="domain-add-row">
            <input
              v-model="newConfirmTool"
              class="form-input domain-input"
              :placeholder="t('sections.commandSafety.toolPlaceholder')"
              spellcheck="false"
              @keydown="handleConfirmToolKeydown"
            />
            <button class="add-btn" @click="addConfirmTool" :disabled="!newConfirmTool.trim()">{{ t('sections.commandSafety.addToolBtn') }}</button>
          </div>
        </div>

        <div class="domain-group">
          <label class="form-label">{{ t('sections.commandSafety.dangerKeywordsLabel') }}</label>
          <p class="field-desc">{{ t('sections.commandSafety.dangerKeywordsDesc') }}</p>
          <div class="pill-list" v-if="editDangerousKeywords.length > 0">
            <span v-for="kw in editDangerousKeywords" :key="kw" class="pill pill-warn">
              {{ kw }}
              <button class="pill-remove" @click="removeDangerousKeyword(kw)">&times;</button>
            </span>
          </div>
          <div v-else class="empty-hint">{{ t('sections.commandSafety.dangerKeywordsEmpty') }}</div>
          <div class="domain-add-row">
            <input
              v-model="newDangerousKeyword"
              class="form-input domain-input"
              :placeholder="t('sections.commandSafety.keywordPlaceholder')"
              spellcheck="false"
              @keydown="handleDangerousKeywordKeydown"
            />
            <button class="add-btn" @click="addDangerousKeyword" :disabled="!newDangerousKeyword.trim()">{{ t('sections.commandSafety.addKeywordBtn') }}</button>
          </div>
        </div>
      </div>

      <div class="modal-footer">
        <button class="btn-secondary" @click="emit('close')">{{ t('common.cancel') }}</button>
        <button class="btn-primary" @click="handleSave">{{ t('sections.commandSafety.saveBtn') }}</button>
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
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.domain-group {
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

.field-desc {
  font-size: 12px;
  color: var(--color-gray-dark);
  margin: -4px 0 0 0;
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

.pill-warn {
  background: #fefce8;
  border: 1px solid #fde68a;
  color: #92400e;
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

.domain-add-row {
  display: flex;
  gap: 8px;
}

.domain-input {
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

.add-btn {
  padding: 8px 14px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
}

.add-btn:hover:not(:disabled) {
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
