/// <reference types="vite/client" />

// Electron API 类型定义
interface Window {
  electron?: {
    isElectron: boolean
    selectFolder: () => Promise<string | null>
    getRuntimeConfig: () => Promise<{
      mode: 'auto' | 'bundled' | 'local'
      effectiveMode: 'bundled' | 'local'
      bundledAvailable: boolean
      bundledSource?: string
      bundledHome?: string | null
    }>
    setRuntimeMode: (mode: 'auto' | 'bundled' | 'local') => Promise<{
      mode: 'auto' | 'bundled' | 'local'
      restartRequired: boolean
    }>
    restartApp: () => Promise<{ accepted: boolean }>
  }
}
