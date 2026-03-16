import { ref, computed } from 'vue'
import { useWebSocket } from './useWebSocket'
import type {
  ImSettings, ImNode, ImContact, ImConversation, ImMessage, ImPairRequestEvent
} from '@/types'

// Module-level state — shared across all consumers
const settings    = ref<ImSettings | null>(null)
const nodes       = ref<ImNode[]>([])
const contacts    = ref<ImContact[]>([])
const conversations = ref<ImConversation[]>([])
const messages    = ref<Record<string, ImMessage[]>>({})  // conversationId → messages
const pendingPairRequests = ref<ImPairRequestEvent[]>([])
const activeConversationId = ref<string | null>(null)
const loading     = ref(false)
const error       = ref<string | null>(null)

const totalUnreadCount = computed(() =>
  conversations.value.reduce((sum, c) => sum + (c.unreadCount ?? 0), 0)
)

let listenersSetup = false

export function useIm() {
  const { request, onEvent } = useWebSocket()

  // ── Settings ───────────────────────────────────────────────────────────────

  async function loadSettings(): Promise<void> {
    loading.value = true
    error.value = null
    try {
      settings.value = await request<ImSettings>('im.settings.get')
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load IM settings'
    } finally {
      loading.value = false
    }
  }

  async function saveSettings(input: {
    displayName?: string
    redisUrl?: string
    redisPassword?: string
    avatarStyle?: string
    avatarSeed?: string
  }): Promise<void> {
    await request('im.settings.save', input)
    await loadSettings()
  }

  // ── Discovery ──────────────────────────────────────────────────────────────

  async function refreshNodes(): Promise<void> {
    nodes.value = await request<ImNode[]>('im.nodes.list')
  }

  // ── Contacts ───────────────────────────────────────────────────────────────

  async function loadContacts(): Promise<void> {
    contacts.value = await request<ImContact[]>('im.contacts.list')
  }

  async function sendPairRequest(targetNodeId: string): Promise<void> {
    await request('im.pair.request', { targetNodeId })
  }

  async function respondToPairRequest(
    event: ImPairRequestEvent,
    accept: boolean
  ): Promise<void> {
    await request('im.pair.respond', {
      fromNodeId:      event.fromNodeId,
      fromDisplayName: event.fromDisplayName,
      fromPubEd25519:  event.fromPubEd25519,
      fromPubX25519:   event.fromPubX25519,
      fromAvatarStyle: event.fromAvatarStyle,
      fromAvatarSeed:  event.fromAvatarSeed,
      accept,
    })
    pendingPairRequests.value = pendingPairRequests.value
      .filter(r => r.fromNodeId !== event.fromNodeId)
    if (accept) {
      await loadContacts()
      await loadConversations()
    }
  }

  // ── Conversations + Messages ───────────────────────────────────────────────

  async function loadConversations(): Promise<void> {
    conversations.value = await request<ImConversation[]>('im.conversations.list')
  }

  async function loadMessages(conversationId: string): Promise<void> {
    const msgs = await request<ImMessage[]>('im.messages.list', { conversationId })
    messages.value = { ...messages.value, [conversationId]: msgs }
    activeConversationId.value = conversationId
    // Reset unread count locally (backend also resets on im.messages.list)
    conversations.value = conversations.value.map(c =>
      c.id === conversationId ? { ...c, unreadCount: 0 } : c
    )
  }

  async function clearConversation(conversationId: string): Promise<{ deletedMessages: number; deletedFiles: number; freedBytes: number }> {
    const result = await request<{ deletedMessages: number; deletedFiles: number; freedBytes: number }>(
      'im.conversation.clear', { conversationId }
    )
    // Clear local message cache for this conversation
    const { [conversationId]: _, ...rest } = messages.value
    messages.value = rest
    // Reset conversation preview locally
    conversations.value = conversations.value.map(c =>
      c.id === conversationId ? { ...c, lastMsg: '', lastMsgAt: null, unreadCount: 0 } : c
    )
    return result
  }

  async function startChat(targetNodeId: string): Promise<void> {
    await request('im.chat.start', { targetNodeId })
    await loadContacts()
    await loadConversations()
    activeConversationId.value = targetNodeId
  }

  async function sendMessage(toNodeId: string, text: string): Promise<void> {
    const { messageId } = await request<{ messageId: string }>('im.message.send', {
      toNodeId,
      text,
    })
    const sentMsg: ImMessage = {
      id:             messageId,
      conversationId: toNodeId,
      senderNodeId:   settings.value?.nodeId ?? '',
      isMe:           true,
      type:           'TEXT',
      content:        JSON.stringify({ text }),
      createdAt:      new Date().toISOString(),
      status:         'sent',
    }
    messages.value = {
      ...messages.value,
      [toNodeId]: [...(messages.value[toNodeId] ?? []), sentMsg],
    }
    await loadConversations()
  }

  async function sendFile(toNodeId: string, file: File): Promise<void> {
    const formData = new FormData()
    formData.append('toNodeId', toNodeId)
    formData.append('file', file)

    const response = await fetch('/api/im/files/send', {
      method: 'POST',
      body: formData,
    })
    if (!response.ok) {
      const body = await response.json().catch(() => ({}))
      throw new Error(body.error ?? `File send failed: ${response.status}`)
    }
    const { messageId } = await response.json() as { messageId: string }

    const isImage = file.type.startsWith('image/')
    const sentMsg: ImMessage = {
      id:             messageId,
      conversationId: toNodeId,
      senderNodeId:   settings.value?.nodeId ?? '',
      isMe:           true,
      type:           isImage ? 'IMAGE' : 'FILE',
      content:        JSON.stringify({ filename: file.name, mimeType: file.type, size: file.size }),
      createdAt:      new Date().toISOString(),
      status:         'sent',
      fileUrl:        `/api/im/files/${messageId}`,
      fileName:       file.name,
      mimeType:       file.type,
      fileSize:       file.size,
    }
    messages.value = {
      ...messages.value,
      [toNodeId]: [...(messages.value[toNodeId] ?? []), sentMsg],
    }
    await loadConversations()
  }


  function setupListeners() {
    if (listenersSetup) return
    listenersSetup = true

    onEvent('im.pair_request', (event) => {
      const payload = event.payload as ImPairRequestEvent
      pendingPairRequests.value = [...pendingPairRequests.value, payload]
    })

    onEvent('im.pair_accepted', () => {
      loadContacts()
      loadConversations()
    })

    onEvent('im.pair_rejected', (event) => {
      const { nodeId } = event.payload as { nodeId: string }
      contacts.value = contacts.value.filter(c => c.nodeId !== nodeId)
    })

    onEvent('im.profile_updated', (event) => {
      const { nodeId, displayName, avatarStyle, avatarSeed } = event.payload as {
        nodeId: string
        displayName: string
        avatarStyle: string
        avatarSeed: string
      }
      // Update contact
      contacts.value = contacts.value.map(c =>
        c.nodeId === nodeId
          ? { ...c,
              displayName: displayName || c.displayName,
              avatarStyle: avatarStyle || c.avatarStyle,
              avatarSeed,
            }
          : c
      )
      // Update conversation display name
      conversations.value = conversations.value.map(c =>
        c.id === nodeId
          ? { ...c, displayName: displayName || c.displayName }
          : c
      )
    })

    onEvent('im.message', (event) => {
      const msg = event.payload as {
        conversationId: string
        messageId: string
        senderNodeId: string
        displayName: string
        type: string
        content: string
        createdAt: string
      }
      const isFile = msg.type === 'IMAGE' || msg.type === 'FILE'
      let fileName: string | undefined
      let mimeType: string | undefined
      let fileSize: number | undefined
      let fileUrl: string | undefined
      if (isFile) {
        try {
          const parsed = JSON.parse(msg.content)
          fileName = parsed.filename
          mimeType = parsed.mimeType
          fileSize = parsed.size
          fileUrl  = `/api/im/files/${msg.messageId}`
        } catch { /* ignore */ }
      }
      const incoming: ImMessage = {
        id:             msg.messageId,
        conversationId: msg.conversationId,
        senderNodeId:   msg.senderNodeId,
        isMe:           false,
        type:           msg.type as ImMessage['type'],
        content:        msg.content,
        createdAt:      msg.createdAt,
        status:         'delivered',
        fileUrl,
        fileName,
        mimeType,
        fileSize,
      }
      const conv = msg.conversationId
      messages.value = {
        ...messages.value,
        [conv]: [...(messages.value[conv] ?? []), incoming],
      }
      // Increment unread if this conversation isn't currently open
      if (conv !== activeConversationId.value) {
        conversations.value = conversations.value.map(c =>
          c.id === conv ? { ...c, unreadCount: (c.unreadCount ?? 0) + 1 } : c
        )
      }
      loadConversations()
    })
  }

  setupListeners()

  return {
    settings,
    nodes,
    contacts,
    conversations,
    messages,
    pendingPairRequests,
    activeConversationId,
    totalUnreadCount,
    loading,
    error,
    loadSettings,
    saveSettings,
    refreshNodes,
    loadContacts,
    sendPairRequest,
    respondToPairRequest,
    loadConversations,
    loadMessages,
    startChat,
    clearConversation,
    sendMessage,
    sendFile,
  }
}
