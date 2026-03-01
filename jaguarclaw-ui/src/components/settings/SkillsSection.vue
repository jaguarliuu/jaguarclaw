<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import type { Skill } from '@/types'
import { useSkills, type SkillViewScope } from '@/composables/useSkills'
import { useAgents } from '@/composables/useAgents'
import { useI18n } from '@/i18n'
import SkillRow from './SkillRow.vue'
import SkillDetailPanel from './SkillDetail.vue'
import SlidePanel from '@/components/common/SlidePanel.vue'

const {
  skills, loading, selectedSkill, selectedSkillDetail, detailLoading,
  uploading, uploadError,
  loadSkills, selectSkill, clearSelection, uploadSkill, clearUploadError
} = useSkills()
const { agents, enabledAgents, defaultAgent, loadAgents } = useAgents()
const { t } = useI18n()

const fileInput = ref<HTMLInputElement | null>(null)
const selectedScope = ref<SkillViewScope>('effective')
const selectedAgentId = ref<string>('main')
const initialized = ref(false)

const availableSkills = computed(() => skills.value.filter((s) => s.available))
const unavailableSkills = computed(() => skills.value.filter((s) => !s.available))
const panelOpen = computed(() => selectedSkill.value !== null)

const effectiveAgentId = computed(() => {
  return selectedAgentId.value || defaultAgent.value?.id || enabledAgents.value[0]?.id || agents.value[0]?.id || 'main'
})

const agentOptions = computed(() => enabledAgents.value.length > 0 ? enabledAgents.value : agents.value)

const scopeHint = computed(() => {
  if (selectedScope.value === 'global') {
    return t('sections.skills.scope.globalHint')
  }
  const active = agentOptions.value.find((agent) => agent.id === effectiveAgentId.value)
  return t('sections.skills.scope.effectiveHint', {
    agent: active?.displayName || active?.name || effectiveAgentId.value,
  })
})

function queryOptions() {
  if (selectedScope.value === 'global') {
    return { scope: 'global' as const }
  }
  return {
    scope: 'effective' as const,
    agentId: effectiveAgentId.value,
  }
}

async function reloadSkills() {
  await loadSkills(queryOptions())
}

function handleSelect(skill: Skill) {
  selectSkill(skill.name, queryOptions())
}

function handleClose() {
  clearSelection()
}

async function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  clearUploadError()
  try {
    await uploadSkill(file)
  } catch {
    // error is already set in composable
  }

  input.value = ''
}

watch([selectedScope, effectiveAgentId], async () => {
  if (!initialized.value) return
  clearSelection()
  await reloadSkills()
})

onMounted(async () => {
  await loadAgents()
  selectedAgentId.value = defaultAgent.value?.id || enabledAgents.value[0]?.id || agents.value[0]?.id || 'main'
  initialized.value = true
  await reloadSkills()
})
</script>

<template>
  <div class="skills-section">
    <header class="section-header">
      <h2 class="section-title">{{ t('settings.nav.skills') }}</h2>
      <span class="section-count">{{ t('sections.skills.totalCount', { n: String(skills.length) }) }}</span>
      <button class="upload-btn" @click="fileInput?.click()" :disabled="uploading">
        {{ uploading ? t('sections.skills.uploadingBtn') : t('sections.skills.uploadBtn') }}
      </button>
      <input ref="fileInput" type="file" accept=".md,.zip" hidden @change="handleFileChange" />
    </header>

    <div class="scope-bar">
      <label class="scope-field">
        <span class="scope-label">{{ t('sections.skills.scope.label') }}</span>
        <select v-model="selectedScope" class="scope-select">
          <option value="effective">{{ t('sections.skills.scope.effective') }}</option>
          <option value="global">{{ t('sections.skills.scope.global') }}</option>
        </select>
      </label>

      <label class="scope-field">
        <span class="scope-label">{{ t('sections.skills.scope.agentLabel') }}</span>
        <select v-model="selectedAgentId" class="scope-select" :disabled="selectedScope === 'global' || agentOptions.length === 0">
          <option v-for="agent in agentOptions" :key="agent.id" :value="agent.id">
            {{ agent.displayName || agent.name }}
          </option>
        </select>
      </label>

      <span class="scope-hint">{{ scopeHint }}</span>
    </div>

    <div v-if="uploadError" class="upload-error">
      <span>{{ uploadError }}</span>
      <button class="dismiss-btn" @click="clearUploadError">&times;</button>
    </div>

    <div v-if="loading" class="loading-state">
      <span>{{ t('sections.skills.loading') }}</span>
    </div>

    <div v-else class="skills-list">
      <div v-if="availableSkills.length" class="skill-group">
        <h3 class="group-title">{{ t('sections.skills.availableGroup', { n: String(availableSkills.length) }) }}</h3>
        <div class="group-list">
          <SkillRow
            v-for="skill in availableSkills"
            :key="skill.name"
            :skill="skill"
            :selected="selectedSkill === skill.name"
            @click="handleSelect(skill)"
          />
        </div>
      </div>

      <div v-if="unavailableSkills.length" class="skill-group">
        <h3 class="group-title">{{ t('sections.skills.unavailableGroup', { n: String(unavailableSkills.length) }) }}</h3>
        <div class="group-list">
          <SkillRow
            v-for="skill in unavailableSkills"
            :key="skill.name"
            :skill="skill"
            :selected="selectedSkill === skill.name"
            @click="handleSelect(skill)"
          />
        </div>
      </div>

      <div v-if="!skills.length" class="empty-state">
        <span>{{ t('sections.skills.empty') }}</span>
      </div>
    </div>

    <SlidePanel :open="panelOpen" :title="selectedSkill || ''" @close="handleClose">
      <SkillDetailPanel :skill="selectedSkillDetail" :loading="detailLoading" />
    </SlidePanel>
  </div>
</template>

<style scoped>
.skills-section {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.section-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding: 20px;
  border-bottom: var(--border);
}

.section-title {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 500;
}

.section-count {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-gray-dark);
}

.upload-btn {
  margin-left: auto;
  font-family: var(--font-mono);
  font-size: 11px;
  padding: 4px 10px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-text);
  cursor: pointer;
  transition: background 0.15s;
}

.upload-btn:hover:not(:disabled) {
  background: var(--color-gray-bg);
}

.upload-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.scope-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 20px;
  border-bottom: var(--border-light);
  background: var(--color-gray-50);
}

.scope-field {
  display: flex;
  align-items: center;
  gap: 8px;
}

.scope-label {
  font-family: var(--font-mono);
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--color-gray-dark);
}

.scope-select {
  min-width: 128px;
  padding: 6px 8px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 12px;
}

.scope-select:disabled {
  opacity: 0.5;
}

.scope-hint {
  margin-left: auto;
  font-size: 12px;
  color: var(--color-gray-dark);
}

.upload-error {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border-radius: var(--radius-md);
  font-family: var(--font-mono);
  font-size: 11px;
  color: #c0392b;
  background: #fdf0ef;
  border-bottom: var(--border);
}

.dismiss-btn {
  margin-left: auto;
  background: none;
  border: none;
  font-size: 14px;
  color: #c0392b;
  cursor: pointer;
  padding: 0 4px;
}

.loading-state,
.empty-state {
  padding: 40px 20px;
  text-align: center;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-gray-dark);
}

.skills-list {
  flex: 1;
  overflow-y: auto;
}

.skill-group {
  border-bottom: var(--border);
}

.skill-group:last-child {
  border-bottom: none;
}

.group-title {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-gray-dark);
  padding: 12px 16px;
  background: var(--color-gray-bg);
  border-bottom: var(--border-light);
}

.group-list {
  display: flex;
  flex-direction: column;
}
</style>
