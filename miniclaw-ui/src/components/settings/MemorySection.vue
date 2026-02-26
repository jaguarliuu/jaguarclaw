<script setup lang="ts">
import { onMounted, computed } from 'vue'
import { useMemory } from '@/composables/useMemory'
import { useI18n } from '@/i18n'

const { status, loading, rebuilding, error, loadStatus, rebuildIndex } = useMemory()
const { t } = useI18n()

const vectorStatus = computed(() => {
  if (!status.value) return t('sections.memory.unknown')
  return status.value.vectorSearchEnabled ? t('sections.memory.enabled') : t('sections.memory.disabled')
})

const embeddingInfo = computed(() => {
  if (!status.value) return t('sections.memory.unknown')
  if (status.value.embeddingProvider === 'none') return t('sections.memory.noProvider')
  return `${status.value.embeddingProvider} / ${status.value.embeddingModel}`
})

async function handleRebuild() {
  try {
    await rebuildIndex()
  } catch (e) {
    // Error already handled in composable
  }
}

onMounted(() => {
  loadStatus()
})
</script>

<template>
  <div class="memory-section">
    <header class="section-header">
      <h2 class="section-title">{{ t('settings.nav.memory') }}</h2>
      <p class="section-subtitle">{{ t('sections.memory.subtitle') }}</p>
    </header>

    <div v-if="loading && !status" class="loading-state">
      {{ t('sections.memory.loading') }}
    </div>

    <div v-else-if="error && !status" class="error-state">
      <p>{{ error }}</p>
      <button class="retry-btn" @click="loadStatus">{{ t('common.retry') }}</button>
    </div>

    <div v-else-if="status" class="status-panel">
      <!-- Key Point Banner -->
      <div class="key-point-banner">
        <span class="banner-icon">💡</span>
        <span class="banner-text">{{ status.note }}</span>
      </div>

      <!-- Status Grid -->
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

      <!-- Embedding Provider -->
      <div class="info-row">
        <span class="info-label">{{ t('sections.memory.embeddingProvider') }}</span>
        <span class="info-value">{{ embeddingInfo }}</span>
      </div>

      <!-- Actions -->
      <div class="actions">
        <button
          class="rebuild-btn"
          :disabled="rebuilding"
          @click="handleRebuild"
        >
          {{ rebuilding ? t('sections.memory.rebuildingBtn') : t('sections.memory.rebuildBtn') }}
        </button>
        <span class="action-hint">
          {{ t('sections.memory.rebuildHint') }}
        </span>
      </div>

      <!-- Error display -->
      <div v-if="error" class="error-banner">
        {{ error }}
      </div>
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

.error-banner {
  padding: 12px 16px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: var(--radius-md);
  color: #dc2626;
  font-size: 13px;
}
</style>
