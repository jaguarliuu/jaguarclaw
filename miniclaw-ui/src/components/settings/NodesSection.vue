<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useNodeConsole } from '@/composables/useNodeConsole'
import { useI18n } from '@/i18n'
import type { ConnectorType, AuthType, SafetyPolicy, NodeInfo } from '@/types'

const { nodes, loading, error, loadNodes, registerNode, removeNode, testNode } = useNodeConsole()
const { t } = useI18n()

// Form state
const showForm = ref(false)
const formAlias = ref('')
const formDisplayName = ref('')
const formConnectorType = ref<ConnectorType>('ssh')
const formHost = ref('')
const formPort = ref<number | undefined>(undefined)
const formUsername = ref('')
const formAuthType = ref<AuthType>('password')
const formCredential = ref('')
const formTags = ref('')
const formSafetyPolicy = ref<SafetyPolicy>('strict')
const formError = ref<string | null>(null)
const submitting = ref(false)
const credentialVisible = ref(false)

// Testing state per node
const testingNodes = ref<Set<string>>(new Set())

// Delete confirmation
const confirmDeleteId = ref<string | null>(null)

function resetForm() {
  formAlias.value = ''
  formDisplayName.value = ''
  formConnectorType.value = 'ssh'
  formHost.value = ''
  formPort.value = undefined
  formUsername.value = ''
  formAuthType.value = 'password'
  formCredential.value = ''
  formTags.value = ''
  formSafetyPolicy.value = 'strict'
  formError.value = null
  credentialVisible.value = false
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
  if (!formAlias.value.trim()) {
    formError.value = t('sections.nodes.errors.aliasRequired')
    return
  }
  if (!formCredential.value.trim()) {
    formError.value = t('sections.nodes.errors.credentialRequired')
    return
  }

  submitting.value = true
  formError.value = null
  try {
    await registerNode({
      alias: formAlias.value.trim(),
      displayName: formDisplayName.value.trim() || undefined,
      connectorType: formConnectorType.value,
      host: formHost.value.trim() || undefined,
      port: formPort.value || undefined,
      username: formUsername.value.trim() || undefined,
      authType: formAuthType.value,
      credential: formCredential.value,
      tags: formTags.value.trim() || undefined,
      safetyPolicy: formSafetyPolicy.value
    })
    closeForm()
  } catch (e) {
    formError.value = e instanceof Error ? e.message : t('sections.nodes.errors.failedToRegister')
  } finally {
    submitting.value = false
  }
}

async function handleTest(nodeId: string) {
  testingNodes.value.add(nodeId)
  try {
    await testNode(nodeId)
  } catch {
    // Error handled in composable
  } finally {
    testingNodes.value.delete(nodeId)
  }
}

async function handleDelete(nodeId: string) {
  try {
    await removeNode(nodeId)
  } catch {
    // Error handled in composable
  } finally {
    confirmDeleteId.value = null
  }
}

function getTypeBadgeClass(type: string): string {
  switch (type) {
    case 'ssh': return 'badge-ssh'
    case 'k8s': return 'badge-k8s'
    case 'db': return 'badge-db'
    default: return ''
  }
}

function getStatusClass(node: NodeInfo): string {
  if (node.lastTestSuccess === null) return 'status-unknown'
  return node.lastTestSuccess ? 'status-ok' : 'status-fail'
}

function getAuthTypeOptions(type: ConnectorType): { value: AuthType; label: string }[] {
  switch (type) {
    case 'ssh': return [
      { value: 'password', label: t('sections.nodes.fields.authOptions.password') },
      { value: 'key', label: t('sections.nodes.fields.authOptions.sshKey') }
    ]
    case 'k8s': return [
      { value: 'kubeconfig', label: t('sections.nodes.fields.authOptions.kubeconfig') },
      { value: 'token', label: t('sections.nodes.fields.authOptions.token') }
    ]
    case 'db': return [
      { value: 'password', label: t('sections.nodes.fields.authOptions.password') },
      { value: 'token', label: t('sections.nodes.fields.authOptions.token') }
    ]
  }
}

function getCredentialPlaceholder(): string {
  switch (formAuthType.value) {
    case 'password': return t('sections.nodes.fields.credentialPlaceholders.password')
    case 'key': return t('sections.nodes.fields.credentialPlaceholders.sshKey')
    case 'kubeconfig': return t('sections.nodes.fields.credentialPlaceholders.kubeconfig')
    case 'token': return t('sections.nodes.fields.credentialPlaceholders.token')
  }
}

onMounted(() => {
  loadNodes()
})
</script>

<template>
  <div class="nodes-section">
    <header class="section-header">
      <div class="header-top">
        <div>
          <h2 class="section-title">{{ t('settings.nav.nodes') }}</h2>
          <p class="section-subtitle">{{ t('sections.nodes.subtitle') }}</p>
        </div>
        <button class="add-btn" @click="openForm">{{ t('sections.nodes.addBtn') }}</button>
      </div>
    </header>

    <!-- Loading -->
    <div v-if="loading && nodes.length === 0" class="loading-state">
      {{ t('sections.nodes.loading') }}
    </div>

    <!-- Error -->
    <div v-if="error && nodes.length === 0" class="error-state">
      <p>{{ error }}</p>
      <button class="retry-btn" @click="loadNodes">{{ t('common.retry') }}</button>
    </div>

    <!-- Add Form -->
    <div v-if="showForm" class="form-panel">
      <h3 class="form-title">{{ t('sections.nodes.formTitle') }}</h3>

      <div class="form-grid">
        <div class="form-group">
          <label class="form-label">{{ t('sections.nodes.fields.aliasLabel') }}</label>
          <input v-model="formAlias" class="form-input" :placeholder="t('sections.nodes.fields.aliasPlaceholder')" />
        </div>

        <div class="form-group">
          <label class="form-label">{{ t('sections.nodes.fields.displayNameLabel') }}</label>
          <input v-model="formDisplayName" class="form-input" :placeholder="t('sections.nodes.fields.displayNamePlaceholder')" />
        </div>

        <div class="form-group">
          <label class="form-label">{{ t('sections.nodes.fields.typeLabel') }}</label>
          <select v-model="formConnectorType" class="form-input">
            <option value="ssh">{{ t('sections.nodes.fields.typeOptions.ssh') }}</option>
            <option value="k8s">{{ t('sections.nodes.fields.typeOptions.k8s') }}</option>
            <option value="db">{{ t('sections.nodes.fields.typeOptions.db') }}</option>
          </select>
        </div>

        <div class="form-group" v-if="formConnectorType !== 'k8s'">
          <label class="form-label">{{ t('sections.nodes.fields.hostLabel') }}</label>
          <input v-model="formHost" class="form-input" :placeholder="t('sections.nodes.fields.hostPlaceholder')" />
        </div>

        <div class="form-group" v-if="formConnectorType === 'ssh'">
          <label class="form-label">{{ t('sections.nodes.fields.portLabel') }}</label>
          <input v-model.number="formPort" type="number" class="form-input" :placeholder="t('sections.nodes.fields.portPlaceholder')" />
        </div>

        <div class="form-group" v-if="formConnectorType === 'ssh'">
          <label class="form-label">{{ t('sections.nodes.fields.usernameLabel') }}</label>
          <input v-model="formUsername" class="form-input" :placeholder="t('sections.nodes.fields.usernamePlaceholder')" />
        </div>

        <div class="form-group">
          <label class="form-label">{{ t('sections.nodes.fields.authTypeLabel') }}</label>
          <select v-model="formAuthType" class="form-input">
            <option v-for="opt in getAuthTypeOptions(formConnectorType)" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </option>
          </select>
        </div>

        <div class="form-group">
          <label class="form-label">{{ t('sections.nodes.fields.tagsLabel') }}</label>
          <input v-model="formTags" class="form-input" :placeholder="t('sections.nodes.fields.tagsPlaceholder')" />
        </div>

        <div class="form-group">
          <label class="form-label">{{ t('sections.nodes.fields.safetyLabel') }}</label>
          <select v-model="formSafetyPolicy" class="form-input">
            <option value="strict">{{ t('sections.nodes.fields.safetyOptions.strict') }}</option>
            <option value="standard">{{ t('sections.nodes.fields.safetyOptions.standard') }}</option>
            <option value="relaxed">{{ t('sections.nodes.fields.safetyOptions.relaxed') }}</option>
          </select>
        </div>
      </div>

      <div class="form-group credential-group">
        <div class="credential-label-row">
          <label class="form-label">{{ t('sections.nodes.fields.credentialLabel') }}</label>
          <button
            type="button"
            class="visibility-toggle"
            @click="credentialVisible = !credentialVisible"
            :title="credentialVisible ? t('sections.nodes.hideCredential') : t('sections.nodes.showCredential')"
          >
            <svg v-if="credentialVisible" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
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
        <!-- Single-line credentials (password, token) use input -->
        <input
          v-if="formAuthType === 'password' || formAuthType === 'token'"
          v-model="formCredential"
          :type="credentialVisible ? 'text' : 'password'"
          class="form-input"
          :placeholder="getCredentialPlaceholder()"
          autocomplete="off"
        />
        <!-- Multi-line credentials (SSH key, kubeconfig) use textarea with masking -->
        <textarea
          v-else
          v-model="formCredential"
          class="form-textarea"
          :class="{ 'credential-masked': !credentialVisible }"
          :placeholder="getCredentialPlaceholder()"
          rows="4"
          autocomplete="off"
          spellcheck="false"
        />
      </div>

      <div v-if="formError" class="form-error">{{ formError }}</div>

      <div class="form-actions">
        <button class="cancel-btn" @click="closeForm">{{ t('common.cancel') }}</button>
        <button class="submit-btn" :disabled="submitting" @click="handleSubmit">
          {{ submitting ? t('sections.nodes.registeringBtn') : t('sections.nodes.registerBtn') }}
        </button>
      </div>
    </div>

    <!-- Node List -->
    <div v-if="nodes.length > 0" class="node-list">
      <div v-for="node in nodes" :key="node.id" class="node-card">
        <div class="node-header">
          <div class="node-info">
            <span class="node-alias">{{ node.alias }}</span>
            <span class="type-badge" :class="getTypeBadgeClass(node.connectorType)">
              {{ node.connectorType }}
            </span>
            <span class="status-dot" :class="getStatusClass(node)" :title="
              node.lastTestSuccess === null ? t('sections.nodes.notTested') :
              node.lastTestSuccess ? t('sections.nodes.connected') : t('sections.nodes.failed')
            " />
          </div>
          <div class="node-actions">
            <button
              class="test-btn"
              :disabled="testingNodes.has(node.id)"
              @click="handleTest(node.id)"
            >
              {{ testingNodes.has(node.id) ? t('sections.nodes.testingBtn') : t('common.test') }}
            </button>
            <button
              v-if="confirmDeleteId !== node.id"
              class="delete-btn"
              @click="confirmDeleteId = node.id"
            >
              {{ t('common.delete') }}
            </button>
            <template v-else>
              <button class="confirm-delete-btn" @click="handleDelete(node.id)">{{ t('common.confirm') }}</button>
              <button class="cancel-delete-btn" @click="confirmDeleteId = null">{{ t('common.cancel') }}</button>
            </template>
          </div>
        </div>
        <div class="node-details">
          <span v-if="node.displayName" class="node-detail">{{ node.displayName }}</span>
          <span v-if="node.host" class="node-detail">{{ node.host }}{{ node.port ? ':' + node.port : '' }}</span>
          <span v-if="node.username" class="node-detail">@{{ node.username }}</span>
          <span v-if="node.tags" class="node-tags">
            <span v-for="tag in node.tags.split(',')" :key="tag" class="tag">{{ tag.trim() }}</span>
          </span>
          <span class="node-policy">{{ t('sections.nodes.policyLabel', { name: node.safetyPolicy }) }}</span>
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div v-if="!loading && !showForm && nodes.length === 0 && !error" class="empty-state">
      <p>{{ t('sections.nodes.empty') }}</p>
      <p class="empty-hint">{{ t('sections.nodes.emptyHint') }}</p>
    </div>

    <!-- Global Error -->
    <div v-if="error && nodes.length > 0" class="error-banner">{{ error }}</div>
  </div>
</template>

<style scoped>
.nodes-section {
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

.credential-masked {
  -webkit-text-security: disc;
  color: var(--color-gray-dark);
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

/* Node List */
.node-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.node-card {
  padding: 14px 16px;
  border: var(--border);
  border-radius: var(--radius-lg);
  background: var(--color-white);
  transition: border-color 0.15s ease;
}

.node-card:hover {
  border-color: var(--color-black);
}

.node-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.node-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.node-alias {
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

.badge-ssh {
  background: #dbeafe;
  color: #1d4ed8;
}

.badge-k8s {
  background: #dcfce7;
  color: #15803d;
}

.badge-db {
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

.node-actions {
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

.node-details {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  font-size: 12px;
  color: var(--color-gray-dark);
}

.node-detail {
  font-family: var(--font-mono);
}

.node-tags {
  display: flex;
  gap: 4px;
}

.tag {
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  background: var(--color-gray-bg);
  font-family: var(--font-mono);
  font-size: 11px;
}

.node-policy {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-gray-dark);
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
