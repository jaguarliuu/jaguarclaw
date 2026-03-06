import { ref } from 'vue'

export interface ChangelogSections {
  added: string[]
  changed: string[]
  fixed: string[]
}

export interface ChangelogEntry {
  version: string
  date: string
  title: string
  sections: ChangelogSections
}

export interface AppPaths {
  appData: string
  data: string
  workspace: string
  skills: string
  logs: string
  startupLog: string
  desktopLog: string
  backendBridgeLog: string
}

export interface DesktopAppInfo {
  name: string
  version: string
  paths: AppPaths
}

function normalizeList(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []
}

function normalizeChangelogEntry(raw: unknown): ChangelogEntry | null {
  if (!raw || typeof raw !== 'object') return null
  const entry = raw as Record<string, unknown>
  const sections = (entry.sections && typeof entry.sections === 'object') ? entry.sections as Record<string, unknown> : {}
  const legacyItems = normalizeList(entry.items)

  return {
    version: typeof entry.version === 'string' ? entry.version : 'unknown',
    date: typeof entry.date === 'string' ? entry.date : '',
    title: typeof entry.title === 'string' ? entry.title : '',
    sections: {
      added: normalizeList(sections.added),
      changed: normalizeList(sections.changed).length ? normalizeList(sections.changed) : legacyItems,
      fixed: normalizeList(sections.fixed),
    },
  }
}

export function useAboutInfo() {
  const changelog = ref<ChangelogEntry[]>([])
  const appInfo = ref<DesktopAppInfo | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchChangelog() {
    const response = await fetch('/api/system/changelog')
    if (!response.ok) {
      throw new Error('Failed to fetch changelog')
    }
    const data = await response.json()
    changelog.value = Array.isArray(data.entries)
      ? data.entries.map(normalizeChangelogEntry).filter((entry: ChangelogEntry | null): entry is ChangelogEntry => !!entry)
      : []
  }

  async function fetchAppInfo() {
    if (typeof window === 'undefined' || !window.electron?.getAppInfo) {
      appInfo.value = null
      return
    }
    appInfo.value = await window.electron.getAppInfo()
  }

  async function refresh() {
    loading.value = true
    error.value = null
    try {
      await Promise.all([fetchChangelog(), fetchAppInfo()])
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Unknown error'
    } finally {
      loading.value = false
    }
  }

  async function openPath(target: keyof AppPaths) {
    if (!window.electron?.openAppPath) {
      throw new Error('Electron shell integration is unavailable')
    }
    return window.electron.openAppPath(target)
  }

  return {
    changelog,
    appInfo,
    loading,
    error,
    refresh,
    openPath,
  }
}
