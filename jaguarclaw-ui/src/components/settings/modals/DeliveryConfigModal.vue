<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from '@/i18n'
import type { EmailDeliveryConfig, WebhookDeliveryConfig } from '@/types'

const { t } = useI18n()

interface EditableEndpoint {
  alias: string
  url: string
  method: string
  trigger: string
  enabled: boolean
  headers: { key: string; value: string }[]
}

const props = defineProps<{
  email: EmailDeliveryConfig
  webhook: WebhookDeliveryConfig
}>()

const emit = defineEmits<{
  close: []
  save: [payload: {
    email: {
      enabled: boolean
      host: string
      port: number
      username: string
      from: string
      tls: boolean
      password: string
    }
    webhook: {
      enabled: boolean
      endpoints: Array<{
        alias: string
        url: string
        method: string
        trigger?: string
        enabled: boolean
        headers: Record<string, string>
      }>
    }
  }]
}>()

// Email state
const emailEnabled = ref(props.email.enabled)
const host = ref(props.email.host || '')
const port = ref(props.email.port || 587)
const username = ref(props.email.username || '')
const from = ref(props.email.from || '')
const tls = ref(props.email.tls ?? true)
const password = ref('')
const passwordVisible = ref(false)

// Webhook state
const webhookEnabled = ref(props.webhook.enabled)
const endpoints = ref<EditableEndpoint[]>(
  (props.webhook.endpoints || []).map(ep => ({
    alias: ep.alias || '',
    url: ep.url || '',
    method: ep.method || 'POST',
    trigger: ep.trigger || '',
    enabled: ep.enabled ?? true,
    headers: Object.entries(ep.headers || {}).map(([key, value]) => ({ key, value }))
  }))
)

function addEndpoint() {
  endpoints.value.push({
    alias: '',
    url: '',
    method: 'POST',
    trigger: '',
    enabled: true,
    headers: []
  })
}

function removeEndpoint(index: number) {
  endpoints.value.splice(index, 1)
}

function addHeader(ep: EditableEndpoint) {
  ep.headers.push({ key: '', value: '' })
}

function removeHeader(ep: EditableEndpoint, index: number) {
  ep.headers.splice(index, 1)
}

function handleSave() {
  emit('save', {
    email: {
      enabled: emailEnabled.value,
      host: host.value.trim(),
      port: Number(port.value) || 587,
      username: username.value.trim(),
      from: from.value.trim(),
      tls: tls.value,
      password: password.value.trim() || props.email.password || ''
    },
    webhook: {
      enabled: webhookEnabled.value,
      endpoints: endpoints.value
        .filter(ep => ep.alias.trim() && ep.url.trim())
        .map(ep => ({
          alias: ep.alias.trim(),
          url: ep.url.trim(),
          method: ep.method || 'POST',
          trigger: ep.trigger.trim() || undefined,
          enabled: ep.enabled,
          headers: Object.fromEntries(
            ep.headers
              .filter(h => h.key.trim())
              .map(h => [h.key.trim(), h.value.trim()])
          )
        }))
    }
  })
}
</script>

<template>
  <div class="modal-overlay" @click.self="emit('close')">
    <div class="modal-content">
      <div class="modal-header">
        <div>
          <h3 class="modal-title">{{ t('sections.delivery.title') }}</h3>
          <p class="modal-subtitle">{{ t('sections.delivery.subtitle') }}</p>
        </div>
        <button class="btn-close" @click="emit('close')">&#x2715;</button>
      </div>

      <div class="modal-body">
        <!-- Email Section -->
        <div class="config-section">
          <div class="section-header">
            <div>
              <label class="form-label">{{ t('sections.delivery.emailLabel') }}</label>
              <p class="field-desc">{{ t('sections.delivery.emailDesc') }}</p>
            </div>
            <label class="toggle-switch">
              <input type="checkbox" v-model="emailEnabled" />
              <span class="toggle-slider"></span>
            </label>
          </div>

          <div v-if="emailEnabled" class="section-body">
            <div class="form-grid">
              <div class="form-field">
                <label class="field-label">{{ t('sections.delivery.hostLabel') }}</label>
                <input v-model="host" class="form-input" placeholder="smtp.example.com" />
              </div>
              <div class="form-field">
                <label class="field-label">{{ t('sections.delivery.portLabel') }}</label>
                <input v-model.number="port" type="number" class="form-input" placeholder="587" />
              </div>
              <div class="form-field">
                <label class="field-label">{{ t('sections.delivery.usernameLabel') }}</label>
                <input v-model="username" class="form-input" placeholder="user@example.com" />
              </div>
              <div class="form-field">
                <label class="field-label">{{ t('sections.delivery.fromLabel') }}</label>
                <input v-model="from" class="form-input" placeholder="noreply@example.com" />
              </div>
            </div>

            <div class="form-field">
              <label class="field-label">{{ t('sections.delivery.passwordLabel') }}</label>
              <div class="input-with-toggle">
                <input
                  v-model="password"
                  :type="passwordVisible ? 'text' : 'password'"
                  class="form-input"
                  :placeholder="props.email.password ? props.email.password : t('sections.delivery.passwordPlaceholder')"
                  autocomplete="off"
                  spellcheck="false"
                />
                <button
                  type="button"
                  class="visibility-toggle"
                  @click="passwordVisible = !passwordVisible"
                >
                  {{ passwordVisible ? t('sections.delivery.hidePassword') : t('sections.delivery.showPassword') }}
                </button>
              </div>
              <span class="form-hint">{{ t('sections.delivery.passwordHint') }}</span>
            </div>

            <div class="tls-row">
              <label class="toggle-switch toggle-sm">
                <input type="checkbox" v-model="tls" />
                <span class="toggle-slider"></span>
              </label>
              <span class="tls-label">{{ t('sections.delivery.tlsLabel') }}</span>
            </div>
          </div>
        </div>

        <!-- Webhook Section -->
        <div class="config-section">
          <div class="section-header">
            <div>
              <label class="form-label">{{ t('sections.delivery.webhookLabel') }}</label>
              <p class="field-desc">{{ t('sections.delivery.webhookDesc') }}</p>
            </div>
            <label class="toggle-switch">
              <input type="checkbox" v-model="webhookEnabled" />
              <span class="toggle-slider"></span>
            </label>
          </div>

          <div v-if="webhookEnabled" class="section-body">
            <!-- Endpoint Cards -->
            <div v-if="endpoints.length === 0" class="empty-hint">
              {{ t('sections.delivery.noEndpoints') }}
            </div>

            <div v-for="(ep, idx) in endpoints" :key="idx" class="endpoint-card">
              <div class="endpoint-header">
                <div class="endpoint-info">
                  <span class="endpoint-alias">{{ ep.alias || `Endpoint #${idx + 1}` }}</span>
                  <span class="endpoint-method-badge">{{ ep.method }}</span>
                </div>
                <div class="endpoint-actions">
                  <label class="toggle-switch toggle-sm">
                    <input type="checkbox" v-model="ep.enabled" />
                    <span class="toggle-slider"></span>
                  </label>
                  <button class="btn-remove" @click="removeEndpoint(idx)" :title="t('sections.delivery.removeEndpoint')">
                    &times;
                  </button>
                </div>
              </div>

              <div class="endpoint-body">
                <div class="form-grid">
                  <div class="form-field">
                    <label class="field-label">{{ t('sections.delivery.aliasLabel') }}</label>
                    <input v-model="ep.alias" class="form-input" :placeholder="t('sections.delivery.aliasPlaceholder')" />
                  </div>
                  <div class="form-field">
                    <label class="field-label">{{ t('sections.delivery.methodLabel') }}</label>
                    <select v-model="ep.method" class="form-input">
                      <option value="POST">POST</option>
                      <option value="PUT">PUT</option>
                    </select>
                  </div>
                </div>

                <div class="form-field">
                  <label class="field-label">{{ t('sections.delivery.urlLabel') }}</label>
                  <input v-model="ep.url" class="form-input" :placeholder="t('sections.delivery.urlPlaceholder')" />
                </div>

                <div class="form-field">
                  <label class="field-label">{{ t('sections.delivery.triggerLabel') }}</label>
                  <input v-model="ep.trigger" class="form-input" :placeholder="t('sections.delivery.triggerPlaceholder')" />
                </div>

                <!-- Headers -->
                <div class="form-field">
                  <label class="field-label">{{ t('sections.delivery.headersLabel') }}</label>
                  <div v-for="(h, hIdx) in ep.headers" :key="hIdx" class="header-row">
                    <input v-model="h.key" class="form-input header-key" :placeholder="t('sections.delivery.headersKeyPlaceholder')" />
                    <input v-model="h.value" class="form-input header-value" :placeholder="t('sections.delivery.headersValuePlaceholder')" />
                    <button class="btn-remove-sm" @click="removeHeader(ep, hIdx)">&times;</button>
                  </div>
                  <button class="add-btn" @click="addHeader(ep)">{{ t('sections.delivery.addHeaderBtn') }}</button>
                </div>
              </div>
            </div>

            <button class="add-btn add-endpoint-btn" @click="addEndpoint">
              {{ t('sections.delivery.addEndpointBtn') }}
            </button>
          </div>
        </div>
      </div>

      <div class="modal-footer">
        <button class="btn-secondary" @click="emit('close')">{{ t('common.cancel') }}</button>
        <button class="btn-primary" @click="handleSave">{{ t('sections.delivery.saveBtn') }}</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: var(--color-white);
  border-radius: var(--radius-lg);
  width: 90%;
  max-width: 640px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-lg);
}

.modal-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: var(--border);
  gap: 20px;
}

.modal-title {
  font-family: var(--font-mono);
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 4px 0;
}

.modal-subtitle {
  font-size: 13px;
  color: var(--color-gray-600);
  margin: 0;
}

.btn-close {
  background: none;
  border: none;
  font-size: 20px;
  cursor: pointer;
  color: var(--color-gray-400);
  padding: 4px;
  line-height: 1;
  flex-shrink: 0;
}

.btn-close:hover {
  color: var(--color-black);
}

.modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.config-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-top: 4px;
}

.form-label {
  display: block;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-gray-700);
  margin: 0;
}

.field-desc {
  font-size: 12px;
  color: var(--color-gray-dark);
  margin: 2px 0 0 0;
}

.field-label {
  display: block;
  font-size: 11px;
  font-weight: 500;
  color: var(--color-gray-700);
  margin-bottom: 4px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.form-field {
  display: flex;
  flex-direction: column;
}

.form-input {
  padding: 8px 10px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 13px;
  width: 100%;
  box-sizing: border-box;
}

.form-input:focus {
  outline: none;
  border-color: var(--color-black);
  box-shadow: 0 0 0 3px rgba(0,0,0,0.03);
}

select.form-input {
  cursor: pointer;
}

.input-with-toggle {
  display: flex;
  gap: 0;
}

.input-with-toggle .form-input {
  flex: 1;
  border-right: none;
  border-top-right-radius: 0;
  border-bottom-right-radius: 0;
}

.visibility-toggle {
  padding: 8px 12px;
  border: var(--border);
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
  background: var(--color-gray-bg);
  font-family: var(--font-mono);
  font-size: 11px;
  cursor: pointer;
  white-space: nowrap;
  color: var(--color-gray-dark);
}

.visibility-toggle:hover {
  background: var(--color-white);
  color: var(--color-black);
}

.form-hint {
  display: block;
  font-size: 11px;
  color: var(--color-gray-dark);
  margin-top: 4px;
}

.tls-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}

.tls-label {
  font-size: 13px;
  color: var(--color-gray-700);
}

/* Toggle switch — matches SearchProvidersModal */
.toggle-switch {
  position: relative;
  display: inline-block;
  width: 36px;
  height: 20px;
  cursor: pointer;
  flex-shrink: 0;
}

.toggle-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.toggle-slider {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: #e5e7eb;
  transition: 0.2s;
  border-radius: 10px;
}

.toggle-slider::before {
  content: '';
  position: absolute;
  height: 16px;
  width: 16px;
  left: 2px;
  bottom: 2px;
  background: white;
  transition: 0.2s;
  border-radius: 50%;
}

.toggle-switch input:checked + .toggle-slider {
  background: var(--color-black);
}

.toggle-switch input:checked + .toggle-slider::before {
  transform: translateX(16px);
}

.toggle-sm {
  width: 30px;
  height: 16px;
}

.toggle-sm .toggle-slider {
  border-radius: 8px;
}

.toggle-sm .toggle-slider::before {
  height: 12px;
  width: 12px;
}

.toggle-sm input:checked + .toggle-slider::before {
  transform: translateX(14px);
}

/* Endpoint card */
.endpoint-card {
  border: var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.endpoint-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: var(--color-gray-bg);
  border-bottom: var(--border-light);
}

.endpoint-info {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.endpoint-alias {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.endpoint-method-badge {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  background: var(--color-gray-100);
  color: var(--color-gray-700);
  flex-shrink: 0;
}

.endpoint-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.endpoint-body {
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.btn-remove {
  background: none;
  border: none;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  color: var(--color-gray-400);
  padding: 2px 4px;
}

.btn-remove:hover {
  color: #dc2626;
}

/* Header rows */
.header-row {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-bottom: 4px;
}

.header-key {
  flex: 2;
}

.header-value {
  flex: 3;
}

.btn-remove-sm {
  background: none;
  border: none;
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
  color: var(--color-gray-400);
  padding: 2px;
  flex-shrink: 0;
}

.btn-remove-sm:hover {
  color: #dc2626;
}

.add-btn {
  padding: 6px 12px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
  color: var(--color-gray-dark);
  align-self: flex-start;
}

.add-btn:hover {
  background: var(--color-gray-bg);
  border-color: var(--color-black);
  color: var(--color-black);
}

.add-endpoint-btn {
  align-self: stretch;
  text-align: center;
  border-style: dashed;
}

.empty-hint {
  font-size: 12px;
  color: var(--color-gray-dark);
  font-style: italic;
}

/* Footer */
.modal-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 16px 24px;
  border-top: var(--border);
  gap: 12px;
}

.btn-secondary {
  padding: 10px 16px;
  background: var(--color-white);
  border: var(--border);
  border-radius: var(--radius-md);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast);
}

.btn-secondary:hover {
  background: var(--color-gray-100);
}

.btn-primary {
  padding: 10px 16px;
  background: var(--color-black);
  color: var(--color-white);
  border: none;
  border-radius: var(--radius-md);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast);
}

.btn-primary:hover {
  background: var(--color-gray-800);
}

@media (max-width: 768px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
