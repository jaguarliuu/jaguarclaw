<script setup lang="ts">
import { ref, watch, onMounted, computed } from 'vue'
import { useWebSocket } from '@/composables/useWebSocket'
import type { HeartbeatConfig } from '@/composables/useHeartbeatConfig'
import { useAgents } from '@/composables/useAgents'
import { useI18n } from '@/i18n'
import Select from '@/components/common/Select.vue'
import type { SelectOption } from '@/components/common/Select.vue'

const { t } = useI18n()
const { agents, loadAgents } = useAgents()
const { request } = useWebSocket()

const selectedAgentId = ref('main')

const agentOptions = computed<SelectOption[]>(() =>
  agents.value.map(a => ({ label: a.displayName || a.name, value: a.id }))
)
const config = ref<HeartbeatConfig | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const saving = ref(false)
const saveSuccess = ref(false)
const saveError = ref<string | null>(null)

const editConfig = ref<HeartbeatConfig>({
  enabled: true,
  intervalMinutes: 30,
  activeHoursStart: '09:00',
  activeHoursEnd: '22:00',
  timezone: 'Asia/Shanghai',
  ackMaxChars: 300
})

async function fetchConfig() {
  loading.value = true
  error.value = null
  config.value = null
  try {
    const result = await request<HeartbeatConfig>('heartbeat.config.get', { agentId: selectedAgentId.value })
    config.value = result
    editConfig.value = { ...result }
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Unknown error'
  } finally {
    loading.value = false
  }
}

watch(selectedAgentId, fetchConfig)

async function handleSave() {
  saving.value = true
  saveError.value = null
  saveSuccess.value = false
  try {
    await request('heartbeat.config.save', { agentId: selectedAgentId.value, config: editConfig.value })
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
  // 默认选中第一个可用 agent
  if (agents.value.length > 0) {
    selectedAgentId.value = agents.value[0]?.id ?? 'main'
  }
  await fetchConfig()
})
</script>

<template>
  <div class="heartbeat-section">
    <header class="section-header">
      <div>
        <h2 class="section-title">{{ t('settings.nav.heartbeat') }}</h2>
        <p class="section-subtitle">{{ t('sections.heartbeat.subtitle') }}</p>
      </div>
    </header>

    <!-- Agent 选择器 -->
    <div class="agent-selector-row" v-if="agents.length > 1">
      <label class="agent-selector-label">{{ t('sections.heartbeat.agentLabel') }}</label>
      <Select v-model="selectedAgentId" :options="agentOptions" class="agent-selector" />
    </div>

    <div v-if="loading && !config" class="loading-state">{{ t('sections.heartbeat.loading') }}</div>

    <div v-if="error && !config" class="error-state">
      <p>{{ error }}</p>
      <button class="retry-btn" @click="fetchConfig">{{ t('sections.heartbeat.retry') }}</button>
    </div>

    <template v-if="config">
      <div class="config-blocks">
        <!-- Enable / Disable -->
        <div class="config-block">
          <h3 class="block-title">{{ t('sections.heartbeat.blocks.status') }}</h3>
          <p class="block-desc">{{ t('sections.heartbeat.blocks.statusDesc') }}</p>

          <div class="toggle-row">
            <label class="toggle-label">
              <div class="toggle-wrapper">
                <input type="checkbox" v-model="editConfig.enabled" class="toggle-input" />
                <span class="toggle-track"></span>
              </div>
              <span>{{ editConfig.enabled ? t('sections.heartbeat.fields.enabledLabel') : t('sections.heartbeat.fields.disabledLabel') }}</span>
            </label>
          </div>
        </div>

        <!-- Interval -->
        <div class="config-block">
          <h3 class="block-title">{{ t('sections.heartbeat.blocks.interval') }}</h3>
          <p class="block-desc">{{ t('sections.heartbeat.blocks.intervalDesc') }}</p>

          <div class="form-group">
            <label class="form-label">{{ t('sections.heartbeat.fields.intervalLabel') }}</label>
            <input
              v-model.number="editConfig.intervalMinutes"
              type="number"
              min="1"
              max="1440"
              class="form-input form-input-narrow"
              placeholder="30"
            />
            <p class="form-help">{{ t('sections.heartbeat.fields.intervalHelp') }}</p>
          </div>
        </div>

        <!-- Active Hours -->
        <div class="config-block">
          <h3 class="block-title">{{ t('sections.heartbeat.blocks.activeHours') }}</h3>
          <p class="block-desc">{{ t('sections.heartbeat.blocks.activeHoursDesc') }}</p>

          <div class="time-row">
            <div class="form-group">
              <label class="form-label">{{ t('sections.heartbeat.fields.startLabel') }}</label>
              <input
                v-model="editConfig.activeHoursStart"
                type="time"
                class="form-input form-input-narrow"
              />
            </div>
            <span class="time-sep">—</span>
            <div class="form-group">
              <label class="form-label">{{ t('sections.heartbeat.fields.endLabel') }}</label>
              <input
                v-model="editConfig.activeHoursEnd"
                type="time"
                class="form-input form-input-narrow"
              />
            </div>
          </div>

          <div class="form-group">
            <label class="form-label">{{ t('sections.heartbeat.fields.timezoneLabel') }}</label>
            <input
              v-model="editConfig.timezone"
              type="text"
              class="form-input"
              placeholder="Asia/Shanghai"
            />
            <p class="form-help">{{ t('sections.heartbeat.fields.timezoneHelp') }}</p>
          </div>
        </div>

        <!-- Silent ACK -->
        <div class="config-block">
          <h3 class="block-title">{{ t('sections.heartbeat.blocks.silentAck') }}</h3>
          <p class="block-desc">{{ t('sections.heartbeat.blocks.silentAckDesc') }}</p>

          <div class="form-group">
            <label class="form-label">{{ t('sections.heartbeat.fields.ackMaxCharsLabel') }}</label>
            <input
              v-model.number="editConfig.ackMaxChars"
              type="number"
              min="10"
              max="2000"
              class="form-input form-input-narrow"
              placeholder="300"
            />
          </div>
        </div>
      </div>

      <div v-if="saveSuccess" class="save-success">{{ t('sections.heartbeat.savedSuccess') }}</div>
      <div v-if="saveError" class="save-error">{{ saveError }}</div>

      <div class="form-actions">
        <button
          class="save-btn"
          :disabled="saving"
          @click="handleSave"
        >
          {{ saving ? t('sections.heartbeat.savingBtn') : t('sections.heartbeat.saveBtn') }}
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.heartbeat-section {
  height: 100%;
  overflow-y: auto;
  padding: 24px 32px;
}

.section-header {
  margin-bottom: 24px;
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

.agent-selector-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
  padding: 12px 16px;
  background: var(--color-gray-50, #f9fafb);
  border: var(--border);
  border-radius: var(--radius-md);
  max-width: 800px;
}

.agent-selector-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-gray-700);
  white-space: nowrap;
}

.agent-selector {
  min-width: 180px;
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

.config-blocks {
  max-width: 800px;
}

.config-block {
  margin-bottom: 28px;
  padding-bottom: 28px;
  border-bottom: var(--border-light);
}

.config-block:last-child {
  border-bottom: none;
}

.block-title {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 4px;
}

.block-desc {
  font-size: 13px;
  color: var(--color-gray-dark);
  margin-bottom: 16px;
}

.form-group {
  margin-bottom: 16px;
}

.form-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-gray-700);
  margin-bottom: 6px;
}

.form-input {
  width: 100%;
  padding: 10px 12px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: inherit;
  font-size: 14px;
  box-sizing: border-box;
}

.form-input-narrow {
  width: 160px;
}

.form-input:focus {
  outline: none;
  border-color: var(--color-black);
  box-shadow: 0 0 0 3px rgba(0, 0, 0, 0.03);
}

.form-help {
  font-size: 12px;
  color: var(--color-gray-600);
  margin: 4px 0 0 0;
}

.toggle-row {
  display: flex;
  align-items: center;
}

.toggle-label {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  font-size: 14px;
}

.toggle-wrapper {
  position: relative;
  display: inline-block;
}

.toggle-input {
  opacity: 0;
  width: 0;
  height: 0;
  position: absolute;
}

.toggle-track {
  display: block;
  width: 40px;
  height: 22px;
  border-radius: 11px;
  background: var(--color-gray-300);
  transition: background var(--duration-fast);
  cursor: pointer;
}

.toggle-track::after {
  content: '';
  position: absolute;
  top: 3px;
  left: 3px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: white;
  transition: transform var(--duration-fast);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
}

.toggle-input:checked + .toggle-track {
  background: var(--color-black);
}

.toggle-input:checked + .toggle-track::after {
  transform: translateX(18px);
}

.time-row {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  margin-bottom: 16px;
}

.time-sep {
  padding-bottom: 10px;
  color: var(--color-gray-500);
  font-size: 14px;
}

.save-success {
  padding: 10px 14px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: var(--radius-md);
  color: #166534;
  font-size: 13px;
  margin-bottom: 16px;
  max-width: 800px;
}

.save-error {
  padding: 10px 14px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: var(--radius-md);
  color: #dc2626;
  font-size: 13px;
  margin-bottom: 16px;
  max-width: 800px;
}

.form-actions {
  margin-top: 24px;
}

.save-btn {
  padding: 10px 20px;
  border: none;
  border-radius: var(--radius-md);
  background: var(--color-black);
  color: var(--color-white);
  font-family: var(--font-mono);
  font-size: 14px;
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
