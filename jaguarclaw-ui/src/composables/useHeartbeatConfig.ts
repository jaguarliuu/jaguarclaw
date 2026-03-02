import { ref } from 'vue'
import { useWebSocket } from './useWebSocket'

export interface HeartbeatConfig {
  enabled: boolean
  intervalMinutes: number
  activeHoursStart: string
  activeHoursEnd: string
  timezone: string
  ackMaxChars: number
}

const { request } = useWebSocket()

export function useHeartbeatConfig(agentId: string = 'main') {
  const config = ref<HeartbeatConfig | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchConfig() {
    loading.value = true
    error.value = null
    try {
      const result = await request<HeartbeatConfig>('heartbeat.config.get', { agentId })
      config.value = result
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Unknown error'
      console.error('Failed to fetch heartbeat config:', e)
    } finally {
      loading.value = false
    }
  }

  async function saveConfig(newConfig: Partial<HeartbeatConfig>) {
    loading.value = true
    error.value = null
    try {
      await request('heartbeat.config.save', { agentId, config: newConfig })
      await fetchConfig()
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Unknown error'
      console.error('Failed to save heartbeat config:', e)
      throw e
    } finally {
      loading.value = false
    }
  }

  return {
    config,
    loading,
    error,
    fetchConfig,
    saveConfig
  }
}
