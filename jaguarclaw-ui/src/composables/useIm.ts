import { ref } from 'vue'
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
      accept,
    })
    pendingPairRequests.value = pendingPairRequests.value
      .filter(r => r.fromNodeId !== event.fromNodeId)
    if (accept) await loadContacts()
  }

  // ── Conversations + Messages ───────────────────────────────────────────────

  async function loadConversations(): Promise<void> {
    conversations.value = await request<ImConversation[]>('im.conversations.list')
  }

  async function loadMessages(conversationId: string): Promise<void> {
    const msgs = await request<ImMessage[]>('im.messages.list', { conversationId })
    messages.value = { ...messages.value, [conversationId]: msgs }
    activeConversationId.value = conversationId
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

  // ── Event Listeners (set up once) ─────────────────────────────────────────

  function setupListeners() {
    if (listenersSetup) return
    listenersSetup = true

    onEvent('im.pair_request', (event) => {
      const payload = event.payload as ImPairRequestEvent
      pendingPairRequests.value = [...pendingPairRequests.value, payload]
    })

    onEvent('im.pair_accepted', () => {
      loadContacts()
    })

    onEvent('im.pair_rejected', (event) => {
      const { nodeId } = event.payload as { nodeId: string }
      contacts.value = contacts.value.filter(c => c.nodeId !== nodeId)
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
      const incoming: ImMessage = {
        id:             msg.messageId,
        conversationId: msg.conversationId,
        senderNodeId:   msg.senderNodeId,
        isMe:           false,
        type:           msg.type as ImMessage['type'],
        content:        msg.content,
        createdAt:      msg.createdAt,
        status:         'delivered',
      }
      const conv = msg.conversationId
      messages.value = {
        ...messages.value,
        [conv]: [...(messages.value[conv] ?? []), incoming],
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
    sendMessage,
  }
}
