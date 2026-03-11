import { ref, readonly } from 'vue'
import { useWebSocket } from './useWebSocket'
import type { Document, DocumentNode, RpcEvent } from '@/types'

// ── Module-level singleton state ──────────────────────────────────────────────
const tree = ref<DocumentNode[]>([])
const currentDoc = ref<Document | null>(null)
const loading = ref(false)
const saving = ref(false)
const aiStreaming = ref(false)
const aiStreamContent = ref('')   // assistant.delta text (for status bar)
const docInsertContent = ref('')  // doc.content.insert text (for streaming block)
const aiStatusText = ref('')
const error = ref<string | null>(null)

let saveTimer: ReturnType<typeof setTimeout> | null = null
let aiUnsubDelta: (() => void) | null = null
let aiUnsubEnd: (() => void) | null = null
let aiUnsubError: (() => void) | null = null
let aiUnsubInsert: (() => void) | null = null

export function useDocuments() {
  const { request, onEvent } = useWebSocket()

  // ── Tree ────────────────────────────────────────────────────────────────────

  async function loadTree() {
    loading.value = true
    error.value = null
    try {
      const result = await request<{ documents: DocumentNode[] }>('document.list')
      tree.value = result.documents
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load documents'
    } finally {
      loading.value = false
    }
  }

  // ── CRUD ────────────────────────────────────────────────────────────────────

  async function loadDocument(id: string) {
    loading.value = true
    error.value = null
    try {
      const doc = await request<Document>('document.get', { id })
      currentDoc.value = doc
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load document'
    } finally {
      loading.value = false
    }
  }

  async function createDocument(title?: string, parentId?: string): Promise<Document> {
    const doc = await request<Document>('document.create', { title: title ?? 'Untitled', parentId })
    await loadTree()
    return doc
  }

  function scheduleSave(id: string, title: string, content: string, wordCount: number) {
    if (saveTimer) clearTimeout(saveTimer)
    saving.value = true
    saveTimer = setTimeout(async () => {
      try {
        await request('document.update', { id, title, content, wordCount })
        updateNodeTitle(tree.value, id, title)
      } catch (e) {
        error.value = e instanceof Error ? e.message : 'Save failed'
      } finally {
        saving.value = false
      }
    }, 1500)
  }

  async function deleteDocument(id: string) {
    await request('document.delete', { id })
    if (currentDoc.value?.id === id) currentDoc.value = null
    await loadTree()
  }

  // ── AI assist ───────────────────────────────────────────────────────────────

  async function aiAssist(
    docId: string,
    action: 'continue' | 'optimize' | 'rewrite' | 'summarize' | 'translate',
    selection?: string,
    onChunk?: (chunk: string) => void,
    userPrompt?: string,
    onDocInsert?: (fullContent: string) => void
  ): Promise<string> {
    aiStreaming.value = true
    aiStreamContent.value = ''
    docInsertContent.value = ''
    aiStatusText.value = ''
    aiUnsubDelta?.()
    aiUnsubEnd?.()
    aiUnsubError?.()
    aiUnsubInsert?.()

    const result = await request<{ streamRunId: string }>('document.ai.assist', { docId, action, selection, userPrompt })
    const streamRunId = result.streamRunId

    aiUnsubDelta = onEvent('assistant.delta', (event: RpcEvent) => {
      if (event.runId === streamRunId) {
        if (event.payload && typeof event.payload === 'object' && 'content' in event.payload) {
          const chunk = (event.payload as { content: string }).content
          aiStreamContent.value += chunk
          onChunk?.(chunk)
          aiStatusText.value = chunk.slice(0, 80)
        }
      }
    })

    aiUnsubInsert = onEvent('doc.content.insert', (event: RpcEvent) => {
      if (event.runId === streamRunId) {
        if (event.payload && typeof event.payload === 'object' && 'content' in event.payload) {
          const chunk = (event.payload as { content: string }).content
          if (chunk) {
            docInsertContent.value += chunk
            onDocInsert?.(docInsertContent.value)
          }
        }
      }
    })

    aiUnsubEnd = onEvent('lifecycle.end', (event: RpcEvent) => {
      if (event.runId === streamRunId) {
        aiStreaming.value = false
        aiUnsubDelta?.(); aiUnsubEnd?.(); aiUnsubError?.(); aiUnsubInsert?.()
        aiUnsubDelta = null; aiUnsubEnd = null; aiUnsubError = null; aiUnsubInsert = null
      }
    })

    aiUnsubError = onEvent('lifecycle.error', (event: RpcEvent) => {
      if (event.runId === streamRunId) {
        aiStreaming.value = false
        aiStatusText.value = ''
        aiUnsubDelta?.(); aiUnsubEnd?.(); aiUnsubError?.(); aiUnsubInsert?.()
        aiUnsubDelta = null; aiUnsubEnd = null; aiUnsubError = null; aiUnsubInsert = null
      }
    })

    return streamRunId
  }

  function stopAiStream() {
    aiStreaming.value = false
    aiStreamContent.value = ''
    docInsertContent.value = ''
    aiStatusText.value = ''
    aiUnsubDelta?.(); aiUnsubEnd?.(); aiUnsubError?.(); aiUnsubInsert?.()
    aiUnsubDelta = null; aiUnsubEnd = null; aiUnsubError = null; aiUnsubInsert = null
  }

  async function getConfig(): Promise<string> {
    const result = await request<{ systemPrompt: string }>('document.config.get', {})
    return result.systemPrompt
  }

  async function setConfig(systemPrompt: string): Promise<void> {
    await request('document.config.set', { systemPrompt })
  }

  return {
    tree: readonly(tree),
    currentDoc: readonly(currentDoc),
    loading: readonly(loading),
    saving: readonly(saving),
    aiStreaming: readonly(aiStreaming),
    aiStreamContent: readonly(aiStreamContent),
    docInsertContent: readonly(docInsertContent),
    aiStatusText: readonly(aiStatusText),
    error: readonly(error),
    loadTree,
    loadDocument,
    createDocument,
    scheduleSave,
    deleteDocument,
    aiAssist,
    stopAiStream,
    getConfig,
    setConfig,
  }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function updateNodeTitle(nodes: DocumentNode[], id: string, title: string) {
  for (const node of nodes) {
    if (node.id === id) { node.title = title; return }
    if (node.children.length) updateNodeTitle(node.children, id, title)
  }
}
