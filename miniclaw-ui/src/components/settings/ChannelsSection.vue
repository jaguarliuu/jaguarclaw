<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useChannels } from '@/composables/useChannels'
import { useI18n } from '@/i18n'
import type { ChannelType, ChannelInfo } from '@/types'

const { channels, loading, error, loadChannels, createChannel, removeChannel, testChannel } = useChannels()
const { t } = useI18n()

// Form state
const showForm = ref(false)
const formName = ref('')
const formType = ref<ChannelType>('email')
const formError = ref<string | null>(null)
const submitting = ref(false)

// Email config
const emailHost = ref('')
const emailPort = ref(587)
const emailUsername = ref('')
const emailFrom = ref('')
const emailTls = ref(true)
const emailPassword = ref('')
const passwordVisible = ref(false)

// Webhook config
const webhookUrl = ref('')
const webhookMethod = ref('POST')
const webhookHeaders = ref('{"Content-Type": "application/json"}')
const webhookSecret = ref('')
const secretVisible = ref(false)

// Testing state per channel
const testingChannels = ref<Set<string>>(new Set())

// Delete confirmation
const confirmDeleteId = ref<string | null>(null)

function resetForm() {
  formName.value = ''
  formType.value = 'email'
  formError.value = null
  emailHost.value = ''
  emailPort.value = 587
  emailUsername.value = ''
  emailFrom.value = ''
  emailTls.value = true
  emailPassword.value = ''
  passwordVisible.value = false
  webhookUrl.value = ''
  webhookMethod.value = 'POST'
  webhookHeaders.value = '{"Content-Type": "application/json"}'
  webhookSecret.value = ''
  secretVisible.value = false
}

function openForm() {
  resetForm()
  showForm.value = true
}

function closeForm() {
  showForm.value = false
  resetForm()
}

async function handleSubmit() {
  if (!formName.value.trim()) {
    formError.value = t('sections.channels.errors.nameRequired')
    return
  }

  if (formType.value === 'email') {
    if (!emailHost.value.trim()) {
      formError.value = t('sections.channels.errors.smtpHostRequired')
      return
    }
    if (!emailUsername.value.trim()) {
      formError.value = t('sections.channels.errors.usernameRequired')
      return
    }
    if (!emailFrom.value.trim()) {
      formError.value = t('sections.channels.errors.fromRequired')
      return
    }
  } else {
    if (!webhookUrl.value.trim()) {
      formError.value = t('sections.channels.errors.urlRequired')
      return
    }
  }

  submitting.value = true
  formError.value = null
  try {
    if (formType.value === 'email') {
      await createChannel({
        name: formName.value.trim(),
        type: 'email',
        config: {
          host: emailHost.value.trim(),
          port: emailPort.value,
          username: emailUsername.value.trim(),
          from: emailFrom.value.trim(),
          tls: emailTls.value
        },
        credential: emailPassword.value || undefined
      })
    } else {
      let headers: Record<string, string> = {}
      try {
        headers = JSON.parse(webhookHeaders.value)
      } catch {
      formError.value = t('sections.channels.errors.invalidJson')
        submitting.value = false
        return
      }
      await createChannel({
        name: formName.value.trim(),
        type: 'webhook',
        config: {
          url: webhookUrl.value.trim(),
          method: webhookMethod.value,
          headers,
          secret: !!webhookSecret.value
        },
        credential: webhookSecret.value || undefined
      })
    }
    closeForm()
  } catch (e) {
    formError.value = e instanceof Error ? e.message : t('sections.channels.errors.failedToCreate')
  } finally {
    submitting.value = false
  }
}

async function handleTest(channelId: string) {
  testingChannels.value.add(channelId)
  try {
    await testChannel(channelId)
  } catch {
    // Error handled in composable
  } finally {
    testingChannels.value.delete(channelId)
  }
}

async function handleDelete(channelId: string) {
  try {
    await removeChannel(channelId)
  } catch {
    // Error handled in composable
  } finally {
    confirmDeleteId.value = null
  }
}

function getTypeBadgeClass(type: string): string {
  switch (type) {
    case 'email': return 'badge-email'
    case 'webhook': return 'badge-webhook'
    default: return ''
  }
}

function getStatusClass(channel: ChannelInfo): string {
  if (channel.lastTestSuccess === null) return 'status-unknown'
  return channel.lastTestSuccess ? 'status-ok' : 'status-fail'
}

function getChannelDetail(channel: ChannelInfo): string {
  if (channel.type === 'email') {
    const config = channel.config as { host?: string; username?: string }
    return `${config.host || ''} (${config.username || ''})`
  } else {
    const config = channel.config as { url?: string; method?: string }
    return `${config.method || 'POST'} ${config.url || ''}`
  }
}

onMounted(() => {
  loadChannels()
})
</script>

<template>
  <div class="channels-section">
    <header class="section-header">
      <div class="header-top">
        <div>
          <h2 class="section-title">{{ t('settings.nav.channels') }}</h2>
          <p class="section-subtitle">{{ t('sections.channels.subtitle') }}</p>
        </div>
        <button class="add-btn" @click="openForm">{{ t('sections.channels.addBtn') }}</button>
      </div>
    </header>

    <!-- Loading -->
    <div v-if="loading && channels.length === 0" class="loading-state">
      {{ t('sections.channels.loading') }}
    </div>

    <!-- Error -->
    <div v-if="error && channels.length === 0" class="error-state">
      <p>{{ error }}</p>
      <button class="retry-btn" @click="loadChannels">{{ t('common.retry') }}</button>
    </div>

    <!-- Add Form -->
    <div v-if="showForm" class="form-panel">
      <h3 class="form-title">{{ t('sections.channels.formTitle') }}</h3>

      <div class="form-grid">
        <div class="form-group">
          <label class="form-label">{{ t('sections.channels.fields.nameLabel') }}</label>
          <input v-model="formName" class="form-input" :placeholder="t('sections.channels.fields.namePlaceholder')" />
        </div>

        <div class="form-group">
          <label class="form-label">{{ t('sections.channels.fields.typeLabel') }}</label>
          <select v-model="formType" class="form-input">
            <option value="email">{{ t('sections.channels.fields.typeOptions.email') }}</option>
            <option value="webhook">{{ t('sections.channels.fields.typeOptions.webhook') }}</option>
          </select>
        </div>
      </div>

      <!-- Email Config -->
      <template v-if="formType === 'email'">
        <div class="form-grid">
          <div class="form-group">
            <label class="form-label">{{ t('sections.channels.fields.smtpHostLabel') }}</label>
            <input v-model="emailHost" class="form-input" :placeholder="t('sections.channels.fields.smtpHostPlaceholder')" />
          </div>
          <div class="form-group">
            <label class="form-label">{{ t('sections.channels.fields.portLabel') }}</label>
            <input v-model.number="emailPort" type="number" class="form-input" :placeholder="t('sections.channels.fields.portPlaceholder')" />
          </div>
          <div class="form-group">
            <label class="form-label">{{ t('sections.channels.fields.usernameLabel') }}</label>
            <input v-model="emailUsername" class="form-input" :placeholder="t('sections.channels.fields.usernamePlaceholder')" />
          </div>
          <div class="form-group">
            <label class="form-label">{{ t('sections.channels.fields.fromLabel') }}</label>
            <input v-model="emailFrom" class="form-input" :placeholder="t('sections.channels.fields.fromPlaceholder')" />
          </div>
        </div>

        <div class="form-grid">
          <div class="form-group">
            <label class="form-label">{{ t('sections.channels.fields.tlsLabel') }}</label>
            <select v-model="emailTls" class="form-input">
              <option :value="true">{{ t('sections.channels.fields.tlsOptions.enabled') }}</option>
              <option :value="false">{{ t('sections.channels.fields.tlsOptions.disabled') }}</option>
            </select>
          </div>
        </div>

        <div class="form-group credential-group">
          <div class="credential-label-row">
            <label class="form-label">{{ t('sections.channels.fields.passwordLabel') }}</label>
            <button
              type="button"
              class="visibility-toggle"
              @click="passwordVisible = !passwordVisible"
              :title="passwordVisible ? t('sections.channels.fields.hidePassword') : t('sections.channels.fields.showPassword')"
            >
              <svg v-if="passwordVisible" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                <circle cx="12" cy="12" r="3"/>
              </svg>
              <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/>
                <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/>
                <line x1="1" y1="1" x2="23" y2="23"/>
                <path d="M14.12 14.12a3 3 0 1 1-4.24-4.24"/>
              </svg>
            </button>
          </div>
          <input
            v-model="emailPassword"
            :type="passwordVisible ? 'text' : 'password'"
            class="form-input"
            :placeholder="t('sections.channels.fields.passwordPlaceholder')"
            autocomplete="off"
          />
        </div>
      </template>

      <!-- Webhook Config -->
      <template v-if="formType === 'webhook'">
        <div class="form-grid">
          <div class="form-group" style="grid-column: 1 / -1">
            <label class="form-label">{{ t('sections.channels.fields.urlLabel') }}</label>
            <input v-model="webhookUrl" class="form-input" :placeholder="t('sections.channels.fields.urlPlaceholder')" />
          </div>
          <div class="form-group">
            <label class="form-label">{{ t('sections.channels.fields.methodLabel') }}</label>
            <select v-model="webhookMethod" class="form-input">
              <option value="POST">{{ t('sections.channels.fields.methodOptions.post') }}</option>
              <option value="PUT">{{ t('sections.channels.fields.methodOptions.put') }}</option>
            </select>
          </div>
        </div>

        <div class="form-group credential-group">
          <label class="form-label">{{ t('sections.channels.fields.headersLabel') }}</label>
          <textarea
            v-model="webhookHeaders"
            class="form-textarea"
            rows="2"
            :placeholder="t('sections.channels.fields.headersPlaceholder')"
            spellcheck="false"
          />
        </div>

        <div class="form-group credential-group">
          <div class="credential-label-row">
            <label class="form-label">{{ t('sections.channels.fields.signingSecretLabel') }}</label>
            <button
              type="button"
              class="visibility-toggle"
              @click="secretVisible = !secretVisible"
              :title="secretVisible ? t('sections.channels.fields.hideSecret') : t('sections.channels.fields.showSecret')"
            >
              <svg v-if="secretVisible" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                <circle cx="12" cy="12" r="3"/>
              </svg>
              <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/>
                <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/>
                <line x1="1" y1="1" x2="23" y2="23"/>
                <path d="M14.12 14.12a3 3 0 1 1-4.24-4.24"/>
              </svg>
            </button>
          </div>
          <input
            v-model="webhookSecret"
            :type="secretVisible ? 'text' : 'password'"
            class="form-input"
            :placeholder="t('sections.channels.fields.signingSecretPlaceholder')"
            autocomplete="off"
          />
        </div>
      </template>

      <div v-if="formError" class="form-error">{{ formError }}</div>

      <div class="form-actions">
        <button class="cancel-btn" @click="closeForm">{{ t('common.cancel') }}</button>
        <button class="submit-btn" :disabled="submitting" @click="handleSubmit">
          {{ submitting ? t('sections.channels.creatingBtn') : t('sections.channels.createBtn') }}
        </button>
      </div>
    </div>

    <!-- Channel List -->
    <div v-if="channels.length > 0" class="channel-list">
      <div v-for="channel in channels" :key="channel.id" class="channel-card">
        <div class="channel-header">
          <div class="channel-info">
            <span class="channel-name">{{ channel.name }}</span>
            <span class="type-badge" :class="getTypeBadgeClass(channel.type)">
              {{ channel.type }}
            </span>
            <span class="status-dot" :class="getStatusClass(channel)" :title="
              channel.lastTestSuccess === null ? t('sections.channels.notTested') :
              channel.lastTestSuccess ? t('sections.channels.connected') : t('sections.channels.failed')
            " />
          </div>
          <div class="channel-actions">
            <button
              class="test-btn"
              :disabled="testingChannels.has(channel.id)"
              @click="handleTest(channel.id)"
            >
              {{ testingChannels.has(channel.id) ? t('sections.channels.testingBtn') : t('common.test') }}
            </button>
            <button
              v-if="confirmDeleteId !== channel.id"
              class="delete-btn"
              @click="confirmDeleteId = channel.id"
            >
              {{ t('common.delete') }}
            </button>
            <template v-else>
              <button class="confirm-delete-btn" @click="handleDelete(channel.id)">{{ t('common.confirm') }}</button>
              <button class="cancel-delete-btn" @click="confirmDeleteId = null">{{ t('common.cancel') }}</button>
            </template>
          </div>
        </div>
        <div class="channel-details">
          <span class="channel-detail">{{ getChannelDetail(channel) }}</span>
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div v-if="!loading && !showForm && channels.length === 0 && !error" class="empty-state">
      <p>{{ t('sections.channels.empty') }}</p>
      <p class="empty-hint">{{ t('sections.channels.emptyHint') }}</p>
    </div>

    <!-- Global Error -->
    <div v-if="error && channels.length > 0" class="error-banner">{{ error }}</div>
  </div>
</template>

<style scoped>
.channels-section {
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
}

.credential-group {
  margin-bottom: 12px;
}

.credential-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.visibility-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  color: var(--color-gray-dark);
  cursor: pointer;
  border-radius: var(--radius-md);
  transition: background 0.15s ease, color 0.15s ease;
}

.visibility-toggle:hover {
  background: var(--color-gray-bg);
  color: var(--color-black);
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

/* Channel List */
.channel-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.channel-card {
  padding: 14px 16px;
  border: var(--border);
  border-radius: var(--radius-lg);
  background: var(--color-white);
  transition: border-color 0.15s ease;
}

.channel-card:hover {
  border-color: var(--color-black);
}

.channel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.channel-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.channel-name {
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

.channel-actions {
  display: flex;
  gap: 6px;
}

.test-btn, .delete-btn, .confirm-delete-btn, .cancel-delete-btn {
  padding: 4px 10px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 12px;
  cursor: pointer;
}

.test-btn:hover, .delete-btn:hover {
  background: var(--color-gray-bg);
}

.test-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.confirm-delete-btn {
  background: #dc2626;
  color: var(--color-white);
  border-color: #dc2626;
}

.confirm-delete-btn:hover {
  background: #b91c1c;
}

.cancel-delete-btn:hover {
  background: var(--color-gray-bg);
}

.channel-details {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  font-size: 12px;
  color: var(--color-gray-dark);
}

.channel-detail {
  font-family: var(--font-mono);
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
</style>
