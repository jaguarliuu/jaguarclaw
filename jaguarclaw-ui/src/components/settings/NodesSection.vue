<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useNodeConsole } from '@/composables/useNodeConsole'
import { useI18n } from '@/i18n'
import Select from '@/components/common/Select.vue'
import type { SelectOption } from '@/components/common/Select.vue'
import type {
  ConnectorType,
  AuthType,
  SafetyPolicy,
  NodeInfo,
  NodeRegisterPayload,
  NodeImportResult
} from '@/types'

const {
  nodes,
  loading,
  error,
  loadNodes,
  registerNode,
  updateNode,
  removeNode,
  testNode,
  importNodes,
  downloadTemplate
} = useNodeConsole()
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
const editingNodeId = ref<string | null>(null)
const nodeTestMessages = ref<Record<string, string>>({})

// Testing state per node
const testingNodes = ref<Set<string>>(new Set())

// Delete confirmation
const confirmDeleteId = ref<string | null>(null)

// Import state
const importResult = ref<NodeImportResult | null>(null)
const importing = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)

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
  editingNodeId.value = null
  showForm.value = true
}

function openEditForm(node: NodeInfo) {
  resetForm()
  editingNodeId.value = node.id
  formAlias.value = node.alias
  formDisplayName.value = node.displayName || ''
  formConnectorType.value = node.connectorType
  formHost.value = node.host || ''
  formPort.value = node.port || undefined
  formUsername.value = node.username || ''
  formAuthType.value = node.authType || 'password'
  formTags.value = node.tags || ''
  formSafetyPolicy.value = node.safetyPolicy
  showForm.value = true
}

function closeForm() {
  showForm.value = false
  editingNodeId.value = null
  resetForm()
}

const isEditing = computed(() => !!editingNodeId.value)

async function handleSubmit() {
  if (!formAlias.value.trim()) {
    formError.value = t('sections.nodes.errors.aliasRequired')
    return
  }
  if (!isEditing.value && !formCredential.value.trim()) {
    formError.value = t('sections.nodes.errors.credentialRequired')
    return
  }

  submitting.value = true
  formError.value = null
  try {
    const payload: Partial<NodeRegisterPayload> = {
      alias: formAlias.value.trim(),
      displayName: formDisplayName.value.trim() || undefined,
      connectorType: formConnectorType.value,
      host: formHost.value.trim() || undefined,
      port: formPort.value || undefined,
      username: formUsername.value.trim() || undefined,
      authType: formAuthType.value,
      tags: formTags.value.trim() || undefined,
      safetyPolicy: formSafetyPolicy.value
    }

    const newCredential = formCredential.value.trim()
    if (newCredential) {
      payload.credential = newCredential
    }

    if (isEditing.value && editingNodeId.value) {
      await updateNode(editingNodeId.value, payload)
    } else {
      await registerNode(payload as NodeRegisterPayload)
    }

    closeForm()
  } catch (e) {
    formError.value = e instanceof Error
      ? e.message
      : isEditing.value
        ? t('sections.nodes.errors.failedToUpdate')
        : t('sections.nodes.errors.failedToRegister')
  } finally {
    submitting.value = false
  }
}

async function handleTest(nodeId: string) {
  testingNodes.value.add(nodeId)
  try {
    const result = await testNode(nodeId)
    if (result.success) {
      delete nodeTestMessages.value[nodeId]
    } else {
      nodeTestMessages.value[nodeId] = result.message || t('sections.nodes.errors.failedToTest')
    }
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

async function handleImportFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  input.value = ''
  importing.value = true
  importResult.value = null
  try {
    const text = await file.text()
    importResult.value = await importNodes(text)
  } catch {
    // Error handled in composable
  } finally {
    importing.value = false
  }
}

function triggerFileInput() {
  fileInputRef.value?.click()
}

function getTypeBadgeClass(type: string): string {
  switch (type) {
    case 'ssh': return 'badge-ssh'
    case 'k8s': return 'badge-k8s'
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
    default: return []
  }
}

const connectorTypeOptions = computed<SelectOption<ConnectorType>[]>(() => [
  { label: t('sections.nodes.fields.typeOptions.ssh'), value: 'ssh' },
  { label: t('sections.nodes.fields.typeOptions.k8s'), value: 'k8s' },
])
const authTypeOptions = computed(() => getAuthTypeOptions(formConnectorType.value))
const safetyPolicyOptions = computed<SelectOption<SafetyPolicy>[]>(() => [
  { label: t('sections.nodes.fields.safetyOptions.strict'), value: 'strict' },
  { label: t('sections.nodes.fields.safetyOptions.standard'), value: 'standard' },
  { label: t('sections.nodes.fields.safetyOptions.relaxed'), value: 'relaxed' },
])

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
        <div class="header-actions">
          <button class="action-btn action-btn-secondary" @click="downloadTemplate">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M12 3v11m0 0 4-4m-4 4-4-4M5 17v1a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-1"
                stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
            <span>{{ t('sections.nodes.templateBtn') }}</span>
          </button>
          <button class="action-btn action-btn-secondary" :disabled="importing" @click="triggerFileInput">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M12 21V10m0 0 4 4m-4-4-4 4M5 7V6a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v1"
                stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
            <span>{{ importing ? t('sections.nodes.importingBtn') : t('sections.nodes.importBtn') }}</span>
          </button>
          <input ref="fileInputRef" type="file" accept=".csv" style="display:none" @change="handleImportFile" />
          <button class="action-btn action-btn-primary" @click="openForm">{{ t('sections.nodes.addBtn') }}</button>
        </div>
      </div>

      <div v-if="importResult" class="import-banner">
        <p v-if="importResult.imported > 0" class="import-ok">
          {{ t('sections.nodes.importSuccess', { n: String(importResult.imported) }) }}
        </p>
        <p v-if="importResult.skipped > 0" class="import-warn">
          {{ t('sections.nodes.importSkipped', {
            n: String(importResult.skipped),
            aliases: importResult.skippedAliases.join(', ')
          }) }}
        </p>
        <p v-for="(err, i) in importResult.errors" :key="i" class="import-err">
          <template v-if="err.row > 0">
            {{ t('sections.nodes.importError', { row: String(err.row), field: err.field ?? '-', reason: err.reason }) }}
          </template>
          <template v-else>
            {{ t('sections.nodes.importErrorNoRow', { reason: err.reason }) }}
          </template>
        </p>
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
      <h3 class="form-title">{{ isEditing ? t('sections.nodes.editTitle') : t('sections.nodes.formTitle') }}</h3>

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
          <Select v-model="formConnectorType" :options="connectorTypeOptions" />
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
          <Select v-model="formAuthType" :options="authTypeOptions" />
        </div>

        <div class="form-group">
          <label class="form-label">{{ t('sections.nodes.fields.tagsLabel') }}</label>
          <input v-model="formTags" class="form-input" :placeholder="t('sections.nodes.fields.tagsPlaceholder')" />
        </div>

        <div class="form-group">
          <label class="form-label">{{ t('sections.nodes.fields.safetyLabel') }}</label>
          <Select v-model="formSafetyPolicy" :options="safetyPolicyOptions" />
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
          {{
            submitting
              ? (isEditing ? t('sections.nodes.updatingBtn') : t('sections.nodes.registeringBtn'))
              : (isEditing ? t('sections.nodes.updateBtn') : t('sections.nodes.registerBtn'))
          }}
        </button>
      </div>
    </div>

    <!-- Node List -->
    <div v-if="nodes.length > 0" class="node-list">
      <div v-for="node in nodes" :key="node.id" class="node-card">
        <div class="node-header">
          <div class="node-info">
            <span class="node-alias">{{ node.alias }}</span>
            <span v-if="node.authType === null" class="badge-pending">{{ t('sections.nodes.pendingConfig') }}</span>
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
              class="edit-btn"
              @click="openEditForm(node)"
            >
              {{ t('common.edit') }}
            </button>
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
        <div v-if="nodeTestMessages[node.id]" class="node-test-message">
          {{ nodeTestMessages[node.id] }}
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
  gap: 16px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
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

.action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 38px;
  padding: 8px 16px;
  border-radius: var(--radius-md);
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition:
    opacity 0.15s ease,
    background 0.15s ease,
    border-color 0.15s ease,
    color 0.15s ease;
}

.action-btn svg {
  flex: 0 0 auto;
}

.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.action-btn-primary {
  border: none;
  background: var(--color-black);
  color: var(--color-white);
}

.action-btn-primary:hover:not(:disabled) {
  opacity: 0.9;
}

.action-btn-secondary {
  border: var(--border);
  background: var(--color-white);
  color: var(--color-black);
}

.action-btn-secondary:hover:not(:disabled) {
  background: var(--color-gray-bg);
}

.import-banner {
  margin-top: var(--space-3);
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-gray-50);
  border: 1px solid var(--color-gray-200);
  font-size: 13px;
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.import-ok {
  color: #16a34a;
}

.import-warn {
  color: #d97706;
}

.import-err {
  color: #dc2626;
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

.badge-pending {
  display: inline-flex;
  align-items: center;
  padding: 1px 6px;
  border-radius: var(--radius-full);
  background: #fed7aa;
  color: #c2410c;
  font-size: 10px;
  font-weight: 600;
  margin-left: var(--space-1);
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

.edit-btn, .test-btn, .delete-btn, .confirm-delete-btn, .cancel-delete-btn {
  padding: 4px 10px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 12px;
  cursor: pointer;
}

.edit-btn:hover, .test-btn:hover, .delete-btn:hover {
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

.node-test-message {
  margin-top: 6px;
  font-size: 12px;
  color: #b91c1c;
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
