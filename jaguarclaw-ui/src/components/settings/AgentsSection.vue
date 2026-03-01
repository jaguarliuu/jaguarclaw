<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useConfirm } from '@/composables/useConfirm'
import { useAgents, type AgentProfilePayload } from '@/composables/useAgents'
import { useI18n } from '@/i18n'
import type { AgentProfile } from '@/types'

type AgentTemplateKey = 'general' | 'coder' | 'writer' | 'researcher'
type ResponseStyleKey = 'concise' | 'balanced' | 'detailed'
type InitiativeKey = 'on_demand' | 'proactive'
type ToneKey = 'neutral' | 'friendly' | 'strict'

const CREATE_TOTAL_STEPS = 3

const TEMPLATE_SEEDS: Record<AgentTemplateKey, { idSeed: string; model: string }> = {
  general: { idSeed: 'assistant', model: 'gpt-4o-mini' },
  coder: { idSeed: 'coder', model: 'gpt-4o-mini' },
  writer: { idSeed: 'writer', model: 'gpt-4o-mini' },
  researcher: { idSeed: 'researcher', model: 'gpt-4o-mini' },
}

const { t } = useI18n()
const { confirm } = useConfirm()
const { agents, loading, saving, error, loadAgents, createAgent, updateAgent, deleteAgent } = useAgents()

const showModal = ref(false)
const modalMode = ref<'create' | 'edit'>('create')
const editingAgentId = ref<string | null>(null)
const formError = ref<string | null>(null)
const createStep = ref(1)
const selectedTemplate = ref<AgentTemplateKey>('general')
const responseStyle = ref<ResponseStyleKey>('balanced')
const initiative = ref<InitiativeKey>('on_demand')
const tone = ref<ToneKey>('neutral')
const autoFillSnapshot = ref({
  name: '',
  displayName: '',
  model: '',
})

const form = ref({
  name: '',
  displayName: '',
  description: '',
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

const templateOptions = computed(() => [
  { key: 'general' as AgentTemplateKey, title: t('sections.agents.wizard.templates.general.name'), description: t('sections.agents.wizard.templates.general.desc') },
  { key: 'coder' as AgentTemplateKey, title: t('sections.agents.wizard.templates.coder.name'), description: t('sections.agents.wizard.templates.coder.desc') },
  { key: 'writer' as AgentTemplateKey, title: t('sections.agents.wizard.templates.writer.name'), description: t('sections.agents.wizard.templates.writer.desc') },
  { key: 'researcher' as AgentTemplateKey, title: t('sections.agents.wizard.templates.researcher.name'), description: t('sections.agents.wizard.templates.researcher.desc') },
])

const responseStyleOptions = computed(() => [
  { value: 'concise' as ResponseStyleKey, label: t('sections.agents.wizard.responseStyle.concise') },
  { value: 'balanced' as ResponseStyleKey, label: t('sections.agents.wizard.responseStyle.balanced') },
  { value: 'detailed' as ResponseStyleKey, label: t('sections.agents.wizard.responseStyle.detailed') },
])

const initiativeOptions = computed(() => [
  { value: 'on_demand' as InitiativeKey, label: t('sections.agents.wizard.initiative.on_demand') },
  { value: 'proactive' as InitiativeKey, label: t('sections.agents.wizard.initiative.proactive') },
])

const toneOptions = computed(() => [
  { value: 'neutral' as ToneKey, label: t('sections.agents.wizard.tone.neutral') },
  { value: 'friendly' as ToneKey, label: t('sections.agents.wizard.tone.friendly') },
  { value: 'strict' as ToneKey, label: t('sections.agents.wizard.tone.strict') },
])

const wizardPrimaryLabel = computed(() => {
  if (modalMode.value !== 'create') return t('common.save')
  if (createStep.value >= CREATE_TOTAL_STEPS) return t('sections.agents.wizard.createNow')
  return t('sections.agents.wizard.next')
})

const wizardSteps = computed(() => [
  { id: 1, label: t('sections.agents.wizard.step1Label') },
  { id: 2, label: t('sections.agents.wizard.step2Label') },
  { id: 3, label: t('sections.agents.wizard.step3Label') },
])

const generatedDescription = computed(() =>
  t('sections.agents.wizard.summaryTemplate', {
    template: t(`sections.agents.wizard.templates.${selectedTemplate.value}.name`),
    responseStyle: t(`sections.agents.wizard.responseStyle.${responseStyle.value}`),
    initiative: t(`sections.agents.wizard.initiative.${initiative.value}`),
    tone: t(`sections.agents.wizard.tone.${tone.value}`),
  }),
)

onMounted(() => {
  loadAgents()
})

function resetForm() {
  form.value = {
    name: '',
    displayName: '',
    description: '',
    model: '',
    enabled: true,
    isDefault: false,
  }
  createStep.value = 1
  selectedTemplate.value = 'general'
  responseStyle.value = 'balanced'
  initiative.value = 'on_demand'
  tone.value = 'neutral'
  autoFillSnapshot.value = { name: '', displayName: '', model: '' }
  applyTemplateDefaults('general')
  formError.value = null
}

function openCreateModal() {
  modalMode.value = 'create'
  editingAgentId.value = null
  resetForm()
  showModal.value = true
}

function openEditModal(agent: AgentProfile) {
  modalMode.value = 'edit'
  editingAgentId.value = agent.id
  form.value = {
    name: agent.name,
    displayName: agent.displayName || '',
    description: agent.description || '',
    model: agent.model || '',
    enabled: agent.enabled,
    isDefault: agent.isDefault,
  }
  formError.value = null
  showModal.value = true
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

function applyTemplateDefaults(template: AgentTemplateKey) {
  selectedTemplate.value = template
  const seed = TEMPLATE_SEEDS[template]
  const displayName = t(`sections.agents.wizard.templates.${template}.name`)

  if (!form.value.name || form.value.name === autoFillSnapshot.value.name) {
    form.value.name = seed.idSeed
  }
  if (!form.value.displayName || form.value.displayName === autoFillSnapshot.value.displayName) {
    form.value.displayName = displayName
  }
  if (!form.value.model || form.value.model === autoFillSnapshot.value.model) {
    form.value.model = seed.model
  }

  autoFillSnapshot.value = {
    name: seed.idSeed,
    displayName,
    model: seed.model,
  }
}

function goToNextCreateStep() {
  if (createStep.value === 1) {
    const name = form.value.name.trim()
    if (!name) {
      formError.value = t('sections.agents.form.nameRequired')
      return
    }
  }
  if (createStep.value < CREATE_TOTAL_STEPS) {
    createStep.value += 1
    formError.value = null
  }
}

function goToPrevCreateStep() {
  if (createStep.value > 1) {
    createStep.value -= 1
    formError.value = null
  }
}

async function handleSave() {
  if (modalMode.value === 'create' && createStep.value < CREATE_TOTAL_STEPS) {
    goToNextCreateStep()
    return
  }

  const name = form.value.name.trim()
  if (!name) {
    formError.value = t('sections.agents.form.nameRequired')
    return
  }

  const manualDescription = form.value.description.trim()
  const payload: AgentProfilePayload = {
    name,
    displayName: form.value.displayName.trim() || name,
    description: modalMode.value === 'create'
      ? (manualDescription || generatedDescription.value)
      : (manualDescription || undefined),
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
              <div class="agent-desc" v-if="agent.description">{{ agent.description }}</div>
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
          <template v-if="modalMode === 'create'">
            <div class="wizard-progress">
              <button
                v-for="step in wizardSteps"
                :key="step.id"
                type="button"
                class="wizard-step"
                :class="{ active: step.id === createStep, completed: step.id < createStep }"
                @click="step.id < createStep && (createStep = step.id)"
              >
                <span class="wizard-step-index">{{ step.id }}</span>
                <span class="wizard-step-label">{{ step.label }}</span>
              </button>
            </div>

            <div v-if="createStep === 1" class="wizard-panel">
              <p class="wizard-helper">{{ t('sections.agents.wizard.step1Hint') }}</p>
              <div class="template-grid">
                <button
                  v-for="option in templateOptions"
                  :key="option.key"
                  type="button"
                  class="template-card"
                  :class="{ selected: selectedTemplate === option.key }"
                  @click="applyTemplateDefaults(option.key)"
                >
                  <div class="template-title">{{ option.title }}</div>
                  <div class="template-desc">{{ option.description }}</div>
                </button>
              </div>

              <div class="form-group">
                <label>{{ t('sections.agents.form.nameLabel') }}</label>
                <input v-model="form.name" class="form-input" :placeholder="t('sections.agents.form.namePlaceholder')" />
              </div>
              <div class="form-group">
                <label>{{ t('sections.agents.form.displayNameLabel') }}</label>
                <input
                  v-model="form.displayName"
                  class="form-input"
                  :placeholder="t('sections.agents.form.displayNamePlaceholder')"
                />
              </div>
            </div>

            <div v-else-if="createStep === 2" class="wizard-panel">
              <div class="form-group">
                <label>{{ t('sections.agents.wizard.responseStyle.label') }}</label>
                <div class="chip-row">
                  <button
                    v-for="option in responseStyleOptions"
                    :key="option.value"
                    type="button"
                    class="chip-option"
                    :class="{ active: responseStyle === option.value }"
                    @click="responseStyle = option.value"
                  >
                    {{ option.label }}
                  </button>
                </div>
              </div>

              <div class="form-group">
                <label>{{ t('sections.agents.wizard.initiative.label') }}</label>
                <div class="chip-row">
                  <button
                    v-for="option in initiativeOptions"
                    :key="option.value"
                    type="button"
                    class="chip-option"
                    :class="{ active: initiative === option.value }"
                    @click="initiative = option.value"
                  >
                    {{ option.label }}
                  </button>
                </div>
              </div>

              <div class="form-group">
                <label>{{ t('sections.agents.wizard.tone.label') }}</label>
                <div class="chip-row">
                  <button
                    v-for="option in toneOptions"
                    :key="option.value"
                    type="button"
                    class="chip-option"
                    :class="{ active: tone === option.value }"
                    @click="tone = option.value"
                  >
                    {{ option.label }}
                  </button>
                </div>
              </div>

              <div class="summary-box">
                <div class="summary-title">{{ t('sections.agents.wizard.generatedSummary') }}</div>
                <p>{{ generatedDescription }}</p>
              </div>
            </div>

            <div v-else class="wizard-panel">
              <div class="form-group">
                <label>{{ t('sections.agents.form.modelLabel') }}</label>
                <input
                  v-model="form.model"
                  class="form-input"
                  :placeholder="t('sections.agents.form.modelPlaceholder')"
                />
              </div>

              <div class="form-group">
                <label>{{ t('sections.agents.form.descriptionLabel') }}</label>
                <textarea
                  v-model="form.description"
                  class="form-textarea"
                  rows="3"
                  :placeholder="t('sections.agents.form.descriptionPlaceholder')"
                />
                <p class="input-hint">{{ t('sections.agents.wizard.autoDescriptionHint') }}</p>
              </div>

              <label class="checkbox-row">
                <input v-model="form.enabled" type="checkbox" />
                <span>{{ t('sections.agents.form.enabledLabel') }}</span>
              </label>

              <label class="checkbox-row">
                <input v-model="form.isDefault" type="checkbox" />
                <span>{{ t('sections.agents.form.defaultLabel') }}</span>
              </label>
            </div>
          </template>

          <template v-else>
            <div class="form-group">
              <label>{{ t('sections.agents.form.nameLabel') }}</label>
              <input v-model="form.name" class="form-input" :placeholder="t('sections.agents.form.namePlaceholder')" />
            </div>

            <div class="form-group">
              <label>{{ t('sections.agents.form.displayNameLabel') }}</label>
              <input
                v-model="form.displayName"
                class="form-input"
                :placeholder="t('sections.agents.form.displayNamePlaceholder')"
              />
            </div>

            <div class="form-group">
              <label>{{ t('sections.agents.form.modelLabel') }}</label>
              <input v-model="form.model" class="form-input" :placeholder="t('sections.agents.form.modelPlaceholder')" />
            </div>

            <div class="form-group">
              <label>{{ t('sections.agents.form.descriptionLabel') }}</label>
              <textarea
                v-model="form.description"
                class="form-textarea"
                rows="3"
                :placeholder="t('sections.agents.form.descriptionPlaceholder')"
              />
            </div>

            <label class="checkbox-row">
              <input v-model="form.enabled" type="checkbox" />
              <span>{{ t('sections.agents.form.enabledLabel') }}</span>
            </label>

            <label class="checkbox-row">
              <input v-model="form.isDefault" type="checkbox" />
              <span>{{ t('sections.agents.form.defaultLabel') }}</span>
            </label>
          </template>

          <div v-if="formError || error" class="form-error">
            {{ formError || error }}
          </div>
        </div>

        <div class="modal-footer">
          <div v-if="modalMode === 'create'" class="wizard-status">
            {{ t('sections.agents.wizard.stepStatus', { current: String(createStep), total: String(CREATE_TOTAL_STEPS) }) }}
          </div>
          <div class="footer-actions">
            <button class="btn-secondary" @click="closeModal">{{ t('common.cancel') }}</button>
            <button v-if="modalMode === 'create' && createStep > 1" class="btn-secondary" @click="goToPrevCreateStep">
              {{ t('sections.agents.wizard.back') }}
            </button>
            <button class="btn-primary" :disabled="saving" @click="handleSave">
              {{ saving ? t('common.loading') : wizardPrimaryLabel }}
            </button>
          </div>
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

.agent-desc {
  margin-top: 4px;
  color: var(--color-gray-500);
  font-size: 12px;
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

.wizard-progress {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 6px;
}

.wizard-step {
  display: flex;
  align-items: center;
  gap: 8px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  padding: 8px 10px;
  text-align: left;
  cursor: pointer;
}

.wizard-step.active {
  border-color: var(--color-black);
  background: var(--color-gray-50);
}

.wizard-step.completed {
  border-color: var(--color-green-100);
  background: var(--color-green-50);
}

.wizard-step-index {
  width: 18px;
  height: 18px;
  border-radius: var(--radius-full);
  background: var(--color-gray-200);
  color: var(--color-gray-600);
  font-size: 11px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.wizard-step.active .wizard-step-index,
.wizard-step.completed .wizard-step-index {
  background: var(--color-black);
  color: var(--color-white);
}

.wizard-step-label {
  font-size: 12px;
  color: var(--color-gray-700);
}

.wizard-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.wizard-helper {
  margin: 0;
  color: var(--color-gray-500);
  font-size: 12px;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.template-card {
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  padding: 10px 12px;
  text-align: left;
  cursor: pointer;
}

.template-card.selected {
  border-color: var(--color-black);
  background: var(--color-gray-50);
}

.template-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-gray-800);
}

.template-desc {
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-gray-500);
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-input,
.form-textarea {
  width: 100%;
  padding: 9px 10px;
  border: var(--border);
  border-radius: var(--radius-md);
  font-size: 14px;
  font-family: var(--font-ui);
}

.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chip-option {
  border: var(--border);
  background: var(--color-white);
  color: var(--color-gray-700);
  border-radius: var(--radius-full);
  padding: 6px 12px;
  font-size: 12px;
  cursor: pointer;
}

.chip-option.active {
  background: var(--color-black);
  border-color: var(--color-black);
  color: var(--color-white);
}

.summary-box {
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-gray-50);
  padding: 10px 12px;
}

.summary-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-gray-700);
  margin-bottom: 4px;
}

.summary-box p {
  margin: 0;
  color: var(--color-gray-600);
  font-size: 12px;
  line-height: 1.5;
}

.input-hint {
  margin: 0;
  font-size: 12px;
  color: var(--color-gray-500);
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
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.wizard-status {
  font-size: 12px;
  color: var(--color-gray-500);
}

.footer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

@media (max-width: 720px) {
  .template-grid {
    grid-template-columns: 1fr;
  }
}
</style>
