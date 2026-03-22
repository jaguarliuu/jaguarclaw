<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useSchedules } from '@/composables/useSchedules'
import { useI18n } from '@/i18n'
import Select from '@/components/common/Select.vue'
import ScheduleRunDetailModal from './ScheduleRunDetailModal.vue'
import type { SelectOption } from '@/components/common/Select.vue'
import type { DeliveryTargetType, ScheduleInfo, ScheduleRunDetail, ScheduleRunLog } from '@/types'

const {
  schedules, loading, error,
  runHistory, runHistoryLoading, runDetails, runDetailLoading,
  loadSchedules, createSchedule, updateSchedule, removeSchedule,
  toggleSchedule, runSchedule, loadRunHistory, loadRunDetail
} = useSchedules()
const { t } = useI18n()

// Form state
const showForm = ref(false)
const editingTaskId = ref<string | null>(null)
const formName = ref('')
const formCron = ref('')
const formPrompt = ref('')
const formTargetType = ref<DeliveryTargetType>('webhook')
const formTargetRef = ref('')
const formEmailTo = ref('')
const formEmailCc = ref('')
const formEnabled = ref(true)
const formError = ref<string | null>(null)
const submitting = ref(false)

// UI state
const runningTasks = ref<Set<string>>(new Set())
const confirmDeleteId = ref<string | null>(null)
const isEditing = computed(() => editingTaskId.value !== null)

const expandedHistory = ref<Set<string>>(new Set())
const selectedRun = ref<ScheduleRunLog | null>(null)
const detailError = ref<string | null>(null)

function toggleHistory(taskId: string) {
  const next = new Set(expandedHistory.value)
  if (next.has(taskId)) {
    next.delete(taskId)
  } else {
    next.add(taskId)
    loadRunHistory(taskId)
  }
  expandedHistory.value = next
}

const selectedRunDetail = computed(() => {
  if (!selectedRun.value) return null
  const detail = runDetails.value.get(selectedRun.value.id)
  if (!detail) return null
  return JSON.parse(JSON.stringify(detail)) as ScheduleRunDetail
})

const selectedRunLoading = computed(() => (
  selectedRun.value ? runDetailLoading.value.has(selectedRun.value.id) : false
))

async function openRunDetail(run: ScheduleRunLog) {
  selectedRun.value = run
  detailError.value = null
  try {
    await loadRunDetail(run.id)
  } catch (e) {
    detailError.value = e instanceof Error ? e.message : t('sections.schedules.detailModal.loadFailed')
  }
}

function closeRunDetail() {
  selectedRun.value = null
  detailError.value = null
}

// Cron presets
const cronPresets = computed(() => [
  { label: t('sections.schedules.presets.everyHour'), cron: '0 * * * *' },
  { label: t('sections.schedules.presets.daily9'), cron: '0 9 * * *' },
  { label: t('sections.schedules.presets.daily18'), cron: '0 18 * * *' },
  { label: t('sections.schedules.presets.mon9'), cron: '0 9 * * 1' },
  { label: t('sections.schedules.presets.firstOfMonth'), cron: '0 9 1 * *' }
])

const isEmailTarget = computed(() => formTargetType.value === 'email')
const targetTypeOptions = computed<SelectOption<string>[]>(() => [
  { label: 'Webhook', value: 'webhook' },
  { label: 'Email', value: 'email' }
])
const EMAIL_SPLIT_PATTERN = /[,，;\n；]+/
const EMAIL_ADDRESS_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function resetForm() {
  editingTaskId.value = null
  formName.value = ''
  formCron.value = ''
  formPrompt.value = ''
  formTargetType.value = 'webhook'
  formTargetRef.value = ''
  formEmailTo.value = ''
  formEmailCc.value = ''
  formEnabled.value = true
  formError.value = null
}

function openForm() {
  resetForm()
  showForm.value = true
}

function editTask(task: ScheduleInfo) {
  editingTaskId.value = task.id
  formName.value = task.name
  formCron.value = task.cronExpr
  formPrompt.value = task.prompt
  formTargetType.value = task.targetType
  formTargetRef.value = task.targetType === 'email' ? '' : task.targetRef
  formEmailTo.value = task.emailTo ?? ''
  formEmailCc.value = task.emailCc ?? ''
  formEnabled.value = task.enabled
  formError.value = null
  showForm.value = true
}

function closeForm() {
  showForm.value = false
  resetForm()
}

function applyPreset(cron: string) {
  formCron.value = cron
}

function getCronDescription(cron: string): string {
  if (!cron) return ''
  const parts = cron.split(/\s+/)
  if (parts.length !== 5) return t('sections.schedules.cronDescriptions.invalid')

  const [min, hour, dom, mon, dow] = parts

  // Simple descriptions for common patterns
  if (min === '0' && hour === '*' && dom === '*' && mon === '*' && dow === '*') return t('sections.schedules.cronDescriptions.everyHour')
  if (min === '0' && hour !== '*' && dom === '*' && mon === '*' && dow === '*') return t('sections.schedules.cronDescriptions.daily', { h: String(hour) })
  if (min !== '*' && hour !== '*' && dom === '*' && mon === '*' && dow !== '*') {
    const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
    const dayName = days[parseInt(dow!)] || `day ${dow}`
    return t('sections.schedules.cronDescriptions.weekly', { day: dayName, h: String(hour) })
  }
  if (min === '0' && hour !== '*' && dom !== '*' && mon === '*' && dow === '*') return t('sections.schedules.cronDescriptions.monthly', { date: String(dom), h: String(hour) })
  return `${min} ${hour} ${dom} ${mon} ${dow}`
}

function getStatusText(task: ScheduleInfo): string {
  if (task.lastRunSuccess === null) return t('sections.schedules.neverRun')
  return task.lastRunSuccess ? t('sections.schedules.success') : t('sections.schedules.failed')
}

function getStatusClass(task: ScheduleInfo): string {
  if (task.lastRunSuccess === null) return 'status-unknown'
  return task.lastRunSuccess ? 'status-ok' : 'status-fail'
}

function formatTime(dateStr: string | null): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return d.toLocaleString()
}

function normalizeEmailList(value: string): string | undefined {
  const emails = value
    .split(EMAIL_SPLIT_PATTERN)
    .map((item) => item.trim())
    .filter(Boolean)
  return emails.length > 0 ? emails.join(',') : undefined
}

function hasInvalidEmailList(value: string): boolean {
  const emails = value
    .split(EMAIL_SPLIT_PATTERN)
    .map((item) => item.trim())
    .filter(Boolean)

  return emails.some((email) => !EMAIL_ADDRESS_PATTERN.test(email))
}

async function handleSubmit() {
  const normalizedEmailTo = normalizeEmailList(formEmailTo.value)
  const normalizedEmailCc = normalizeEmailList(formEmailCc.value)

  if (!formName.value.trim()) {
    formError.value = t('sections.schedules.errors.nameRequired')
    return
  }
  if (!formCron.value.trim()) {
    formError.value = t('sections.schedules.errors.cronRequired')
    return
  }
  if (!formPrompt.value.trim()) {
    formError.value = t('sections.schedules.errors.promptRequired')
    return
  }
  if (!formTargetType.value) {
    formError.value = 'Target type is required'
    return
  }
  if (formTargetType.value === 'webhook' && !formTargetRef.value.trim()) {
    formError.value = 'Webhook target alias is required'
    return
  }
  if (isEmailTarget.value && !normalizedEmailTo) {
    formError.value = t('sections.schedules.errors.emailToRequired')
    return
  }
  if (isEmailTarget.value && (hasInvalidEmailList(formEmailTo.value) || hasInvalidEmailList(formEmailCc.value))) {
    formError.value = t('sections.schedules.errors.invalidEmailList')
    return
  }

  submitting.value = true
  formError.value = null
  try {
    const payload = {
      name: formName.value.trim(),
      cronExpr: formCron.value.trim(),
      prompt: formPrompt.value.trim(),
      targetRef: formTargetType.value === 'email' ? 'email-default' : formTargetRef.value.trim(),
      targetType: formTargetType.value,
      emailTo: isEmailTarget.value ? normalizedEmailTo : undefined,
      emailCc: isEmailTarget.value ? normalizedEmailCc : undefined
    }

    if (isEditing.value && editingTaskId.value) {
      await updateSchedule({
        id: editingTaskId.value,
        enabled: formEnabled.value,
        ...payload
      })
    } else {
      await createSchedule(payload)
    }
    closeForm()
  } catch (e) {
    formError.value = e instanceof Error
      ? e.message
      : isEditing.value
        ? t('sections.schedules.errors.failedToUpdate')
        : t('sections.schedules.errors.failedToCreate')
  } finally {
    submitting.value = false
  }
}

async function handleToggle(task: ScheduleInfo) {
  try {
    await toggleSchedule(task.id, !task.enabled)
  } catch {
    // Error handled in composable
  }
}

async function handleRun(taskId: string) {
  runningTasks.value = new Set(runningTasks.value).add(taskId)
  // Auto-expand history when running
  expandedHistory.value = new Set(expandedHistory.value).add(taskId)
  try {
    await runSchedule(taskId)
  } catch {
    // Error handled in composable
  } finally {
    const next = new Set(runningTasks.value)
    next.delete(taskId)
    runningTasks.value = next
  }
}

async function handleDelete(taskId: string) {
  try {
    await removeSchedule(taskId)
  } catch {
    // Error handled in composable
  } finally {
    confirmDeleteId.value = null
  }
}

function formatDuration(ms: number | null): string {
  if (ms == null) return '-'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

function getRunStatusIcon(status: string): string {
  if (status === 'running') return '⟳'
  if (status === 'success') return '✓'
  return '✗'
}

function getRunStatusClass(status: string): string {
  if (status === 'running') return 'run-status-running'
  if (status === 'success') return 'run-status-success'
  return 'run-status-failed'
}

onMounted(() => {
  loadSchedules()
})
</script>

<template>
  <div class="schedules-section">
    <header class="section-header">
      <div class="header-top">
        <div>
          <h2 class="section-title">{{ t('settings.nav.schedules') }}</h2>
          <p class="section-subtitle">{{ t('sections.schedules.subtitle') }}</p>
        </div>
        <button class="add-btn" @click="showForm ? closeForm() : openForm()">
          {{ showForm ? t('common.cancel') : t('sections.schedules.addBtn') }}
        </button>
      </div>
    </header>

    <!-- Loading -->
    <div v-if="loading && schedules.length === 0" class="loading-state">
      {{ t('sections.schedules.loading') }}
    </div>

    <!-- Error -->
    <div v-if="error && schedules.length === 0" class="error-state">
      <p>{{ error }}</p>
      <button class="retry-btn" @click="loadSchedules">{{ t('common.retry') }}</button>
    </div>

    <!-- Add Form -->
    <div v-if="showForm" class="form-panel">
      <h3 class="form-title">
        {{ isEditing ? t('sections.schedules.editFormTitle') : t('sections.schedules.formTitle') }}
      </h3>

      <div class="form-group">
        <label class="form-label">{{ t('sections.schedules.fields.nameLabel') }}</label>
        <input v-model="formName" class="form-input" :placeholder="t('sections.schedules.fields.namePlaceholder')" />
      </div>

      <div class="form-group">
        <label class="form-label">{{ t('sections.schedules.fields.cronLabel') }}</label>
        <input v-model="formCron" class="form-input" :placeholder="t('sections.schedules.fields.cronPlaceholder')" spellcheck="false" />
        <div class="cron-presets">
          <button
            v-for="preset in cronPresets"
            :key="preset.cron"
            class="preset-btn"
            :class="{ active: formCron === preset.cron }"
            @click="applyPreset(preset.cron)"
          >
            {{ preset.label }}
          </button>
        </div>
        <div v-if="formCron" class="cron-description">{{ getCronDescription(formCron) }}</div>
      </div>

      <div class="form-group">
        <label class="form-label">{{ t('sections.schedules.fields.promptLabel') }}</label>
        <textarea
          v-model="formPrompt"
          class="form-textarea"
          rows="4"
          :placeholder="t('sections.schedules.fields.promptPlaceholder')"
          spellcheck="false"
        />
      </div>

      <div class="form-group">
        <label class="form-label">Target Type *</label>
        <Select v-model="formTargetType" :options="targetTypeOptions" />
      </div>

      <div v-if="formTargetType === 'webhook'" class="form-group">
        <label class="form-label">Webhook Alias *</label>
        <input v-model="formTargetRef" class="form-input" placeholder="e.g. ops-alert" />
      </div>

      <!-- Email fields -->
      <template v-if="isEmailTarget">
        <div class="form-grid">
          <div class="form-group">
            <label class="form-label">{{ t('sections.schedules.fields.toLabel') }}</label>
            <input v-model="formEmailTo" class="form-input" :placeholder="t('sections.schedules.fields.toPlaceholder')" />
          </div>
          <div class="form-group">
            <label class="form-label">{{ t('sections.schedules.fields.ccLabel') }}</label>
            <input v-model="formEmailCc" class="form-input" :placeholder="t('sections.schedules.fields.ccPlaceholder')" />
          </div>
        </div>
        <div class="form-hint">{{ t('sections.schedules.fields.multiAddressHint') }}</div>
      </template>

      <div v-if="formError" class="form-error">{{ formError }}</div>

      <div class="form-actions">
        <button class="cancel-btn" @click="closeForm">{{ t('common.cancel') }}</button>
        <button class="submit-btn" :disabled="submitting" @click="handleSubmit">
          {{
            submitting
              ? (isEditing ? t('sections.schedules.savingBtn') : t('sections.schedules.creatingBtn'))
              : (isEditing ? t('sections.schedules.saveBtn') : t('sections.schedules.createBtn'))
          }}
        </button>
      </div>
    </div>

    <!-- Task List -->
    <div v-if="schedules.length > 0" class="task-list">
      <div v-for="task in schedules" :key="task.id" class="task-card" :class="{ disabled: !task.enabled }">
        <div class="task-header">
          <div class="task-info">
            <span class="task-name">{{ task.name }}</span>
            <span class="type-badge" :class="'badge-' + task.targetType">
              {{ task.targetType }}
            </span>
            <span class="status-dot" :class="getStatusClass(task)" :title="getStatusText(task)" />
          </div>
          <div class="task-actions">
            <button
              class="history-btn"
              :class="{ active: expandedHistory.has(task.id) }"
              @click="toggleHistory(task.id)"
            >
              {{ t('sections.schedules.historyBtn') }}
            </button>
            <button
              class="edit-btn"
              @click="editTask(task)"
            >
              {{ t('common.edit') }}
            </button>
            <button
              class="run-btn"
              :disabled="runningTasks.has(task.id)"
              @click="handleRun(task.id)"
            >
              {{ runningTasks.has(task.id) ? t('sections.schedules.runningBtn') : t('sections.schedules.runNowBtn') }}
            </button>
            <button
              class="toggle-btn"
              :class="{ 'toggle-on': task.enabled, 'toggle-off': !task.enabled }"
              @click="handleToggle(task)"
            >
              {{ task.enabled ? t('sections.schedules.enabledStatus') : t('sections.schedules.disabledStatus') }}
            </button>
            <button
              v-if="confirmDeleteId !== task.id"
              class="delete-btn"
              @click="confirmDeleteId = task.id"
            >
              {{ t('common.delete') }}
            </button>
            <template v-else>
              <button class="confirm-delete-btn" @click="handleDelete(task.id)">{{ t('common.confirm') }}</button>
              <button class="cancel-delete-btn" @click="confirmDeleteId = null">{{ t('common.cancel') }}</button>
            </template>
          </div>
        </div>
        <div class="task-details">
          <span class="task-detail">
            <span class="detail-label">{{ t('sections.schedules.detail.cron') }}</span> {{ task.cronExpr }}
            <span class="cron-hint">{{ getCronDescription(task.cronExpr) }}</span>
          </span>
          <span class="task-detail">
            <span class="detail-label">Target</span> {{ task.targetRef }}
          </span>
          <span v-if="task.lastRunAt" class="task-detail">
            <span class="detail-label">{{ t('sections.schedules.detail.lastRun') }}</span> {{ formatTime(task.lastRunAt) }}
          </span>
        </div>
        <div class="task-prompt">
          <span class="detail-label">{{ t('sections.schedules.detail.prompt') }}</span> {{ task.prompt.length > 120 ? task.prompt.substring(0, 120) + '...' : task.prompt }}
        </div>
        <div v-if="task.lastRunError" class="task-error">
          {{ task.lastRunError }}
        </div>
        <!-- Run History -->
        <div v-if="expandedHistory.has(task.id)" class="run-history">
          <div v-if="runHistoryLoading.has(task.id)" class="run-history-loading">
            {{ t('sections.schedules.historyLoading') }}
          </div>
          <template v-else>
            <div v-if="!runHistory.get(task.id)?.length" class="run-history-empty">
              {{ t('sections.schedules.historyEmpty') }}
            </div>
            <div
              v-for="run in runHistory.get(task.id)"
              :key="run.id"
              class="run-entry"
              :class="getRunStatusClass(run.status)"
            >
              <span class="run-status-icon">{{ getRunStatusIcon(run.status) }}</span>
              <span class="run-trigger">{{ run.triggeredBy }}</span>
              <span class="run-time">{{ formatTime(run.startedAt) }}</span>
              <span class="run-duration">{{ formatDuration(run.durationMs) }}</span>
              <button class="run-detail-btn" @click="openRunDetail(run)">
                {{ t('sections.schedules.viewDetailBtn') }}
              </button>
              <span v-if="run.errorMessage" class="run-error-msg" :title="run.errorMessage">
                {{ run.errorMessage.length > 60 ? run.errorMessage.substring(0, 60) + '...' : run.errorMessage }}
              </span>
            </div>
          </template>
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div v-if="!loading && !showForm && schedules.length === 0 && !error" class="empty-state">
      <p>{{ t('sections.schedules.empty') }}</p>
      <p class="empty-hint">{{ t('sections.schedules.emptyHint') }}</p>
    </div>

    <!-- Global Error -->
    <div v-if="error && schedules.length > 0" class="error-banner">{{ error }}</div>

    <ScheduleRunDetailModal
      :show="selectedRun !== null"
      :run="selectedRun"
      :detail="selectedRunDetail"
      :loading="selectedRunLoading"
      :error-message="detailError"
      @close="closeRunDetail"
    />
  </div>
</template>

<style scoped>
.schedules-section {
  height: 100%;
  overflow-y: auto;
  padding: 24px 32px;
}

.section-header {
  margin-bottom: 24px;
}

.header-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.section-title {
  font-family: var(--font-mono);
  font-size: 20px;
  font-weight: 500;
  margin-bottom: 4px;
}

.section-subtitle {
  font-size: 14px;
  color: var(--color-gray-dark);
}

.add-btn {
  padding: 8px 16px;
  border: none;
  border-radius: var(--radius-md);
  background: var(--color-black);
  color: var(--color-white);
  font-family: var(--font-mono);
  font-size: 13px;
  cursor: pointer;
  transition: opacity 0.15s ease;
}

.add-btn:hover {
  opacity: 0.9;
}

/* Form */
.form-panel {
  margin-bottom: 24px;
  padding: 20px;
  border: var(--border);
  border-radius: var(--radius-lg);
  background: var(--color-gray-bg);
}

.form-title {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 16px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 12px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 12px;
}

.form-label {
  font-size: 11px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-gray-dark);
}

.form-input {
  padding: 8px 10px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 13px;
}

.form-textarea {
  width: 100%;
  padding: 8px 10px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 13px;
  resize: vertical;
}

.cron-presets {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 6px;
}

.preset-btn {
  padding: 3px 8px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 11px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.preset-btn:hover {
  background: var(--color-gray-bg);
}

.preset-btn.active {
  background: var(--color-black);
  color: var(--color-white);
  border-color: var(--color-black);
}

.cron-description {
  font-size: 12px;
  color: var(--color-gray-dark);
  font-family: var(--font-mono);
  margin-top: 4px;
}

.form-error {
  padding: 8px 12px;
  margin-bottom: 12px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: var(--radius-md);
  color: #dc2626;
  font-size: 13px;
}

.form-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.form-hint {
  margin-top: -4px;
  margin-bottom: 12px;
  color: var(--color-gray-dark);
  font-size: 12px;
}

.cancel-btn {
  padding: 8px 16px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 13px;
  cursor: pointer;
}

.cancel-btn:hover {
  background: var(--color-gray-bg);
}

.submit-btn {
  padding: 8px 16px;
  border: none;
  border-radius: var(--radius-md);
  background: var(--color-black);
  color: var(--color-white);
  font-family: var(--font-mono);
  font-size: 13px;
  cursor: pointer;
}

.submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* Task List */
.task-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.task-card {
  padding: 14px 16px;
  border: var(--border);
  border-radius: var(--radius-lg);
  background: var(--color-white);
  transition: border-color 0.15s ease;
}

.task-card:hover {
  border-color: var(--color-black);
}

.task-card.disabled {
  opacity: 0.6;
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.task-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.task-name {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 500;
}

.type-badge {
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.badge-email {
  background: #dbeafe;
  color: #1d4ed8;
}

.badge-webhook {
  background: #fef3c7;
  color: #b45309;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-ok {
  background: #22c55e;
}

.status-fail {
  background: #ef4444;
}

.status-unknown {
  background: #d1d5db;
}

.task-actions {
  display: flex;
  gap: 6px;
}

.edit-btn, .run-btn, .toggle-btn, .delete-btn, .confirm-delete-btn, .cancel-delete-btn {
  padding: 4px 10px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 12px;
  cursor: pointer;
}

.edit-btn:hover, .run-btn:hover, .delete-btn:hover, .cancel-delete-btn:hover {
  background: var(--color-gray-bg);
}

.run-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.toggle-on {
  background: #dcfce7;
  color: #15803d;
  border-color: #bbf7d0;
}

.toggle-on:hover {
  background: #bbf7d0;
}

.toggle-off {
  background: #f3f4f6;
  color: #6b7280;
}

.toggle-off:hover {
  background: #e5e7eb;
}

.confirm-delete-btn {
  background: #dc2626;
  color: var(--color-white);
  border-color: #dc2626;
}

.confirm-delete-btn:hover {
  background: #b91c1c;
}

.task-details {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  font-size: 12px;
  color: var(--color-gray-dark);
  margin-bottom: 4px;
}

.task-detail {
  font-family: var(--font-mono);
}

.detail-label {
  color: var(--color-gray-dark);
  font-weight: 500;
}

.cron-hint {
  color: #9ca3af;
  margin-left: 4px;
}

.task-prompt {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-gray-dark);
  margin-top: 4px;
  line-height: 1.4;
}

.task-error {
  margin-top: 6px;
  padding: 6px 10px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: var(--radius-md);
  color: #dc2626;
  font-family: var(--font-mono);
  font-size: 12px;
}

/* States */
.loading-state,
.error-state,
.empty-state {
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

.empty-hint {
  font-size: 13px;
  margin-top: 8px;
}

.error-banner {
  margin-top: 16px;
  padding: 12px 16px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: var(--radius-md);
  color: #dc2626;
  font-size: 13px;
}

/* Run History */
.history-btn {
  padding: 4px 10px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 12px;
  cursor: pointer;
}

.history-btn:hover {
  background: var(--color-gray-bg);
}

.history-btn.active {
  background: var(--color-gray-bg);
  border-color: var(--color-gray-dark);
}

.run-history {
  margin-top: 10px;
  padding-top: 10px;
  border-top: var(--border);
}

.run-history-loading,
.run-history-empty {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-gray-dark);
  padding: 4px 0;
}

.run-entry {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  font-family: var(--font-mono);
  font-size: 12px;
  border-bottom: 1px solid var(--color-gray-100, #f3f4f6);
}

.run-entry:last-child {
  border-bottom: none;
}

.run-status-icon {
  font-size: 14px;
  width: 16px;
  text-align: center;
}

.run-status-running .run-status-icon { color: #f59e0b; }
.run-status-success .run-status-icon { color: #22c55e; }
.run-status-failed  .run-status-icon { color: #ef4444; }

.run-trigger {
  padding: 1px 5px;
  border-radius: var(--radius-sm);
  font-size: 10px;
  font-weight: 500;
  text-transform: uppercase;
  background: #f3f4f6;
  color: #6b7280;
}

.run-time {
  color: var(--color-gray-dark);
  flex: 1;
}

.run-duration {
  color: var(--color-gray-dark);
  min-width: 50px;
  text-align: right;
}

.run-detail-btn {
  padding: 2px 8px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 11px;
  cursor: pointer;
}

.run-detail-btn:hover {
  background: var(--color-gray-bg);
}

.run-error-msg {
  color: #ef4444;
  font-size: 11px;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
