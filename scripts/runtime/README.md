# Browser Runtime Staging Scripts

用于把 `agent-browser + Chromium` 落地到 `runtime/staging`，供后续打包 `runtime.zip` 使用。

## Linux/macOS

```bash
bash scripts/runtime/stage-browser-runtime.sh
```

可选覆盖下载源：

```bash
bash scripts/runtime/stage-browser-runtime.sh \
  --agent-source /path/to/agent-browser-win32-x64.exe \
  --chromium-source /path/to/chrome-win64.zip
```

## Windows PowerShell

```powershell
powershell -ExecutionPolicy Bypass -File scripts/runtime/stage-browser-runtime.ps1
```

可选覆盖下载源：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/runtime/stage-browser-runtime.ps1 `
  -AgentSource "D:\pkgs\agent-browser-win32-x64.exe" `
  -ChromiumSource "D:\pkgs\chrome-win64.zip"
```

## Next Step

staging 完成后执行：

```bash
node electron/scripts/package-runtime.js
```
