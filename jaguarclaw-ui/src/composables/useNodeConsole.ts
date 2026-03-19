import { ref, readonly } from 'vue'
import { useWebSocket } from './useWebSocket'
import { useToast } from './useToast'
import type { NodeInfo, NodeRegisterPayload, NodeTestResult, NodeImportResult } from '@/types'

const nodes = ref<NodeInfo[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

export function useNodeConsole() {
  const { request } = useWebSocket()
  const { showToast } = useToast()

  async function loadNodes() {
    loading.value = true
    error.value = null
    try {
      const result = await request<NodeInfo[]>('nodes.list')
      nodes.value = result
    } catch (e) {
      console.error('[NodeConsole] Failed to load nodes:', e)
      error.value = e instanceof Error ? e.message : 'Failed to load nodes'
      nodes.value = []
    } finally {
      loading.value = false
    }
  }

  async function registerNode(payload: NodeRegisterPayload): Promise<NodeInfo> {
    error.value = null
    try {
      const result = await request<NodeInfo>('nodes.register', payload)
      await loadNodes()
      return result
    } catch (e) {
      console.error('[NodeConsole] Failed to register node:', e)
      error.value = e instanceof Error ? e.message : 'Failed to register node'
      throw e
    }
  }

  async function updateNode(id: string, payload: Partial<NodeRegisterPayload>): Promise<NodeInfo> {
    error.value = null
    try {
      const result = await request<NodeInfo>('nodes.update', { id, ...payload })
      await loadNodes()
      return result
    } catch (e) {
      console.error('[NodeConsole] Failed to update node:', e)
      error.value = e instanceof Error ? e.message : 'Failed to update node'
      throw e
    }
  }

  async function removeNode(id: string): Promise<void> {
    error.value = null
    try {
      await request<{ success: boolean }>('nodes.remove', { id })
      await loadNodes()
    } catch (e) {
      console.error('[NodeConsole] Failed to remove node:', e)
      error.value = e instanceof Error ? e.message : 'Failed to remove node'
      throw e
    }
  }

  async function testNode(id: string): Promise<NodeTestResult> {
    error.value = null
    try {
      const result = await request<NodeTestResult>('nodes.test', { id })
      await loadNodes()
      if (!result.success) {
        error.value = result.message || 'Connection test failed'
      }
      return result
    } catch (e) {
      console.error('[NodeConsole] Failed to test node:', e)
      error.value = e instanceof Error ? e.message : 'Failed to test node'
      throw e
    }
  }

  async function importNodes(csvContent: string): Promise<NodeImportResult> {
    error.value = null
    try {
      const result = await request<NodeImportResult>('nodes.import', { csv: csvContent })
      if (result.imported > 0) {
        await loadNodes()
      }
      return result
    } catch (e) {
      console.error('[NodeConsole] Failed to import nodes:', e)
      error.value = e instanceof Error ? e.message : 'Failed to import nodes'
      throw e
    }
  }

  async function downloadTemplate(): Promise<void> {
    try {
      const result = await request<{ csv: string }>('nodes.import.template')
      const blob = new Blob([result.csv], { type: 'text/csv;charset=utf-8;' })
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = 'nodes-template.csv'
      anchor.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      console.error('[NodeConsole] Failed to download template:', e)
      showToast({ type: 'error', message: 'Failed to download template' })
    }
  }

  return {
    nodes: readonly(nodes),
    loading: readonly(loading),
    error: readonly(error),
    loadNodes,
    registerNode,
    updateNode,
    removeNode,
    testNode,
    importNodes,
    downloadTemplate
  }
}
