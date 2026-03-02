import { ref } from 'vue'
import { useWebSocket } from './useWebSocket'

export interface PersonaFiles {
  soul: string
  rule: string
  profile: string
}

const { request, onEvent } = useWebSocket()

export function useSoulConfig() {
  const persona = ref<PersonaFiles | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchPersona(agentId?: string) {
    loading.value = true
    error.value = null
    try {
      const payload = agentId ? { agentId } : undefined
      const result = await request<PersonaFiles>('soul.get', payload)
      persona.value = result
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Unknown error'
      console.error('Failed to fetch persona:', e)
    } finally {
      loading.value = false
    }
  }

  async function saveFile(agentId: string, file: 'soul' | 'rule' | 'profile', content: string) {
    loading.value = true
    error.value = null
    try {
      await request('soul.save', { agentId, file, content })
      if (persona.value) {
        persona.value = { ...persona.value, [file]: content }
      }
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Unknown error'
      console.error('Failed to save persona file:', e)
      throw e
    } finally {
      loading.value = false
    }
  }

  // Refresh persona when AI uses update_soul / update_rule / update_profile tools
  function watchPersonaUpdates(agentId?: string, onUpdated?: () => void): () => void {
    const pendingCallIds = new Set<string>()
    const watchedTools = new Set(['update_soul', 'update_rule', 'update_profile'])

    const offCall = onEvent('tool.call', (event) => {
      const payload = event.payload
      if (payload && typeof payload === 'object' && 'toolName' in payload && 'callId' in payload) {
        const toolName = String((payload as { toolName: unknown }).toolName)
        const callId = String((payload as { callId: unknown }).callId)
        if (watchedTools.has(toolName)) {
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
              fetchPersona(agentId)
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
    persona,
    loading,
    error,
    fetchPersona,
    saveFile,
    watchPersonaUpdates,
  }
}
