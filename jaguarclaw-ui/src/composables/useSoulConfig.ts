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

  async function fetchConfig() {
    loading.value = true
    error.value = null
    try {
      const result = await request<SoulConfig>('soul.get')
      config.value = result
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Unknown error'
      console.error('Failed to fetch soul config:', e)
    } finally {
      loading.value = false
    }
  }

  async function saveConfig(newConfig: Partial<SoulConfig>) {
    loading.value = true
    error.value = null
    try {
      await request('soul.save', newConfig)
      await fetchConfig()
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Unknown error'
      console.error('Failed to save soul config:', e)
      throw e
    } finally {
      loading.value = false
    }
  }

  // Refresh config when AI uses update_soul tool
  function watchSoulUpdates(): () => void {
    const pendingCallIds = new Set<string>()

    const offCall = onEvent('tool.call', (event) => {
      if (event.payload.toolName === 'update_soul') {
        pendingCallIds.add(event.payload.callId)
      }
    })

    const offResult = onEvent('tool.result', (event) => {
      if (pendingCallIds.has(event.payload.callId)) {
        pendingCallIds.delete(event.payload.callId)
        if (event.payload.success) {
          fetchConfig()
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
