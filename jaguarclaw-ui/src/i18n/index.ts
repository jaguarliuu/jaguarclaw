import { ref, computed } from 'vue'
import zh from './locales/zh'
import en from './locales/en'

export type Locale = 'zh' | 'en'

// ── Derive all valid dot-notation key paths from the zh structure ──
type DeepKeys<T extends object, Prefix extends string = ''> = {
  [K in keyof T & string]:
    T[K] extends string
      ? `${Prefix}${K}`
      : T[K] extends object
        ? DeepKeys<T[K], `${Prefix}${K}.`>
        : never
}[keyof T & string]

export type TranslationKey = DeepKeys<typeof zh>

// ── Module-level reactive state (one instance shared across all components) ──
const locale = ref<Locale>(
  (localStorage.getItem('mc-locale') as Locale) ?? 'zh'
)

const messages = computed(() => locale.value === 'zh' ? zh : en)

function resolve(obj: unknown, path: string): string {
  const parts = path.split('.')
  let cur: unknown = obj
  for (const p of parts) {
    if (cur == null || typeof cur !== 'object') return path
    cur = (cur as Record<string, unknown>)[p]
  }
  return typeof cur === 'string' ? cur : path
}

export function useI18n() {
  function t(key: TranslationKey, vars?: Record<string, string>): string {
    let val = resolve(messages.value, key)
    if (vars) {
      for (const [k, v] of Object.entries(vars)) {
        val = val.replace(`{${k}}`, v)
      }
    }
    return val
  }

  function setLocale(lang: Locale) {
    locale.value = lang
    localStorage.setItem('mc-locale', lang)
  }

  return { t, locale, setLocale }
}
