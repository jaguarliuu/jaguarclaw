import { ref, watch } from 'vue'

export type ThemeId = 'default' | 'teal' | 'ocean' | 'forest' | 'grape' | 'sunset'

export interface Theme {
  id: ThemeId
  name: string
  color: string
}

export const THEMES: Theme[] = [
  { id: 'default', name: 'Midnight', color: '#111111' },
  { id: 'teal',    name: 'Teal',     color: '#009191' },  // C100M5Y5K40
  { id: 'ocean',   name: 'Ocean',    color: '#1a6cdb' },
  { id: 'forest',  name: 'Forest',   color: '#2d7d46' },
  { id: 'grape',   name: 'Grape',    color: '#6d28d9' },
  { id: 'sunset',  name: 'Sunset',   color: '#c2410c' },
]

const STORAGE_KEY = 'miniclaw-theme'

function readStored(): ThemeId {
  try {
    const v = localStorage.getItem(STORAGE_KEY)
    if (v && THEMES.some(t => t.id === v)) return v as ThemeId
  } catch { /* ignore */ }
  return 'default'
}

const activeTheme = ref<ThemeId>(readStored())

function applyTheme(id: ThemeId) {
  const el = document.documentElement
  // Temporarily enable smooth transitions across all elements
  el.classList.add('theme-switching')
  if (id === 'default') {
    el.removeAttribute('data-theme')
  } else {
    el.setAttribute('data-theme', id)
  }
  setTimeout(() => el.classList.remove('theme-switching'), 400)
}

// Apply persisted theme immediately before Vue mounts (avoids flash)
applyTheme(activeTheme.value)

watch(activeTheme, (id) => {
  applyTheme(id)
  try { localStorage.setItem(STORAGE_KEY, id) } catch { /* ignore */ }
})

export function useTheme() {
  return {
    currentTheme: activeTheme,
    themes: THEMES,
    setTheme(id: ThemeId) {
      activeTheme.value = id
    },
  }
}
