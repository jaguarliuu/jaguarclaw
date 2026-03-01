<script setup lang="ts">
import { onMounted, computed, ref } from 'vue'
import { useMemory } from '@/composables/useMemory'
import { useAgents } from '@/composables/useAgents'
import { useI18n } from '@/i18n'

const { status, loading, rebuilding, error, loadStatus, rebuildIndex } = useMemory()
const { agents, enabledAgents, defaultAgent, loadAgents } = useAgents()
const { t } = useI18n()

const selectedAgentId = ref<string>('main')

const vectorStatus = computed(() => {
  if (!status.value) return t('sections.memory.unknown')
  return status.value.vectorSearchEnabled ? t('sections.memory.enabled') : t('sections.memory.disabled')
})

const embeddingInfo = computed(() => {
  if (!status.value) return t('sections.memory.unknown')
  if (status.value.embeddingProvider === 'none') return t('sections.memory.noProvider')
  return `${status.value.embeddingProvider} / ${status.value.embeddingModel}`
})

const agentOptions = computed(() => enabledAgents.value.length > 0 ? enabledAgents.value : agents.value)

const activeAgentName = computed(() => {
  const agent = agentOptions.value.find((item) => item.id === selectedAgentId.value)
  return agent?.displayName || agent?.name || selectedAgentId.value
})

async function handleRebuild() {
  try {
    await rebuildIndex()
  } catch {
    // Error already handled in composable
  }
}

onMounted(async () => {
  await Promise.all([loadStatus(), loadAgents()])
  selectedAgentId.value = defaultAgent.value?.id || agentOptions.value[0]?.id || 'main'
})
</script>

<template>
  <div class="memory-section">
    <header class="section-header">
      <h2 class="section-title">{{ t('settings.nav.memory') }}</h2>
      <p class="section-subtitle">{{ t('sections.memory.subtitle') }}</p>
    </header>

    <div class="scope-toolbar">
      <label class="scope-field">
        <span class="scope-label">{{ t('sections.memory.scope.strategyLabel') }}</span>
        <span class="scope-value">{{ t('sections.memory.scope.strategyValue') }}</span>
      </label>
      <label class="scope-field">
        <span class="scope-label">{{ t('sections.memory.scope.agentLabel') }}</span>
        <select v-model="selectedAgentId" class="scope-select">
          <option v-for="agent in agentOptions" :key="agent.id" :value="agent.id">
            {{ agent.displayName || agent.name }}
          </option>
        </select>
      </label>
      <span class="scope-hint">{{ t('sections.memory.scope.agentHint', { agent: activeAgentName }) }}</span>
    </div>

    <div class="scope-grid">
      <div class="scope-card">
        <h3>{{ t('sections.memory.scope.globalTitle') }}</h3>
        <p>{{ t('sections.memory.scope.globalDesc') }}</p>
      </div>
      <div class="scope-card">
        <h3>{{ t('sections.memory.scope.agentTitle') }}</h3>
        <p>{{ t('sections.memory.scope.agentDesc') }}</p>
      </div>
      <div class="scope-card">
        <h3>{{ t('sections.memory.scope.bothTitle') }}</h3>
        <p>{{ t('sections.memory.scope.bothDesc') }}</p>
      </div>
    </div>

    <div v-if="loading && !status" class="loading-state">
      {{ t('sections.memory.loading') }}
    </div>

    <div v-else-if="error && !status" class="error-state">
      <p>{{ error }}</p>
      <button class="retry-btn" @click="loadStatus">{{ t('common.retry') }}</button>
    </div>

    <div v-else-if="status" class="status-panel">
      <div class="key-point-banner">
        <span class="banner-icon">💡</span>
        <span class="banner-text">{{ status.note }}</span>
      </div>

      <div class="status-grid">
        <div class="status-card">
          <div class="card-label">{{ t('sections.memory.totalChunks') }}</div>
          <div class="card-value">{{ status.totalChunks }}</div>
        </div>

        <div class="status-card">
          <div class="card-label">{{ t('sections.memory.withEmbedding') }}</div>
          <div class="card-value">{{ status.chunksWithEmbedding }}</div>
        </div>

        <div class="status-card">
          <div class="card-label">{{ t('sections.memory.memoryFiles') }}</div>
          <div class="card-value">{{ status.memoryFileCount }}</div>
        </div>

        <div class="status-card">
          <div class="card-label">{{ t('sections.memory.vectorSearch') }}</div>
          <div class="card-value" :class="{ enabled: status.vectorSearchEnabled }">
            {{ vectorStatus }}
          </div>
        </div>
      </div>

      <div class="info-row">
        <span class="info-label">{{ t('sections.memory.embeddingProvider') }}</span>
        <span class="info-value">{{ embeddingInfo }}</span>
      </div>

      <div class="actions">
        <button class="rebuild-btn" :disabled="rebuilding" @click="handleRebuild">
          {{ rebuilding ? t('sections.memory.rebuildingBtn') : t('sections.memory.rebuildBtn') }}
        </button>
        <span class="action-hint">{{ t('sections.memory.rebuildHint') }}</span>
      </div>

      <div class="tool-tips">
        <div class="tips-title">{{ t('sections.memory.scope.toolsTitle') }}</div>
        <div class="tips-row"><code>memory_search(query, scope=agent|global|both)</code></div>
        <div class="tips-row"><code>memory_write(target, content, scope=agent|global)</code></div>
        <div class="tips-row"><code>memory_get(path, scope=agent|global)</code></div>
      </div>

      <div v-if="error" class="error-banner">{{ error }}</div>
    </div>
  </div>
</template>

<style scoped>
.memory-section {
  height: 100%;
  overflow-y: auto;
  padding: 24px 32px;
}

.section-header {
  margin-bottom: 16px;
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

.scope-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: var(--border-light);
  border-radius: var(--radius-md);
  background: var(--color-gray-50);
  margin-bottom: 16px;
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

.scope-value {
  padding: 4px 8px;
  border-radius: var(--radius-full);
  background: var(--color-gray-200);
  font-family: var(--font-mono);
  font-size: 11px;
}

.scope-select {
  min-width: 150px;
  padding: 6px 8px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 12px;
}

.scope-hint {
  margin-left: auto;
  font-size: 12px;
  color: var(--color-gray-dark);
}

.scope-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.scope-card {
  border: var(--border-light);
  border-radius: var(--radius-md);
  background: var(--color-white);
  padding: 12px;
}

.scope-card h3 {
  margin: 0 0 6px;
  font-size: 13px;
}

.scope-card p {
  margin: 0;
  font-size: 12px;
  color: var(--color-gray-dark);
  line-height: 1.5;
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

.status-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.key-point-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-radius: var(--radius-md);
  background: var(--color-gray-bg);
  border-left: 3px solid var(--color-black);
  font-size: 14px;
}

.banner-icon {
  font-size: 16px;
}

.banner-text {
  color: var(--color-gray-dark);
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 16px;
}

.status-card {
  padding: 16px;
  border: var(--border);
  border-radius: var(--radius-lg);
  background: var(--color-white);
}

.card-label {
  font-size: 11px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-gray-dark);
  margin-bottom: 8px;
}

.card-value {
  font-family: var(--font-mono);
  font-size: 20px;
  font-weight: 500;
}

.card-value.enabled {
  color: #22c55e;
}

.info-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 0;
  border-top: var(--border-light);
}

.info-label {
  font-size: 13px;
  color: var(--color-gray-dark);
}

.info-value {
  font-family: var(--font-mono);
  font-size: 13px;
}

.actions {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-top: 8px;
}

.rebuild-btn {
  padding: 10px 20px;
  border: none;
  border-radius: var(--radius-md);
  background: var(--color-black);
  color: var(--color-white);
  font-family: var(--font-mono);
  font-size: 13px;
  cursor: pointer;
  transition: opacity 0.15s ease;
}

.rebuild-btn:hover:not(:disabled) {
  opacity: 0.9;
}

.rebuild-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.action-hint {
  font-size: 12px;
  color: var(--color-gray-dark);
}

.tool-tips {
  border: var(--border-light);
  border-radius: var(--radius-md);
  background: var(--color-gray-50);
  padding: 10px 12px;
}

.tips-title {
  margin-bottom: 8px;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-gray-dark);
}

.tips-row {
  margin-top: 4px;
  font-size: 12px;
}

.tips-row code {
  font-family: var(--font-mono);
}

.error-banner {
  padding: 12px 16px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: var(--radius-md);
  color: #dc2626;
  font-size: 13px;
}

@media (max-width: 960px) {
  .memory-section {
    padding: 20px;
  }

  .scope-toolbar {
    flex-wrap: wrap;
  }

  .scope-grid {
    grid-template-columns: 1fr;
  }
}
</style>
