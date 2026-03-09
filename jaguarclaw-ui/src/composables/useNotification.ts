import { useRouter } from 'vue-router'
import { useWebSocket } from './useWebSocket'
import type { ToolConfirmRequestPayload, RunOutcomePayload } from '@/types'
import type { RpcErrorEvent } from './useWebSocket'
import { useToast } from './useToast'
import { useI18n } from '@/i18n'

let isSetup = false

export function useNotification() {
  if (isSetup) return
  isSetup = true

  const router = useRouter()
  const { onEvent, onRpcError } = useWebSocket()
  const { showToast } = useToast()
  const { t } = useI18n()

  // Request notification permission
  if ('Notification' in window && Notification.permission === 'default') {
    Notification.requestPermission()
  }

  onEvent('run.outcome', (event) => {
    const payload = event.payload as RunOutcomePayload | undefined
    if (!payload || (!payload.pendingQuestion && payload.planStatus !== 'BLOCKED')) {
      return
    }

    const isOnWorkspace = router.currentRoute.value.path === '/'
    const isHidden = document.visibilityState === 'hidden'
    const title = payload.pendingQuestion ? t('workspace.runOutcome.toastTitle') : t('workspace.runOutcome.popupBlockedTitle')
    const body = payload.pendingQuestion || payload.message || payload.detail || payload.reason

    if (isOnWorkspace && !isHidden) {
      return
    }

    if ('Notification' in window && Notification.permission === 'granted') {
      const n = new Notification(`JaguarClaw - ${title}`, {
        body,
        tag: `run-outcome-${event.runId}`
      })
      n.onclick = () => {
        window.focus()
        router.push('/')
        n.close()
      }
    } else {
      showToast({
        type: 'warning',
        title,
        message: body,
        dedupeKey: `run-outcome-${event.runId}`,
        dedupeWindowMs: 3000,
        durationMs: 5000
      })
    }
  })

  onEvent('tool.confirm_request', (event) => {
    const payload = event.payload as ToolConfirmRequestPayload | undefined
    const toolName = payload?.toolName ?? 'Tool'
    const callId = payload?.callId ?? toolName
    const isOnWorkspace = router.currentRoute.value.path === '/'
    const isHidden = document.visibilityState === 'hidden'

    if (isOnWorkspace && !isHidden) {
      showToast({
        type: 'warning',
        title: '需要确认',
        message: `${toolName} 需要你的确认`,
        dedupeKey: `tool-confirm-${callId}`,
        dedupeWindowMs: 3000,
        durationMs: 5000
      })
      return
    }

    if (!isOnWorkspace || isHidden) {
      if ('Notification' in window && Notification.permission === 'granted') {
        const n = new Notification('JaguarClaw - Action Required', {
          body: `${toolName} requires your approval`,
          tag: `tool-confirm-${callId}`
        })
        n.onclick = () => {
          window.focus()
          router.push('/')
          n.close()
        }
      }
    }
  })

  onRpcError((error) => {
    notifySecurityError(error, showToast)
  })
}

function notifySecurityError(
  error: RpcErrorEvent,
  showToast: (options: {
    type?: 'info' | 'success' | 'warning' | 'error'
    title?: string
    message: string
    durationMs?: number
    dedupeKey?: string
    dedupeWindowMs?: number
  }) => string | null
) {
  const title = 'JaguarClaw - Request Notice'
  const body = mapRpcErrorMessage(error)
  const toastType = mapRpcErrorToastType(error)

  console.warn('[WS][RPC_ERROR]', error.code, error.method, error.message)

  showToast({
    type: toastType,
    title: '请求提示',
    message: body,
    dedupeKey: `rpc-${error.code}-${error.method ?? 'unknown'}`,
    dedupeWindowMs: 3000,
    durationMs: 4500
  })

  const isHidden = document.visibilityState === 'hidden'
  if (isHidden && 'Notification' in window && Notification.permission === 'granted') {
    const notification = new Notification(title, {
      body,
      tag: `rpc-${error.code}`
    })
    notification.onclick = () => {
      window.focus()
      notification.close()
    }
  }
}

function mapRpcErrorMessage(error: RpcErrorEvent): string {
  switch (error.code) {
    case 'UNAUTHORIZED':
      return 'Connection authentication expired. Retrying automatically.'
    case 'PERMISSION_DENIED':
      return 'Permission denied for current operation.'
    case 'RATE_LIMITED':
      return 'Too many requests. Please wait a moment.'
    case 'TOKEN_BUDGET_EXCEEDED':
      return 'Daily token budget exceeded.'
    case 'REQUEST_EXPIRED':
    case 'REPLAY_DETECTED':
    case 'DUPLICATE_REQUEST':
      return 'Request blocked by replay protection.'
    default:
      return error.message || 'Request failed.'
  }
}

function mapRpcErrorToastType(error: RpcErrorEvent): 'info' | 'warning' | 'error' {
  switch (error.code) {
    case 'UNAUTHORIZED':
      return 'info'
    case 'RATE_LIMITED':
    case 'REQUEST_EXPIRED':
    case 'REPLAY_DETECTED':
    case 'DUPLICATE_REQUEST':
      return 'warning'
    default:
      return 'error'
  }
}
