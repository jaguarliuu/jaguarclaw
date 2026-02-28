import { readonly, ref } from 'vue'

export type ToastType = 'info' | 'success' | 'warning' | 'error'

export interface ToastOptions {
  type?: ToastType
  title?: string
  message: string
  durationMs?: number
  dedupeKey?: string
  dedupeWindowMs?: number
}

export interface ToastItem {
  id: string
  type: ToastType
  title?: string
  message: string
  createdAt: number
}

const toasts = ref<ToastItem[]>([])
const lastShownAtByKey = new Map<string, number>()
let toastIdCounter = 0

function nextToastId(): string {
  toastIdCounter += 1
  return `toast-${Date.now()}-${toastIdCounter}`
}

function showToast(options: ToastOptions): string | null {
  const now = Date.now()
  const dedupeKey = options.dedupeKey
  const dedupeWindowMs = options.dedupeWindowMs ?? 3000

  if (dedupeKey) {
    const lastShownAt = lastShownAtByKey.get(dedupeKey)
    if (lastShownAt && now - lastShownAt < dedupeWindowMs) {
      return null
    }
    lastShownAtByKey.set(dedupeKey, now)
  }

  const toast: ToastItem = {
    id: nextToastId(),
    type: options.type ?? 'info',
    title: options.title,
    message: options.message,
    createdAt: now
  }
  toasts.value.push(toast)

  const durationMs = options.durationMs ?? 4000
  setTimeout(() => {
    dismissToast(toast.id)
  }, durationMs)

  return toast.id
}

function dismissToast(id: string): void {
  const index = toasts.value.findIndex((item) => item.id === id)
  if (index >= 0) {
    toasts.value.splice(index, 1)
  }
}

function clearToasts(): void {
  toasts.value = []
}

export function useToast() {
  return {
    toasts: readonly(toasts),
    showToast,
    dismissToast,
    clearToasts
  }
}

