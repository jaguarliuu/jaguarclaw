<script setup lang="ts">
import { computed, ref } from 'vue'
import type { EmailDeliveryConfig, WebhookDeliveryConfig } from '@/types'

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

const emailEnabled = ref(props.email.enabled)
const host = ref(props.email.host || '')
const port = ref(props.email.port || 587)
const username = ref(props.email.username || '')
const from = ref(props.email.from || '')
const tls = ref(props.email.tls ?? true)
const password = ref('')

const webhookEnabled = ref(props.webhook.enabled)
const endpointsJson = ref(JSON.stringify(props.webhook.endpoints || [], null, 2))
const localError = ref<string | null>(null)

const maskedPasswordHint = computed(() => {
  if (!props.email.password) return ''
  return `当前密码已保存（${props.email.password}）`
})

function handleSave() {
  localError.value = null
  try {
    const parsed = JSON.parse(endpointsJson.value || '[]')
    if (!Array.isArray(parsed)) {
      localError.value = 'Webhook endpoints JSON 必须是数组'
      return
    }

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
        endpoints: parsed
      }
    })
  } catch {
    localError.value = 'Webhook endpoints JSON 格式不正确'
  }
}
</script>

<template>
  <div class="modal-overlay" @click.self="emit('close')">
    <div class="modal">
      <div class="modal-header">
        <h3>Delivery Tools</h3>
        <button class="close-btn" @click="emit('close')">×</button>
      </div>

      <div class="section">
        <h4>Email</h4>
        <label class="row">
          <input v-model="emailEnabled" type="checkbox" />
          <span>Enable send_email tool</span>
        </label>
        <div class="grid">
          <input v-model="host" placeholder="SMTP host" />
          <input v-model.number="port" type="number" placeholder="Port" />
          <input v-model="username" placeholder="Username" />
          <input v-model="from" placeholder="From email" />
          <label class="row">
            <input v-model="tls" type="checkbox" />
            <span>TLS</span>
          </label>
          <div class="password-col">
            <input v-model="password" type="password" placeholder="Password (leave blank to keep)" />
            <div v-if="maskedPasswordHint" class="hint">{{ maskedPasswordHint }}</div>
          </div>
        </div>
      </div>

      <div class="section">
        <h4>Webhook</h4>
        <label class="row">
          <input v-model="webhookEnabled" type="checkbox" />
          <span>Enable send_webhook tool</span>
        </label>
        <textarea
          v-model="endpointsJson"
          rows="12"
          placeholder='[{"alias":"ops-alert","url":"https://...","method":"POST","trigger":"告警","enabled":true,"headers":{"Content-Type":"application/json"}}]'
        />
      </div>

      <div v-if="localError" class="error">{{ localError }}</div>

      <div class="actions">
        <button @click="emit('close')">Cancel</button>
        <button class="primary" @click="handleSave">Save</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.24);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal {
  width: min(860px, 92vw);
  max-height: 88vh;
  overflow-y: auto;
  background: var(--color-white);
  border: var(--border);
  border-radius: var(--radius-lg);
  padding: 18px;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.close-btn {
  border: none;
  background: transparent;
  font-size: 22px;
  cursor: pointer;
}

.section {
  margin-top: 14px;
}

.row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 8px 0;
}

.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

input, textarea {
  width: 100%;
  border: var(--border);
  border-radius: var(--radius-md);
  padding: 8px 10px;
  font-family: var(--font-mono);
  font-size: 12px;
}

.password-col {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.hint {
  font-size: 11px;
  color: var(--color-gray-dark);
}

.error {
  margin-top: 10px;
  color: #dc2626;
  font-size: 12px;
}

.actions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

button {
  border: var(--border);
  background: var(--color-white);
  border-radius: var(--radius-md);
  padding: 8px 12px;
  cursor: pointer;
  font-family: var(--font-mono);
  font-size: 12px;
}

button.primary {
  background: var(--color-black);
  color: var(--color-white);
  border-color: var(--color-black);
}

@media (max-width: 768px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>

