const { contextBridge, ipcRenderer } = require('electron');

// 暴露安全的 API 给渲染进程
contextBridge.exposeInMainWorld('electron', {
  // 选择文件夹
  selectFolder: () => ipcRenderer.invoke('dialog:selectFolder'),

  // 运行时来源配置
  getRuntimeConfig: () => ipcRenderer.invoke('runtime:getConfig'),
  setRuntimeMode: (mode) => ipcRenderer.invoke('runtime:setMode', { mode }),
  restartApp: () => ipcRenderer.invoke('app:restart'),
  getAppInfo: () => ipcRenderer.invoke('app:getInfo'),
  openAppPath: (target) => ipcRenderer.invoke('app:openPath', { target }),

  // 监听启动日志
  onStartupLog: (callback) => {
    const listener = (_event, payload) => callback(payload);
    ipcRenderer.on('startup:log', listener);
    return () => ipcRenderer.removeListener('startup:log', listener);
  },

  // 监听启动状态
  onStartupStatus: (callback) => {
    const listener = (_event, payload) => callback(payload);
    ipcRenderer.on('startup:status', listener);
    return () => ipcRenderer.removeListener('startup:status', listener);
  },

  // 检查是否在 Electron 环境中
  isElectron: true
});
