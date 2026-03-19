import { ref, readonly } from 'vue'
import type { ConnectionState, RpcRequest, RpcResponse, RpcEvent, AgentEventType } from '@/types'

const WS_PROTOCOL = typeof window !== 'undefined' && window.location.protocol === 'https:' ? 'wss' : 'ws'
const WS_URL = `${WS_PROTOCOL}://${window.location.host}/ws`
const DEBUG_WS = import.meta.env.DEV

const PUBLIC_METHODS = new Set([
  'ping',
  'auth.local.bootstrap',
  'auth.refresh'
])

const AUTH_STORAGE_KEY = 'jaguarclaw.ws.auth.v1'
const DEVICE_ID_STORAGE_KEY = 'jaguarclaw.device.id'
const SENSITIVE_KEYS = new Set([
  'credential', 'password', 'token', 'apiKey', 'apikey', 'accessToken', 'refreshToken',
  'authorization', 'secret', 'privateKey'
].map((k) => k.toLowerCase()))

// Global state
const connectionState = ref<ConnectionState>('disconnected')
const socket = ref<WebSocket | null>(null)
const authenticated = ref(false)
const authSession = ref<AuthSession | null>(loadAuthSession())
const pendingRequests = new Map<string, {
  resolve: (value: RpcResponse) => void
  reject: (reason: Error) => void
}>()

// Event handlers
// 用一个“最宽”的事件类型来存 handler（内部存储用）
type AnyRpcEvent = RpcEvent<AgentEventType>

// Event handlers
const eventHandlers = new Map<string, Set<(event: AnyRpcEvent) => void>>()
const rpcErrorHandlers = new Set<(error: RpcErrorEvent) => void>()

let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let intentionalDisconnect = false
let requestIdCounter = 0
let authPromise: Promise<void> | null = null

interface AuthSession {
  principalId: string
  roles: string[]
  accessToken: string
  refreshToken: string
  accessExpiresAt: string
  refreshExpiresAt: string
}

interface RpcErrorLike {
  code: string
  message: string
}

export interface RpcErrorEvent {
  code: string
  message: string
  method?: string
}

class RpcRequestError extends Error {
  code: string
  method?: string

  constructor(error: RpcErrorLike, method?: string) {
    super(error.message)
    this.name = 'RpcRequestError'
    this.code = error.code
    this.method = method
  }
}

function sanitizeForLog(value: unknown): unknown {
  if (value === null || value === undefined) return value
  if (Array.isArray(value)) return value.map((v) => sanitizeForLog(v))
  if (typeof value !== 'object') return value

  const source = value as Record<string, unknown>
  const sanitized: Record<string, unknown> = {}
  for (const [key, fieldValue] of Object.entries(source)) {
    if (SENSITIVE_KEYS.has(key.toLowerCase())) {
      sanitized[key] = '[REDACTED]'
    } else {
      sanitized[key] = sanitizeForLog(fieldValue)
    }
  }
  return sanitized
}

function generateId(): string {
  return `req-${Date.now()}-${++requestIdCounter}`
}

function generateNonce(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `nonce-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function generateDeviceId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return `device-${crypto.randomUUID()}`
  }
  return `device-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function getOrCreateDeviceId(): string {
  try {
    const saved = window.localStorage.getItem(DEVICE_ID_STORAGE_KEY)
    if (saved && saved.trim().length > 0) return saved

    const deviceId = generateDeviceId()
    window.localStorage.setItem(DEVICE_ID_STORAGE_KEY, deviceId)
    return deviceId
  } catch {
    return generateDeviceId()
  }
}

function loadAuthSession(): AuthSession | null {
  try {
    const raw = window.localStorage.getItem(AUTH_STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as AuthSession
    if (!parsed.refreshToken || !parsed.principalId) return null
    return parsed
  } catch {
    return null
  }
}

function saveAuthSession(session: AuthSession | null) {
  authSession.value = session
  try {
    if (!session) {
      window.localStorage.removeItem(AUTH_STORAGE_KEY)
      return
    }
    window.localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session))
  } catch {
    // ignore persistence errors
  }
}

function normalizeAuthSession(payload: unknown): AuthSession {
  const data = (payload ?? {}) as Partial<AuthSession>
  if (!data.principalId || !data.accessToken || !data.refreshToken) {
    throw new Error('Invalid auth payload')
  }
  return {
    principalId: data.principalId,
    roles: Array.isArray(data.roles) ? data.roles : [],
    accessToken: data.accessToken,
    refreshToken: data.refreshToken,
    accessExpiresAt: data.accessExpiresAt ?? '',
    refreshExpiresAt: data.refreshExpiresAt ?? ''
  }
}

function connect() {
  if (
    socket.value?.readyState === WebSocket.OPEN ||
    socket.value?.readyState === WebSocket.CONNECTING
  ) {
    return
  }

  intentionalDisconnect = false
  connectionState.value = 'connecting'
  authenticated.value = false

  const ws = new WebSocket(WS_URL)

  ws.onopen = () => {
    console.log('[WS] Connected')
    authenticateOnConnect()
      .then(() => {
        connectionState.value = 'connected'
      })
      .catch((error) => {
        connectionState.value = 'error'
        console.error('[WS] Authentication failed:', error)
        ws.close()
      })
  }

  ws.onclose = () => {
    connectionState.value = 'disconnected'
    socket.value = null
    authenticated.value = false
    console.log('[WS] Disconnected')

    for (const { reject } of pendingRequests.values()) {
      reject(new Error('WebSocket disconnected'))
    }
    pendingRequests.clear()

    // Auto reconnect after 3s, unless disconnect was intentional
    if (!intentionalDisconnect && !reconnectTimer) {
      reconnectTimer = setTimeout(() => {
        reconnectTimer = null
        connect()
      }, 3000)
    }
  }

  ws.onerror = (error) => {
    connectionState.value = 'error'
    console.error('[WS] Error:', error)
  }

  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      if (DEBUG_WS) {
        console.debug('[WS] Received:', sanitizeForLog(data))
      }

      // Check message type
      if (data.type === 'response' && data.id && pendingRequests.has(data.id)) {
        // It's a response to a pending request
        const { resolve } = pendingRequests.get(data.id)!
        pendingRequests.delete(data.id)
        resolve(data as RpcResponse)
        return
      }

      if (data.type === 'event' && data.event) {
        const rpcEvent = data as AnyRpcEvent
        const handlers = eventHandlers.get(rpcEvent.event)
        if (handlers) handlers.forEach((h) => h(rpcEvent))

        const wildcardHandlers = eventHandlers.get('*')
        if (wildcardHandlers) wildcardHandlers.forEach((h) => h(rpcEvent))
      }
    } catch (e) {
      console.error('[WS] Parse error:', e)
    }
  }

  socket.value = ws
}

function disconnect() {
  intentionalDisconnect = true
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  socket.value?.close()
  socket.value = null
  authenticated.value = false
  connectionState.value = 'disconnected'
}

function waitForConnection(timeoutMs = 10000): Promise<void> {
  return new Promise((resolve, reject) => {
    if (socket.value?.readyState === WebSocket.OPEN) {
      resolve()
      return
    }

    const timeout = setTimeout(() => {
      clearInterval(check)
      reject(new Error('WebSocket connection timeout'))
    }, timeoutMs)

    const check = setInterval(() => {
      if (socket.value?.readyState === WebSocket.OPEN) {
        clearTimeout(timeout)
        clearInterval(check)
        resolve()
      }
    }, 50)
  })
}

async function request<T = unknown>(method: string, payload?: unknown): Promise<T> {
  const isPublic = PUBLIC_METHODS.has(method)

  if (!isPublic) {
    await ensureAuthenticated()
  }

  try {
    return await requestRaw<T>(method, payload)
  } catch (error) {
    if (!isPublic && isUnauthorizedError(error)) {
      await ensureAuthenticated(true)
      try {
        return await requestRaw<T>(method, payload)
      } catch (retryError) {
        emitRpcError(retryError, method)
        throw retryError
      }
    }
    emitRpcError(error, method)
    throw error
  }
}

async function requestRaw<T = unknown>(method: string, payload?: unknown): Promise<T> {
  if (!socket.value || socket.value.readyState !== WebSocket.OPEN) {
    await waitForConnection()
  }

  const id = generateId()
  const rpcRequest: RpcRequest = {
    type: 'request',
    id,
    method,
    payload,
    timestamp: Date.now(),
    nonce: generateNonce(),
    idempotencyKey: `idem-${id}`
  }

  if (DEBUG_WS) {
    console.debug('[WS] Sending:', sanitizeForLog(rpcRequest))
  }

  return new Promise((resolve, reject) => {
    pendingRequests.set(id, {
      resolve: (response) => {
        if (response.error) {
          reject(new RpcRequestError(response.error, method))
        } else {
          resolve(response.payload as T)
        }
      },
      reject
    })

    socket.value!.send(JSON.stringify(rpcRequest))

    setTimeout(() => {
      if (pendingRequests.has(id)) {
        pendingRequests.delete(id)
        reject(new Error('Request timeout'))
      }
    }, 30000)
  })
}

async function ensureAuthenticated(force = false): Promise<void> {
  if (!force && authenticated.value) return
  await waitForConnection()
  await authenticateOnConnect(force)
}

async function authenticateOnConnect(force = false): Promise<void> {
  if (authPromise) return authPromise
  if (!force && authenticated.value) return

  authPromise = (async () => {
    authenticated.value = false

    // 1) 优先 refresh（如果有 refresh token）
    const saved = authSession.value
    if (saved?.refreshToken) {
      try {
        const refreshed = await requestRaw<AuthSession>('auth.refresh', {
          refreshToken: saved.refreshToken
        })
        const normalized = normalizeAuthSession(refreshed)
        saveAuthSession(normalized)
        authenticated.value = true
        return
      } catch (error) {
        console.warn('[WS] Refresh failed, fallback to bootstrap:', error)
        saveAuthSession(null)
      }
    }

    // 2) fallback: local bootstrap
    const deviceId = getOrCreateDeviceId()
    const bootstrapped = await requestRaw<AuthSession>('auth.local.bootstrap', { deviceId })
    const normalized = normalizeAuthSession(bootstrapped)
    saveAuthSession(normalized)
    authenticated.value = true
  })()
    .finally(() => {
      authPromise = null
    })

  return authPromise
}

function isUnauthorizedError(error: unknown): boolean {
  return error instanceof RpcRequestError && error.code === 'UNAUTHORIZED'
}

function emitRpcError(error: unknown, method?: string) {
  let payload: RpcErrorEvent | null = null

  if (error instanceof RpcRequestError) {
    payload = { code: error.code, message: error.message, method: error.method ?? method }
  } else if (error instanceof Error) {
    payload = { code: 'UNKNOWN_ERROR', message: error.message, method }
  }

  if (!payload) return
  rpcErrorHandlers.forEach((handler) => {
    try {
      handler(payload as RpcErrorEvent)
    } catch (handlerError) {
      console.error('[WS] rpc error handler failed:', handlerError)
    }
  })
}

// ✅ onEvent：事件名是 AgentEventType 时，payload 自动推断
function onEvent<K extends AgentEventType>(
    eventType: K,
    handler: (event: RpcEvent<K>) => void
): () => void

// ✅ 兜底：允许监听任意字符串（比如你以后有 * 或后端新事件）
function onEvent(
    eventType: string,
    handler: (event: AnyRpcEvent) => void
): () => void

function onEvent(
    eventType: string,
    handler: (event: AnyRpcEvent) => void
) {
  if (!eventHandlers.has(eventType)) {
    eventHandlers.set(eventType, new Set())
  }
  eventHandlers.get(eventType)!.add(handler)

  return () => {
    eventHandlers.get(eventType)?.delete(handler)
  }
}

function onRpcError(handler: (error: RpcErrorEvent) => void): () => void {
  rpcErrorHandlers.add(handler)
  return () => {
    rpcErrorHandlers.delete(handler)
  }
}

export function useWebSocket() {
  return {
    state: readonly(connectionState),
    connect,
    disconnect,
    request,
    onEvent,
    onRpcError
  }
}
