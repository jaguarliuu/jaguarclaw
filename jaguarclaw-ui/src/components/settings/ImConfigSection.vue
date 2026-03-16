<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useIm } from '@/composables/useIm'
import { useI18n } from '@/i18n'
import DiceBearAvatar from '@/components/im/DiceBearAvatar.vue'

const { t } = useI18n()
const { settings, loadSettings, saveSettings } = useIm()

const displayName  = ref('')
const redisUrl     = ref('')
const redisPassword = ref('')
const avatarStyle  = ref('thumbs')
const avatarSeed   = ref('')
const saving = ref(false)
const saved  = ref(false)
const error  = ref<string | null>(null)

const AVATAR_STYLES = [
  { key: 'thumbs',     label: 'Thumbs' },
  { key: 'bottts',     label: 'Bottts' },
  { key: 'fun-emoji',  label: 'Emoji' },
  { key: 'lorelei',    label: 'Lorelei' },
  { key: 'micah',      label: 'Micah' },
  { key: 'pixel-art',  label: 'Pixel' },
  { key: 'shapes',     label: 'Shapes' },
  { key: 'adventurer', label: 'Adventurer' },
]

onMounted(async () => {
  await loadSettings()
  displayName.value = settings.value?.displayName ?? ''
  redisUrl.value    = settings.value?.redisUrl ?? ''
  avatarStyle.value = settings.value?.avatarStyle ?? 'thumbs'
  avatarSeed.value  = settings.value?.avatarSeed ?? ''
})

async function save() {
  saving.value = true
  saved.value  = false
  error.value  = null
  try {
    await saveSettings({
      displayName: displayName.value,
      redisUrl: redisUrl.value,
      redisPassword: redisPassword.value,
      avatarStyle: avatarStyle.value,
      avatarSeed: avatarSeed.value,
    })
    saved.value = true
    setTimeout(() => { saved.value = false }, 2000)
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to save'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="im-section">
    <header class="section-header">
      <h2 class="section-title">IM</h2>
      <p class="section-subtitle">{{ t('sections.im.subtitle') }}</p>
    </header>

    <!-- Node Identity -->
    <div v-if="settings?.nodeId" class="identity-card">
      <span class="identity-label">{{ t('sections.im.nodeIdLabel') }}</span>
      <code class="identity-value">{{ settings.nodeId }}</code>
    </div>

    <!-- Form -->
    <div class="form-panel">
      <div class="form-group">
        <label class="form-label">{{ t('sections.im.fields.displayName') }}</label>
        <input v-model="displayName" class="form-input" :placeholder="t('sections.im.fields.displayNamePlaceholder')" />
      </div>

      <!-- Avatar Picker -->
      <div class="form-group">
        <label class="form-label">Avatar</label>
        <div class="avatar-picker">
          <div class="avatar-preview-col">
            <DiceBearAvatar :avatar-style="avatarStyle" :avatar-seed="avatarSeed || displayName || 'me'" :size="80" />
          </div>
          <div class="avatar-picker-right">
            <div class="style-grid">
              <button
                v-for="s in AVATAR_STYLES"
                :key="s.key"
                class="style-cell"
                :class="{ active: avatarStyle === s.key }"
                @click="avatarStyle = s.key"
                :title="s.label"
              >
                <DiceBearAvatar :avatar-style="s.key" :avatar-seed="displayName || 'preview'" :size="32" />
              </button>
            </div>
            <input
              v-model="avatarSeed"
              class="form-input seed-input"
              placeholder="Seed (leave empty for display name)"
            />
          </div>
        </div>
      </div>

      <div class="form-group">
        <label class="form-label">{{ t('sections.im.fields.redisUrl') }}</label>
        <input v-model="redisUrl" class="form-input" :placeholder="t('sections.im.fields.redisUrlPlaceholder')" />
        <p class="form-hint">{{ t('sections.im.fields.redisUrlHint') }}</p>
      </div>

      <div class="form-group">
        <label class="form-label">
          {{ t('sections.im.fields.redisPassword') }}
          <span class="optional">{{ t('sections.im.fields.redisPasswordOptional') }}</span>
        </label>
        <input v-model="redisPassword" type="password" class="form-input" :placeholder="t('sections.im.fields.redisPasswordPlaceholder')" autocomplete="off" />
      </div>

      <div v-if="error" class="form-error">{{ error }}</div>

      <div class="form-actions">
        <button class="save-btn" @click="save" :disabled="saving">
          {{ saving ? t('sections.im.saving') : saved ? t('sections.im.saved') : t('sections.im.save') }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.im-section {
  height: 100%;
  overflow-y: auto;
  padding: 24px 32px;
  max-width: 560px;
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

.identity-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-gray-bg);
  margin-bottom: 20px;
}

.identity-label {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: var(--color-gray-dark);
  flex-shrink: 0;
}

.identity-value {
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--color-black);
  word-break: break-all;
}

.form-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.form-label {
  font-size: 11px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-gray-dark);
}

.optional {
  font-size: 10px;
  text-transform: none;
  letter-spacing: 0;
  font-weight: 400;
  color: var(--color-gray-400);
}

.form-input {
  padding: 8px 10px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 13px;
  outline: none;
  transition: border-color 0.15s ease;
}

.form-input:focus {
  border-color: var(--color-black);
}

.form-hint {
  font-size: 12px;
  color: var(--color-gray-400);
  margin: 0;
}

.form-error {
  padding: 8px 12px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: var(--radius-md);
  color: #dc2626;
  font-size: 13px;
  font-family: var(--font-mono);
}

.form-actions {
  display: flex;
  padding-top: 4px;
}

.save-btn {
  padding: 8px 20px;
  border: none;
  border-radius: var(--radius-md);
  background: var(--color-black);
  color: var(--color-white);
  font-family: var(--font-mono);
  font-size: 13px;
  cursor: pointer;
  transition: opacity 0.15s ease;
}

.save-btn:hover {
  opacity: 0.85;
}

.save-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* ── Avatar picker ───────────────────────────────── */
.avatar-picker {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  padding: 12px;
  border: var(--border);
  border-radius: var(--radius-lg);
  background: var(--color-gray-bg);
}

.avatar-preview-col {
  flex-shrink: 0;
  border-radius: 50%;
  overflow: hidden;
  border: 2px solid var(--color-gray-200);
}

.avatar-picker-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.style-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
}

.style-cell {
  border: 2px solid transparent;
  border-radius: var(--radius-md);
  padding: 3px;
  cursor: pointer;
  background: var(--color-white);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: border-color 0.15s ease;
  overflow: hidden;
}

.style-cell:hover {
  border-color: var(--color-gray-300);
}

.style-cell.active {
  border-color: var(--color-primary);
}

.seed-input {
  font-size: 12px;
}
</style>
