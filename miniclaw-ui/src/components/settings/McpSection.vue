<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useMcpServers } from '@/composables/useMcpServers'
import { useI18n } from '@/i18n'
import McpServerModal from './McpServerModal.vue'

const { t } = useI18n()
const { servers, loading, loadServers, deleteServer } = useMcpServers()

const showModal = ref(false)
const modalMode = ref<'create' | 'edit'>('create')
const selectedServer = ref<any>(null)

onMounted(() => {
  loadServers()
})

function openCreateModal() {
  modalMode.value = 'create'
  selectedServer.value = null
  showModal.value = true
}

function openEditModal(server: any) {
  modalMode.value = 'edit'
  selectedServer.value = server
  showModal.value = true
}

function closeModal() {
  showModal.value = false
  selectedServer.value = null
}

function handleSuccess() {
  loadServers()
  closeModal()
}

async function handleDelete(server: any) {
  if (!confirm(t('sections.mcp.confirmDelete', { name: server.name }))) {
    return
  }

  await deleteServer(server.id)
  loadServers()
}

function getStatusClass(server: any) {
  return server.enabled ? 'status-connected' : 'status-disconnected'
}

function getTransportBadgeClass(type: string) {
  return `badge-${type.toLowerCase()}`
}
</script>

<template>
  <div class="mcp-section">
    <div class="section-header">
      <div>
        <h2 class="section-title">{{ t('settings.nav.mcp') }}</h2>
        <p class="section-description">
          {{ t('sections.mcp.subtitle') }}
        </p>
      </div>
      <button class="btn-primary" @click="openCreateModal">
        {{ t('sections.mcp.addBtn') }}
      </button>
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

    <div v-else class="servers-table-container">
      <table class="servers-table">
        <thead>
          <tr>
            <th>{{ t('sections.mcp.table.name') }}</th>
            <th>{{ t('sections.mcp.table.type') }}</th>
            <th>{{ t('sections.mcp.table.status') }}</th>
            <th>{{ t('sections.mcp.table.tools') }}</th>
            <th>{{ t('sections.mcp.table.actions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="server in servers" :key="server.id">
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
              <span class="status" :class="getStatusClass(server)">
                {{ server.enabled ? t('sections.mcp.connected') : t('sections.mcp.disconnected') }}
              </span>
            </td>
            <td class="tools-count">
              {{ t('sections.mcp.toolCount', { n: server.toolCount || 0 }) }}
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

    <!-- Modal -->
    <McpServerModal
      v-if="showModal"
      :mode="modalMode"
      :server="selectedServer"
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
  margin-bottom: 32px;
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
  color: var(--color-gray-600);
  font-size: 13px;
}

.actions {
  display: flex;
  gap: 8px;
}

.btn-icon {
  padding: 6px 10px;
  background: transparent;
  border: var(--border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--duration-fast);
  font-family: var(--font-mono);
  font-size: 12px;
}

.btn-icon:hover {
  background: var(--color-gray-100);
  border-color: var(--color-gray-300);
}

.btn-icon.btn-danger:hover {
  background: var(--color-red-50);
  border-color: var(--color-red-200);
  color: var(--color-red-600);
}
</style>
