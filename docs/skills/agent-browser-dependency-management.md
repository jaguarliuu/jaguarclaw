# agent-browser Skill 依赖管理（Desktop 内置模式）

本文档描述当前的依赖策略：**桌面端默认内置 `agent-browser` + Chromium kernel**，不要求用户手动安装或配置 PATH。

## 当前策略

1. Electron 启动时注入 runtime 环境：
- `AGENT_BROWSER_EXECUTABLE_PATH`
- `AGENT_BROWSER_CHROMIUM_PATH`
- `AGENT_BROWSER_KERNEL_HOME`
- `AGENT_BROWSER_PROVIDER=kernel`
- `AGENT_BROWSER_SKIP_INSTALL=1`

2. Java 侧统一解析 bundled runtime：
- binary 优先级：`explicit path > bundled runtime > PATH`
- kernel 优先级：`explicit path > bundled runtime`

3. Skill gating 判定：
- `agent-browser` binary 可解析
- Chromium kernel 可解析（bundled 模式下为必需）

4. Shell 执行自动注入会话隔离变量：
- `AGENT_BROWSER_SESSION`
- `AGENT_BROWSER_PROFILE`

## 目录约定

- runtime 中的浏览器组件（默认）：
  - `runtime/staging/bin/agent-browser.cmd`
  - `runtime/staging/browser/chromium/chrome.exe`
- profile 持久化目录：
  - `<appData>/browser-profiles/<agentId>/<sessionId>`

## 诊断建议

当 skill 显示不可用时，优先检查：

1. runtime 包内是否存在 binary/kernel。
2. 上述 `AGENT_BROWSER_*` 环境变量是否已注入。
3. 目标路径是否有执行权限与读权限。

## 非 Desktop 场景

Web/Server 部署可以继续使用系统安装版本或远端 provider，但桌面端默认不依赖用户本地安装。
