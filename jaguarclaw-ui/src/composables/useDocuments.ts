import { ref, readonly } from 'vue'
import { useWebSocket } from './useWebSocket'
import type { Document, DocumentNode, RpcEvent } from '@/types'

// ── Module-level singleton state ──────────────────────────────────────────────
const tree = ref<DocumentNode[]>([])
const currentDoc = ref<Document | null>(null)
const loading = ref(false)
const saving = ref(false)
const aiStreaming = ref(false)
const aiStreamContent = ref('')
const error = ref<string | null>(null)

let saveTimer: ReturnType<typeof setTimeout> | null = null
let aiUnsubDelta: (() => void) | null = null
let aiUnsubEnd: (() => void) | null = null

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
    selection?: string
  ): Promise<string> {
    aiStreaming.value = true
    aiStreamContent.value = ''
    aiUnsubDelta?.()
    aiUnsubEnd?.()

    const result = await request<{ runId: string }>('document.ai.assist', { docId, action, selection })
    const streamRunId = result.runId

    aiUnsubDelta = onEvent('assistant.delta', (event: RpcEvent) => {
      if (event.runId === streamRunId) {
        if (event.payload && typeof event.payload === 'object' && 'content' in event.payload) {
          aiStreamContent.value += (event.payload as { content: string }).content
        }
      }
    })

    aiUnsubEnd = onEvent('lifecycle.end', (event: RpcEvent) => {
      if (event.runId === streamRunId) {
        aiStreaming.value = false
        aiUnsubDelta?.()
        aiUnsubEnd?.()
      }
    })

    return streamRunId
  }

  function stopAiStream() {
    aiStreaming.value = false
    aiStreamContent.value = ''
    aiUnsubDelta?.()
    aiUnsubEnd?.()
    aiUnsubDelta = null
    aiUnsubEnd = null
  }

  return {
    tree: readonly(tree),
    currentDoc: readonly(currentDoc),
    loading: readonly(loading),
    saving: readonly(saving),
    aiStreaming: readonly(aiStreaming),
    aiStreamContent: readonly(aiStreamContent),
    error: readonly(error),
    loadTree,
    loadDocument,
    createDocument,
    scheduleSave,
    deleteDocument,
    aiAssist,
    stopAiStream,
  }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function updateNodeTitle(nodes: DocumentNode[], id: string, title: string) {
  for (const node of nodes) {
    if (node.id === id) { node.title = title; return }
    if (node.children.length) updateNodeTitle(node.children, id, title)
  }
}
