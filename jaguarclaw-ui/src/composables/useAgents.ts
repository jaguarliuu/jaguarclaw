import { computed, ref } from 'vue'
import { useWebSocket } from './useWebSocket'
import type { AgentProfile } from '@/types'

const FALLBACK_AGENT_ID = 'main'

export interface AgentProfilePayload {
  name: string
  displayName?: string
  description?: string
  workspacePath?: string
  model?: string
  enabled?: boolean
  isDefault?: boolean
  allowedTools?: string[]
  excludedTools?: string[]
  heartbeatInterval?: number
  heartbeatActiveHours?: string
  dailyTokenLimit?: number
  monthlyCostLimit?: number
}

// Shared state across composable consumers
const agents = ref<AgentProfile[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)

export function useAgents() {
  const { request } = useWebSocket()

  const enabledAgents = computed(() => agents.value.filter((agent) => agent.enabled))

  const defaultAgent = computed<AgentProfile | null>(() => {
    const preferred =
      enabledAgents.value.find((agent) => agent.isDefault) ??
      enabledAgents.value[0] ??
      agents.value.find((agent) => agent.isDefault) ??
      agents.value[0]
    return preferred ?? null
  })

  function resolveAgentId(agentId?: string | null): string {
    if (agentId && agentId.trim().length > 0) {
      return agentId
    }
    return defaultAgent.value?.id ?? FALLBACK_AGENT_ID
  }

  function findAgent(agentId?: string | null): AgentProfile | undefined {
    if (!agentId) return undefined
    return agents.value.find((agent) => agent.id === agentId)
  }

  async function loadAgents() {
    loading.value = true
    error.value = null

    try {
      const result = await request<{ agents: AgentProfile[] }>('agent.list')
      agents.value = result.agents || []
    } catch (err: any) {
      error.value = err?.message || 'Failed to load agents'
      console.error('Failed to load agents:', err)
    } finally {
      loading.value = false
    }
  }

  async function getAgent(agentId: string) {
    try {
      return await request<AgentProfile>('agent.get', { agentId })
    } catch (err: any) {
      error.value = err?.message || 'Failed to get agent'
      console.error('Failed to get agent:', err)
      return null
    }
  }

  async function createAgent(payload: AgentProfilePayload) {
    saving.value = true
    error.value = null
    try {
      const created = await request<AgentProfile>('agent.create', payload)
      await loadAgents()
      return created
    } catch (err: any) {
      error.value = err?.message || 'Failed to create agent'
      console.error('Failed to create agent:', err)
      return null
    } finally {
      saving.value = false
    }
  }

  async function updateAgent(agentId: string, payload: Partial<AgentProfilePayload>) {
    saving.value = true
    error.value = null
    try {
      const updated = await request<AgentProfile>('agent.update', {
        agentId,
        ...payload,
      })
      await loadAgents()
      return updated
    } catch (err: any) {
      error.value = err?.message || 'Failed to update agent'
      console.error('Failed to update agent:', err)
      return null
    } finally {
      saving.value = false
    }
  }

  async function deleteAgent(agentId: string) {
    saving.value = true
    error.value = null
    try {
      await request<{ deleted: boolean }>('agent.delete', { agentId })
      await loadAgents()
      return true
    } catch (err: any) {
      error.value = err?.message || 'Failed to delete agent'
      console.error('Failed to delete agent:', err)
      return false
    } finally {
      saving.value = false
    }
  }

  return {
    agents,
    enabledAgents,
    defaultAgent,
    loading,
    saving,
    error,
    loadAgents,
    getAgent,
    createAgent,
    updateAgent,
    deleteAgent,
    resolveAgentId,
    findAgent,
  }
}
