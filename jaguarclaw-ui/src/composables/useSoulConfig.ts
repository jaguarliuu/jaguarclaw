import { ref } from 'vue'
import { useWebSocket } from './useWebSocket'

export interface SoulConfig {
  id?: number
  agentName: string
  personality: string
  traits: string[]
  responseStyle: string
  detailLevel: string
  expertise: string[]
  forbiddenTopics: string[]
  customPrompt: string
  enabled: boolean
}

const { request, onEvent } = useWebSocket()

export function useSoulConfig() {
  const config = ref<SoulConfig | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchConfig(agentId?: string) {
    loading.value = true
    error.value = null
    try {
      const payload = agentId ? { agentId } : undefined
      const result = await request<SoulConfig>('soul.get', payload)
      config.value = result
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Unknown error'
      console.error('Failed to fetch soul config:', e)
    } finally {
      loading.value = false
    }
  }

  async function saveConfig(newConfig: Partial<SoulConfig>, agentId?: string) {
    loading.value = true
    error.value = null
    try {
      await request('soul.save', {
        agentId,
        config: newConfig,
      })
      await fetchConfig(agentId)
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Unknown error'
      console.error('Failed to save soul config:', e)
      throw e
    } finally {
      loading.value = false
    }
  }

  // Refresh config when AI uses update_soul tool
  function watchSoulUpdates(onUpdated?: () => void): () => void {
    const pendingCallIds = new Set<string>()

    const offCall = onEvent('tool.call', (event) => {
      const payload = event.payload
      if (payload && typeof payload === 'object' && 'toolName' in payload && 'callId' in payload) {
        const toolName = String((payload as { toolName: unknown }).toolName)
        const callId = String((payload as { callId: unknown }).callId)
        if (toolName === 'update_soul') {
          pendingCallIds.add(callId)
        }
      }
    })

    const offResult = onEvent('tool.result', (event) => {
      const payload = event.payload
      if (payload && typeof payload === 'object' && 'callId' in payload) {
        const callId = String((payload as { callId: unknown }).callId)
        const success = Boolean((payload as { success?: unknown }).success)
        if (pendingCallIds.has(callId)) {
          pendingCallIds.delete(callId)
          if (success) {
            if (onUpdated) {
              onUpdated()
            } else {
              fetchConfig()
            }
          }
        }
      }
    })

    return () => {
      offCall()
      offResult()
    }
  }

  return {
    config,
    loading,
    error,
    fetchConfig,
    saveConfig,
    watchSoulUpdates
  }
}
