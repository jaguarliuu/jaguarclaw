import { ref, computed, watch } from 'vue'

export interface HeartbeatNotification {
  id: string
  content: string
  sessionId: string
  runId: string
  createdAt: string
  read: boolean
}

const STORAGE_KEY = 'heartbeat-notifications'
const MAX_NOTIFICATIONS = 50

function loadFromStorage(): HeartbeatNotification[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : []
  } catch {
    return []
  }
}

// Module-level state — shared across all composable calls
const notifications = ref<HeartbeatNotification[]>(loadFromStorage())
const unreadCount = computed(() => notifications.value.filter(n => !n.read).length)
const selectedNotificationId = ref<string | null>(null)
const selectedNotification = computed(() =>
  selectedNotificationId.value
    ? (notifications.value.find(n => n.id === selectedNotificationId.value) ?? null)
    : null
)

watch(notifications, (val) => {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(val))
  } catch {}
}, { deep: true })

export function useHeartbeat() {
  function addNotification(content: string, sessionId: string, runId: string) {
    if (notifications.value.some(n => n.id === runId)) return
    notifications.value.unshift({
      id: runId,
      content,
      sessionId,
      runId,
      createdAt: new Date().toISOString(),
      read: false
    })
    if (notifications.value.length > MAX_NOTIFICATIONS) {
      notifications.value = notifications.value.slice(0, MAX_NOTIFICATIONS)
    }
  }

  function markAsRead(id: string) {
    const n = notifications.value.find(n => n.id === id)
    if (n) n.read = true
  }

  function markAllAsRead() {
    notifications.value.forEach(n => { n.read = true })
  }

  function clearAll() {
    notifications.value = []
    selectedNotificationId.value = null
  }

  function selectNotification(id: string | null) {
    selectedNotificationId.value = id
    if (id) markAsRead(id)
  }

  return {
    notifications,
    unreadCount,
    selectedNotification,
    addNotification,
    markAsRead,
    markAllAsRead,
    clearAll,
    selectNotification
  }
}

