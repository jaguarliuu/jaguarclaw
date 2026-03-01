<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useMcpServers, type McpServer } from '@/composables/useMcpServers'
import { useAgents } from '@/composables/useAgents'
import { useI18n } from '@/i18n'
import McpServerModal from './McpServerModal.vue'

const { t } = useI18n()
const { servers, loading, loadServers, deleteServer } = useMcpServers()
const { agents, enabledAgents, defaultAgent, loadAgents } = useAgents()

const showModal = ref(false)
const modalMode = ref<'create' | 'edit'>('create')
const selectedServer = ref<McpServer | null>(null)

const filterScope = ref<'all' | 'global' | 'agent'>('all')
const filterAgentId = ref<string>('')
const createScope = ref<'GLOBAL' | 'AGENT'>('GLOBAL')
const createAgentId = ref<string>('')

const agentOptions = computed(() => enabledAgents.value.length > 0 ? enabledAgents.value : agents.value)

const filteredServers = computed(() => {
  return servers.value.filter((server) => {
    const normalizedScope = server.scope?.toString().toUpperCase() === 'AGENT' ? 'AGENT' : 'GLOBAL'
    if (filterScope.value === 'global' && normalizedScope !== 'GLOBAL') return false
    if (filterScope.value === 'agent') {
      if (normalizedScope !== 'AGENT') return false
      if (filterAgentId.value) return server.agentId === filterAgentId.value
    }
    return true
  })
})

onMounted(async () => {
  await Promise.all([loadServers(), loadAgents()])
  const fallbackAgent = defaultAgent.value?.id || agentOptions.value[0]?.id || ''
  filterAgentId.value = fallbackAgent
  createAgentId.value = fallbackAgent
})

function openCreateModal() {
  modalMode.value = 'create'
  selectedServer.value = null
  if (filterScope.value === 'agent') {
    createScope.value = 'AGENT'
    createAgentId.value = filterAgentId.value || defaultAgent.value?.id || agentOptions.value[0]?.id || ''
  } else {
    createScope.value = 'GLOBAL'
    createAgentId.value = ''
  }
  showModal.value = true
}

function openEditModal(server: McpServer) {
  modalMode.value = 'edit'
  selectedServer.value = server
  showModal.value = true
}

function closeModal() {
  showModal.value = false
  selectedServer.value = null
}

async function handleSuccess() {
  await loadServers()
  closeModal()
}

async function handleDelete(server: McpServer) {
  if (!confirm(t('sections.mcp.confirmDelete', { name: server.name }))) {
    return
  }

  await deleteServer(server.id)
  await loadServers()
}

function getStatusClass(server: McpServer) {
  return server.enabled ? 'status-connected' : 'status-disconnected'
}

function getTransportBadgeClass(type: string) {
  return `badge-${type.toLowerCase()}`
}

function getScopeLabel(server: McpServer) {
  return server.scope?.toString().toUpperCase() === 'AGENT'
    ? t('sections.mcp.scope.agent')
    : t('sections.mcp.scope.global')
}

function getAgentName(agentId?: string) {
  if (!agentId) return '-'
  const agent = agentOptions.value.find((item) => item.id === agentId)
  return agent?.displayName || agent?.name || agentId
}
</script>

<template>
  <div class="mcp-section">
    <div class="section-header">
      <div>
        <h2 class="section-title">{{ t('settings.nav.mcp') }}</h2>
        <p class="section-description">{{ t('sections.mcp.subtitle') }}</p>
      </div>
      <button class="btn-primary" @click="openCreateModal">
        {{ t('sections.mcp.addBtn') }}
      </button>
    </div>

    <div class="scope-toolbar">
      <label class="scope-field">
        <span class="scope-label">{{ t('sections.mcp.filters.scope') }}</span>
        <select v-model="filterScope" class="scope-select">
          <option value="all">{{ t('sections.mcp.filters.scopeAll') }}</option>
          <option value="global">{{ t('sections.mcp.scope.global') }}</option>
          <option value="agent">{{ t('sections.mcp.scope.agent') }}</option>
        </select>
      </label>

      <label class="scope-field">
        <span class="scope-label">{{ t('sections.mcp.filters.agent') }}</span>
        <select v-model="filterAgentId" class="scope-select" :disabled="filterScope !== 'agent'">
          <option value="">{{ t('sections.mcp.filters.anyAgent') }}</option>
          <option v-for="agent in agentOptions" :key="agent.id" :value="agent.id">
            {{ agent.displayName || agent.name }}
          </option>
        </select>
      </label>
    </div>

    <div v-if="loading" class="loading-state">
      <div class="spinner"></div>
      <p>{{ t('sections.mcp.loading') }}</p>
    </div>

    <div v-else-if="servers.length === 0" class="empty-state">
      <div class="empty-icon">🔌</div>
      <h3>{{ t('sections.mcp.empty') }}</h3>
      <p>{{ t('sections.mcp.emptyHint') }}</p>
      <button class="btn-primary" @click="openCreateModal">
        {{ t('sections.mcp.addFirstBtn') }}
      </button>
    </div>

    <div v-else-if="filteredServers.length === 0" class="empty-state">
      <h3>{{ t('sections.mcp.filteredEmpty') }}</h3>
      <p>{{ t('sections.mcp.filteredEmptyHint') }}</p>
    </div>

    <div v-else class="servers-table-container">
      <table class="servers-table">
        <thead>
          <tr>
            <th>{{ t('sections.mcp.table.name') }}</th>
            <th>{{ t('sections.mcp.table.type') }}</th>
            <th>{{ t('sections.mcp.table.scope') }}</th>
            <th>{{ t('sections.mcp.table.agent') }}</th>
            <th>{{ t('sections.mcp.table.status') }}</th>
            <th>{{ t('sections.mcp.table.tools') }}</th>
            <th>{{ t('sections.mcp.table.actions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="server in filteredServers" :key="server.id">
            <td>
              <div class="server-name">
                {{ server.name }}
                <span v-if="server.toolPrefix" class="tool-prefix">{{ server.toolPrefix }}</span>
              </div>
            </td>
            <td>
              <span class="badge" :class="getTransportBadgeClass(server.transportType)">
                {{ server.transportType }}
              </span>
            </td>
            <td>
              <span class="scope-badge" :class="{ agent: server.scope?.toString().toUpperCase() === 'AGENT' }">
                {{ getScopeLabel(server) }}
              </span>
            </td>
            <td class="agent-cell">{{ getAgentName(server.agentId) }}</td>
            <td>
              <span class="status" :class="getStatusClass(server)">
                {{ server.enabled ? t('sections.mcp.connected') : t('sections.mcp.disconnected') }}
              </span>
            </td>
            <td class="tools-count">
              {{ t('sections.mcp.toolCount', { n: String(server.toolCount || 0) }) }}
            </td>
            <td>
              <div class="actions">
                <button class="btn-icon" @click="openEditModal(server)" :title="t('common.edit')">
                  {{ t('common.edit') }}
                </button>
                <button class="btn-icon btn-danger" @click="handleDelete(server)" :title="t('common.delete')">
                  {{ t('common.delete') }}
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <McpServerModal
      v-if="showModal"
      :mode="modalMode"
      :server="selectedServer"
      :default-scope="createScope"
      :default-agent-id="createAgentId"
      @close="closeModal"
      @success="handleSuccess"
    />
  </div>
</template>

<style scoped>
.mcp-section {
  padding: 32px;
  height: 100%;
  overflow-y: auto;
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20px;
}

.section-title {
  font-size: 24px;
  font-weight: 600;
  margin: 0 0 8px 0;
}

.section-description {
  color: var(--color-gray-500);
  font-size: 14px;
  margin: 0;
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
  transform: translateY(-1px);
}

.scope-toolbar {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 16px;
  padding: 10px 12px;
  border: var(--border-light);
  border-radius: var(--radius-md);
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
  min-width: 140px;
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

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  color: var(--color-gray-500);
}

.spinner {
  width: 24px;
  height: 24px;
  border: 2px solid var(--color-gray-200);
  border-top-color: var(--color-black);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 12px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.empty-state {
  text-align: center;
  padding: 80px 20px;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-state h3 {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 8px 0;
}

.empty-state p {
  color: var(--color-gray-500);
  font-size: 14px;
  margin: 0 0 24px 0;
}

.servers-table-container {
  background: var(--color-white);
  border: var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.servers-table {
  width: 100%;
  border-collapse: collapse;
}

.servers-table thead {
  background: var(--color-gray-50);
  border-bottom: var(--border);
}

.servers-table th {
  padding: 12px 16px;
  text-align: left;
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-gray-600);
}

.servers-table td {
  padding: 16px;
  border-bottom: var(--border-light);
}

.servers-table tbody tr:last-child td {
  border-bottom: none;
}

.servers-table tbody tr:hover {
  background: var(--color-gray-50);
}

.server-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
}

.tool-prefix {
  font-family: var(--font-mono);
  font-size: 11px;
  padding: 2px 6px;
  background: var(--color-gray-100);
  border-radius: var(--radius-sm);
  color: var(--color-gray-600);
}

.badge {
  display: inline-block;
  padding: 4px 8px;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  border-radius: var(--radius-sm);
  letter-spacing: 0.05em;
}

.badge-stdio {
  background: var(--color-blue-50);
  color: var(--color-blue-600);
}

.badge-sse {
  background: var(--color-green-50);
  color: var(--color-green-600);
}

.badge-http {
  background: var(--color-purple-50);
  color: var(--color-purple-600);
}

.scope-badge {
  display: inline-flex;
  align-items: center;
  padding: 3px 8px;
  border-radius: var(--radius-full);
  font-size: 11px;
  background: var(--color-gray-100);
  color: var(--color-gray-700);
}

.scope-badge.agent {
  background: var(--color-green-50);
  color: var(--color-green-700);
}

.agent-cell {
  color: var(--color-gray-600);
  font-size: 13px;
}

.status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.status::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.status-connected {
  color: var(--color-green-600);
}

.status-connected::before {
  background: var(--color-green-500);
}

.status-disconnected {
  color: var(--color-gray-400);
}

.status-disconnected::before {
  background: var(--color-gray-300);
}

.tools-count {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-gray-600);
}

.actions {
  display: flex;
  gap: 8px;
}

.btn-icon {
  padding: 6px 12px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-size: 12px;
  cursor: pointer;
  transition: all var(--duration-fast);
}

.btn-icon:hover {
  background: var(--color-gray-50);
}

.btn-danger {
  color: var(--color-red-600);
}

@media (max-width: 960px) {
  .mcp-section {
    padding: 20px;
  }

  .scope-toolbar {
    flex-wrap: wrap;
  }

  .servers-table-container {
    overflow-x: auto;
  }

  .servers-table {
    min-width: 860px;
  }
}
</style>
