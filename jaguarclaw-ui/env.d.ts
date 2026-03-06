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
    getAppInfo: () => Promise<{
      name: string
      version: string
      paths: {
        appData: string
        data: string
        workspace: string
        skills: string
        logs: string
        startupLog: string
        desktopLog: string
        backendBridgeLog: string
      }
    }>
    openAppPath: (target: 'appData' | 'data' | 'workspace' | 'skills' | 'logs' | 'startupLog' | 'desktopLog' | 'backendBridgeLog') => Promise<{
      target: string
      path: string
      success: boolean
      error: string | null
    }>
  }
}
