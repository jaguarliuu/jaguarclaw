<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useConfirm } from '@/composables/useConfirm'
import { useAgents, type AgentProfilePayload } from '@/composables/useAgents'
import { useLlmConfig } from '@/composables/useLlmConfig'
import { useI18n } from '@/i18n'
import type { AgentProfile } from '@/types'
import Select from '@/components/common/Select.vue'
import type { SelectOption } from '@/components/common/Select.vue'

const { t } = useI18n()
const router = useRouter()
const { confirm } = useConfirm()
const { agents, loading, saving, error, loadAgents, createAgent, updateAgent, deleteAgent } = useAgents()
const { getConfig, getAllModelOptions } = useLlmConfig()

const showModal = ref(false)
const modalMode = ref<'create' | 'edit'>('create')
const editingAgentId = ref<string | null>(null)
const formError = ref<string | null>(null)
const modelLoading = ref(false)

const form = ref({
  displayName: '',
  model: '',
  enabled: true,
  isDefault: false,
})

const sortedAgents = computed(() =>
  [...agents.value].sort((a, b) => {
    if (a.isDefault && !b.isDefault) return -1
    if (!a.isDefault && b.isDefault) return 1
    return a.createdAt.localeCompare(b.createdAt)
  }),
)

const modelOptions = computed<SelectOption[]>(() =>
  getAllModelOptions().map((option) => ({
    value: `${option.providerId}:${option.modelName}`,
    label: `${option.providerName} / ${option.modelName}`,
  })),
)

onMounted(async () => {
  await Promise.all([loadAgents(), refreshModelOptions()])
})

async function refreshModelOptions() {
  modelLoading.value = true
  try {
    await getConfig()
  } catch (err) {
    console.error('Failed to load model options for agents:', err)
  } finally {
    modelLoading.value = false
  }
}

function resetForm() {
  form.value = {
    displayName: '',
    model: '',
    enabled: true,
    isDefault: false,
  }
  formError.value = null
}

async function openCreateModal() {
  modalMode.value = 'create'
  editingAgentId.value = null
  resetForm()
  await refreshModelOptions()
  showModal.value = true
}

async function openEditModal(agent: AgentProfile) {
  modalMode.value = 'edit'
  editingAgentId.value = agent.id
  form.value = {
    displayName: agent.displayName || agent.name,
    model: agent.model || '',
    enabled: agent.enabled,
    isDefault: agent.isDefault,
  }
  formError.value = null
  await refreshModelOptions()
  showModal.value = true
}

function openPersonaConfig(agentId: string) {
  router.push({
    path: '/settings/soul',
    query: { agentId },
  })
}

function closeModal() {
  showModal.value = false
  editingAgentId.value = null
  formError.value = null
}

async function handleDelete(agent: AgentProfile) {
  const confirmed = await confirm({
    title: t('sections.agents.deleteTitle'),
    message: t('sections.agents.deleteMessage', { name: agent.displayName || agent.name }),
    confirmText: t('common.delete'),
    cancelText: t('common.cancel'),
    danger: true,
  })
  if (!confirmed) return
  await deleteAgent(agent.id)
}

async function handleSave() {
  const displayName = form.value.displayName.trim()
  if (!displayName) {
    formError.value = t('sections.agents.form.nameRequired')
    return
  }

  const payload: AgentProfilePayload = {
    displayName,
    model: form.value.model.trim() || undefined,
    enabled: form.value.enabled,
    isDefault: form.value.isDefault,
  }

  let ok = false
  if (modalMode.value === 'create') {
    ok = !!(await createAgent(payload))
  } else if (editingAgentId.value) {
    ok = !!(await updateAgent(editingAgentId.value, payload))
  }

  if (ok) {
    closeModal()
  } else {
    formError.value = error.value || t('sections.agents.form.saveFailed')
  }
}
</script>

<template>
  <div class="agents-section">
    <div class="section-header">
      <div>
        <h2 class="section-title">{{ t('settings.nav.agents') }}</h2>
        <p class="section-description">{{ t('sections.agents.subtitle') }}</p>
      </div>
      <button class="btn-primary" @click="openCreateModal">
        {{ t('sections.agents.addBtn') }}
      </button>
    </div>

    <div v-if="loading" class="loading-state">
      {{ t('sections.agents.loading') }}
    </div>

    <div v-else-if="sortedAgents.length === 0" class="empty-state">
      <p>{{ t('sections.agents.empty') }}</p>
    </div>

    <div v-else class="table-wrap">
      <table class="agents-table">
        <thead>
          <tr>
            <th>{{ t('sections.agents.table.name') }}</th>
            <th>{{ t('sections.agents.table.id') }}</th>
            <th>{{ t('sections.agents.table.model') }}</th>
            <th>{{ t('sections.agents.table.status') }}</th>
            <th>{{ t('sections.agents.table.actions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="agent in sortedAgents" :key="agent.id">
            <td>
              <div class="agent-name-cell">
                <span class="agent-name">{{ agent.displayName || agent.name }}</span>
                <span v-if="agent.isDefault" class="default-badge">{{ t('sections.agents.default') }}</span>
              </div>
            </td>
            <td><code class="agent-id">{{ agent.id }}</code></td>
            <td>{{ agent.model || '-' }}</td>
            <td>
              <span class="status-badge" :class="{ enabled: agent.enabled, disabled: !agent.enabled }">
                {{ agent.enabled ? t('common.enable') : t('common.disable') }}
              </span>
            </td>
            <td>
              <div class="actions">
                <button class="btn-link" @click="openPersonaConfig(agent.id)">{{ t('sections.agents.table.persona') }}</button>
                <button class="btn-link" @click="openEditModal(agent)">{{ t('common.edit') }}</button>
                <button class="btn-link danger" @click="handleDelete(agent)">{{ t('common.delete') }}</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="showModal" class="modal-overlay" @click.self="closeModal">
      <div class="modal-content">
        <div class="modal-header">
          <h3>{{ modalMode === 'create' ? t('sections.agents.createTitle') : t('sections.agents.editTitle') }}</h3>
          <button class="btn-close" @click="closeModal">✕</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>{{ t('sections.agents.form.nameLabel') }}</label>
            <input
              v-model="form.displayName"
              class="form-input"
              :placeholder="t('sections.agents.form.namePlaceholder')"
            />
          </div>

          <div class="form-group">
            <label>{{ t('sections.agents.form.modelLabel') }}</label>
            <Select v-model="form.model" :options="modelOptions" :placeholder="t('sections.agents.form.modelPlaceholder')" :disabled="modelLoading" />
          </div>

          <label class="checkbox-row">
            <input v-model="form.enabled" type="checkbox" />
            <span>{{ t('sections.agents.form.enabledLabel') }}</span>
          </label>

          <label class="checkbox-row">
            <input v-model="form.isDefault" type="checkbox" />
            <span>{{ t('sections.agents.form.defaultLabel') }}</span>
          </label>

          <div v-if="formError || error" class="form-error">
            {{ formError || error }}
          </div>
        </div>

        <div class="modal-footer">
          <button class="btn-secondary" @click="closeModal">{{ t('common.cancel') }}</button>
          <button class="btn-primary" :disabled="saving" @click="handleSave">
            {{ saving ? t('common.loading') : t('common.save') }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.agents-section {
  padding: 32px;
  height: 100%;
  overflow-y: auto;
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 24px;
}

.section-title {
  font-size: 24px;
  font-weight: 600;
  margin: 0 0 8px;
}

.section-description {
  color: var(--color-gray-500);
  font-size: 14px;
  margin: 0;
}

.btn-primary {
  padding: 10px 16px;
  background: var(--color-black);
  color: var(--color-white);
  border: none;
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: 14px;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-secondary {
  padding: 10px 16px;
  background: transparent;
  border: var(--border);
  border-radius: var(--radius-md);
  cursor: pointer;
}

.loading-state,
.empty-state {
  padding: 32px;
  text-align: center;
  color: var(--color-gray-500);
}

.table-wrap {
  border: var(--border);
  border-radius: var(--radius-lg);
  background: var(--color-white);
  overflow: hidden;
}

.agents-table {
  width: 100%;
  border-collapse: collapse;
}

.agents-table th,
.agents-table td {
  padding: 12px 16px;
  border-bottom: var(--border-light);
  text-align: left;
  vertical-align: top;
}

.agents-table th {
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-gray-600);
  background: var(--color-gray-50);
}

.agents-table tbody tr:last-child td {
  border-bottom: none;
}

.agent-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.agent-name {
  font-weight: 600;
}

.agent-id {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-gray-600);
}

.default-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  background: var(--color-gray-100);
  color: var(--color-gray-700);
  font-family: var(--font-mono);
  font-size: 10px;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: var(--radius-full);
  font-size: 12px;
}

.status-badge.enabled {
  background: var(--color-green-50);
  color: var(--color-green-600);
}

.status-badge.disabled {
  background: var(--color-gray-100);
  color: var(--color-gray-500);
}

.actions {
  display: flex;
  gap: 8px;
}

.btn-link {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 13px;
  color: var(--color-gray-600);
}

.btn-link.danger {
  color: var(--color-red-600);
}

.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  width: 560px;
  max-width: calc(100vw - 32px);
  background: var(--color-white);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: var(--border);
}

.btn-close {
  border: none;
  background: transparent;
  color: var(--color-gray-400);
  cursor: pointer;
}

.modal-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-input {
  width: 100%;
  padding: 9px 10px;
  border: var(--border);
  border-radius: var(--radius-md);
  font-size: 14px;
  font-family: var(--font-ui);
}

.checkbox-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--color-gray-700);
}

.form-error {
  padding: 8px 10px;
  border-radius: var(--radius-md);
  background: var(--color-red-50);
  color: var(--color-red-600);
  font-size: 12px;
}

.modal-footer {
  padding: 14px 20px;
  border-top: var(--border);
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
