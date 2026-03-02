<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useSoulConfig } from '@/composables/useSoulConfig'
import { useAgents } from '@/composables/useAgents'
import { useI18n } from '@/i18n'
import Select from '@/components/common/Select.vue'
import type { SelectOption } from '@/components/common/Select.vue'

const { t } = useI18n()
const route = useRoute()

const { persona, loading, error, fetchPersona, saveFile, watchPersonaUpdates } = useSoulConfig()
const { agents, defaultAgent, loadAgents } = useAgents()

let stopWatcher: (() => void) | null = null
const selectedAgentId = ref('main')

const editSoul = ref('')
const editRule = ref('')
const editProfile = ref('')

const saving = ref(false)
const saveSuccess = ref(false)
const saveError = ref<string | null>(null)

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

function syncFormFromPersona() {
  if (!persona.value) return
  editSoul.value = persona.value.soul
  editRule.value = persona.value.rule
  editProfile.value = persona.value.profile
}

async function loadAgentSoul(agentId: string) {
  await fetchPersona(agentId)
  syncFormFromPersona()
}

function resolveRouteAgentId() {
  const fromQuery = route.query.agentId
  if (typeof fromQuery === 'string' && fromQuery.trim().length > 0) {
    return fromQuery.trim()
  }
  return defaultAgent.value?.id || 'main'
}

async function handleSave() {
  saving.value = true
  saveError.value = null
  saveSuccess.value = false
  try {
    await saveFile(selectedAgentId.value, 'soul', editSoul.value)
    await saveFile(selectedAgentId.value, 'rule', editRule.value)
    await saveFile(selectedAgentId.value, 'profile', editProfile.value)
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
  await loadAgentSoul(selectedAgentId.value)
  stopWatcher = watchPersonaUpdates(selectedAgentId.value, () => {
    void loadAgentSoul(selectedAgentId.value)
  })
})

onUnmounted(() => {
  stopWatcher?.()
})

watch(selectedAgentId, async (agentId) => {
  await loadAgentSoul(agentId)
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
</script>

<template>
  <div class="soul-section">
    <header class="section-header">
      <div class="header-left">
        <h2 class="section-title">{{ t('settings.nav.persona') }}</h2>
        <p class="section-subtitle">{{ t('sections.soul.subtitle') }}</p>
      </div>
      <div class="header-agent-select">
        <label class="form-label">{{ t('sections.soul.agentScopeLabel') }}</label>
        <Select v-model="selectedAgentId" :options="agentSelectOptions" />
      </div>
    </header>

    <div v-if="loading && !persona" class="loading-state">{{ t('sections.soul.loading') }}</div>

    <div v-if="error && !persona" class="error-state">
      <p>{{ error }}</p>
      <button class="retry-btn" @click="fetchPersona(selectedAgentId)">{{ t('common.retry') }}</button>
    </div>

    <template v-if="persona">
      <div class="config-blocks">
        <!-- SOUL.md -->
        <div class="config-block">
          <h3 class="block-title">SOUL.md</h3>
          <p class="block-desc">{{ t('sections.soul.blocks.soulDesc') }}</p>
          <textarea v-model="editSoul" class="form-textarea md-editor" rows="12" />
        </div>

        <!-- RULE.md -->
        <div class="config-block">
          <h3 class="block-title">RULE.md</h3>
          <p class="block-desc">{{ t('sections.soul.blocks.ruleDesc') }}</p>
          <textarea v-model="editRule" class="form-textarea md-editor" rows="10" />
        </div>

        <!-- PROFILE.md -->
        <div class="config-block">
          <h3 class="block-title">PROFILE.md</h3>
          <p class="block-desc">{{ t('sections.soul.blocks.profileDesc') }}</p>
          <textarea v-model="editProfile" class="form-textarea md-editor" rows="8" />
        </div>
      </div>

      <div v-if="saveSuccess" class="save-success">{{ t('sections.soul.savedSuccess') }}</div>
      <div v-if="saveError" class="save-error">{{ saveError }}</div>

      <div class="form-actions">
        <button class="save-btn" :disabled="saving" @click="handleSave">
          {{ saving ? t('sections.soul.savingBtn') : t('sections.soul.saveBtn') }}
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.soul-section {
  height: 100%;
  overflow-y: auto;
  padding: 24px 32px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 24px;
}

.header-left {
  min-width: 0;
}

.header-agent-select {
  min-width: 220px;
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

.form-input,
.form-textarea {
  width: 100%;
  padding: 10px 12px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: inherit;
  font-size: 14px;
  box-sizing: border-box;
}

.form-input:focus,
.form-textarea:focus {
  outline: none;
  border-color: var(--color-black);
  box-shadow: 0 0 0 3px rgba(0, 0, 0, 0.03);
}

.form-textarea {
  resize: vertical;
  line-height: 1.5;
}

.form-help {
  font-size: 12px;
  color: var(--color-gray-600);
  margin: 4px 0 0 0;
}

.md-editor {
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.6;
}

.save-success {
  padding: 10px 14px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: var(--radius-md);
  color: #166534;
  font-size: 13px;
  margin-bottom: 16px;
}

.save-error {
  padding: 10px 14px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: var(--radius-md);
  color: #dc2626;
  font-size: 13px;
  margin-bottom: 16px;
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

@media (max-width: 768px) {
  .section-header {
    flex-direction: column;
  }

  .header-agent-select {
    width: 100%;
    min-width: 0;
  }
}
</style>
