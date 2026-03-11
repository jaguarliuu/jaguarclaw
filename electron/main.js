const { app, BrowserWindow, dialog, ipcMain } = require('electron');
const { spawn, execSync, execFileSync } = require('child_process');
const path = require('path');
const http = require('http');
const portfinder = require('portfinder');
const { autoUpdater } = require('electron-updater');
const crypto = require('crypto');
const fs = require('fs');
const { createLogManager } = require('./scripts/lib/log-manager');
const { resolveAppIdentity } = require('./scripts/lib/app-identity');

let mainWindow = null;
let splashWindow = null;
let javaProcess = null;
let serverPort = null;
let javaErrorLogs = [];  // 收集 Java 错误日志
let startupLogs = [];
const STARTUP_TOTAL_STEPS = 6;
const STARTUP_PROGRESS = Object.freeze({
  prepare: 1,
  runtime: 2,
  setup: 3,
  port: 4,
  backend: 5,
  health: 6,
  ready: 6,
});
let currentStartupStatus = {
  message: 'Initializing...',
  step: 0,
  total: STARTUP_TOTAL_STEPS,
  percent: 0,
  failed: false,
};

// Paths
const isPackaged = app.isPackaged;
const appIdentity = resolveAppIdentity({
  appName: app.getName(),
  exePath: process.execPath,
  isPackaged,
});
const APP_DISPLAY_NAME = appIdentity.displayName;
if (process.platform === 'win32' && typeof app.setAppUserModelId === 'function') {
  app.setAppUserModelId(appIdentity.appUserModelId);
}
app.setPath('userData', path.join(app.getPath('appData'), appIdentity.dataDirName));
const resourcesPath = isPackaged
  ? path.join(process.resourcesPath)
  : path.join(__dirname, 'resources');

const jrePath = path.join(resourcesPath, 'jre');
const jarPath = path.join(resourcesPath, 'app.jar');
const webappPath = path.join(resourcesPath, 'webapp');
const appDataPath = app.getPath('userData');

const dataDir = path.join(appDataPath, 'data');
const dbPath = path.join(appDataPath, 'jaguarclaw.db');
const workspacePath = path.join(appDataPath, 'workspace');
const skillsDir = path.join(appDataPath, 'skills');
const runtimeStoreRoot = path.join(appDataPath, 'runtime');
const builtinSkillsDir = path.join(resourcesPath, 'skills');
const configPath = path.join(appDataPath, 'config.json');
const logsDir = path.join(appDataPath, 'logs');
const startupLogPath = path.join(logsDir, 'startup.log');
const desktopLogPath = path.join(logsDir, 'desktop.log');
const backendBridgeLogPath = path.join(logsDir, 'backend-bridge.log');
const STARTUP_LOG_LIMIT = 300;
const logManager = createLogManager({
  logDir: logsDir,
  maxFileSizeBytes: 10 * 1024 * 1024,
  maxHistory: 20,
  maxAgeDays: 7,
});
const RUNTIME_MODES = Object.freeze(['auto', 'bundled', 'local']);
let currentRuntimeInfo = {
  mode: 'auto',
  effectiveMode: 'local',
  enabled: false,
  home: null,
  source: 'none',
  bundledAvailable: false,
  bundledSource: 'none',
  browser: {
    agentBrowserPath: null,
    chromiumPath: null,
    chromiumHome: null,
    ready: false,
  },
};

function resolveResourceFile(fileName) {
  const candidates = [
    path.join(resourcesPath, fileName),
    path.join(__dirname, 'resources', fileName),
  ];
  return candidates.find((candidate) => fs.existsSync(candidate)) || null;
}

function resolveResourceDirectory(dirName) {
  const candidates = [
    path.join(resourcesPath, dirName),
    path.join(__dirname, 'resources', dirName),
  ];
  return candidates.find((candidate) => fs.existsSync(candidate) && fs.statSync(candidate).isDirectory()) || null;
}

function sendToSplash(channel, payload) {
  if (splashWindow && !splashWindow.isDestroyed()) {
    splashWindow.webContents.send(channel, payload);
  }
}

function publishStartupStatus(message, stage = null, failed = false) {
  let step = currentStartupStatus.step;
  if (stage && STARTUP_PROGRESS[stage] !== undefined) {
    step = STARTUP_PROGRESS[stage];
  }

  currentStartupStatus = {
    message,
    step,
    total: STARTUP_TOTAL_STEPS,
    percent: Math.round((step / STARTUP_TOTAL_STEPS) * 100),
    failed,
  };

  sendToSplash('startup:status', currentStartupStatus);
}

function appendLogToChannel(channel, level, message) {
  try {
    logManager.append(channel, level, message);
  } catch (err) {
    console.error(`Failed to write ${channel} log:`, err.message);
  }
}

function appendDesktopLog(level, message) {
  appendLogToChannel('desktop', level, message);
}

function appendBackendBridgeLog(level, message) {
  appendLogToChannel('backend-bridge', level, message);
}

function appendStartupLog(level, message) {
  const text = String(message ?? '').replace(/\r/g, '');
  const lines = text.split('\n').map((line) => line.trim()).filter(Boolean);

  for (const line of lines) {
    const entry = {
      level,
      message: line,
      timestamp: new Date().toISOString(),
    };

    startupLogs.push(entry);
    if (startupLogs.length > STARTUP_LOG_LIMIT) {
      startupLogs.shift();
    }

    if (level === 'error') {
      javaErrorLogs.push(line);
      if (javaErrorLogs.length > 50) {
        javaErrorLogs.shift();
      }
    }

    appendLogToChannel('startup', level, line);
    sendToSplash('startup:log', entry);
  }
}

function ensureDirectories() {
  for (const dir of [appDataPath, dataDir, workspacePath, skillsDir, runtimeStoreRoot, logsDir]) {
    fs.mkdirSync(dir, { recursive: true });
  }
}

function readAppConfig() {
  if (!fs.existsSync(configPath)) {
    return {};
  }
  try {
    const data = fs.readFileSync(configPath, 'utf8');
    const parsed = JSON.parse(data);
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch (err) {
    appendDesktopLog('error', `Failed to read config, using defaults: ${err.message}`);
    return {};
  }
}

function writeAppConfig(config) {
  fs.writeFileSync(configPath, JSON.stringify(config || {}, null, 2), 'utf8');
}

function normalizeRuntimeMode(value) {
  return RUNTIME_MODES.includes(value) ? value : 'auto';
}

function getConfiguredRuntimeMode() {
  const config = readAppConfig();
  return normalizeRuntimeMode(config.runtimeMode);
}

function setConfiguredRuntimeMode(mode) {
  const normalized = normalizeRuntimeMode(mode);
  const config = readAppConfig();
  config.runtimeMode = normalized;
  writeAppConfig(config);
  return normalized;
}

function probeBundledRuntimeAvailability() {
  const runtimeDirInResources = resolveResourceDirectory('runtime');
  if (runtimeDirInResources) {
    return { available: true, source: 'resource-dir', home: runtimeDirInResources };
  }
  const runtimeZipInResources = resolveResourceFile('runtime.zip');
  if (runtimeZipInResources) {
    return { available: true, source: 'runtime.zip', home: null };
  }
  return { available: false, source: 'none', home: null };
}

function resolveRuntimeVersion() {
  const runtimeVersionFile = resolveResourceFile('runtime.version');
  if (runtimeVersionFile) {
    const value = fs.readFileSync(runtimeVersionFile, 'utf8').trim();
    if (value) return value;
  }

  const runtimeZip = resolveResourceFile('runtime.zip');
  if (runtimeZip) {
    const stat = fs.statSync(runtimeZip);
    return `zip-${stat.size}-${Math.floor(stat.mtimeMs)}`;
  }

  return 'builtin';
}

function sanitizeVersionSegment(input) {
  return String(input || 'builtin').replace(/[^a-zA-Z0-9._-]/g, '_');
}

function normalizeExtractedRuntimeRoot(extractRoot) {
  const entries = fs.readdirSync(extractRoot, { withFileTypes: true })
    .filter((entry) => !entry.name.startsWith('.'));

  // 如果 zip 内只有一个顶层目录，使用该目录作为 runtime 根目录
  if (entries.length === 1 && entries[0].isDirectory()) {
    return path.join(extractRoot, entries[0].name);
  }
  return extractRoot;
}

function extractZipArchive(archivePath, destinationDir) {
  fs.mkdirSync(destinationDir, { recursive: true });

  if (process.platform === 'win32') {
    const psCommand = `Expand-Archive -LiteralPath '${archivePath.replace(/'/g, "''")}' -DestinationPath '${destinationDir.replace(/'/g, "''")}' -Force`;
    execFileSync('powershell.exe', [
      '-NoProfile',
      '-NonInteractive',
      '-ExecutionPolicy',
      'Bypass',
      '-Command',
      psCommand,
    ], { stdio: 'pipe' });
    return;
  }

  if (process.platform === 'darwin') {
    execFileSync('/usr/bin/ditto', ['-x', '-k', archivePath, destinationDir], { stdio: 'pipe' });
    return;
  }

  execFileSync('unzip', ['-q', archivePath, '-d', destinationDir], { stdio: 'pipe' });
}

function ensureBundledRuntime() {
  const runtimeDirInResources = resolveResourceDirectory('runtime');
  const runtimeZipInResources = resolveResourceFile('runtime.zip');

  // 支持直接打包目录（开发/调试场景）
  if (runtimeDirInResources) {
    return {
      enabled: true,
      home: runtimeDirInResources,
      source: 'resource-dir',
    };
  }

  // 生产主路径：resources/runtime.zip -> appData/runtime/<version>
  if (!runtimeZipInResources) {
    return { enabled: false, home: null, source: 'none' };
  }

  const version = sanitizeVersionSegment(resolveRuntimeVersion());
  const targetDir = path.join(runtimeStoreRoot, version);
  const markerPath = path.join(targetDir, '.ready.marker');
  if (fs.existsSync(markerPath)) {
    return { enabled: true, home: targetDir, source: 'zip-cache', version };
  }

  const tmpRoot = path.join(runtimeStoreRoot, `.extract-${version}-${Date.now()}`);
  const tmpArchive = path.join(runtimeStoreRoot, `.runtime-${version}-${Date.now()}.zip`);
  fs.rmSync(tmpRoot, { recursive: true, force: true });
  fs.mkdirSync(tmpRoot, { recursive: true });

  try {
    let archiveForExtraction = runtimeZipInResources;
    // app.asar 内文件不是宿主系统真实文件路径，先落地到临时 zip 再解压
    if (runtimeZipInResources.includes('.asar')) {
      fs.copyFileSync(runtimeZipInResources, tmpArchive);
      archiveForExtraction = tmpArchive;
    }

    extractZipArchive(archiveForExtraction, tmpRoot);
    const extractedRoot = normalizeExtractedRuntimeRoot(tmpRoot);

    fs.rmSync(targetDir, { recursive: true, force: true });
    if (path.resolve(extractedRoot) === path.resolve(tmpRoot)) {
      fs.renameSync(tmpRoot, targetDir);
    } else {
      fs.renameSync(extractedRoot, targetDir);
      fs.rmSync(tmpRoot, { recursive: true, force: true });
    }

    fs.writeFileSync(markerPath, JSON.stringify({
      source: 'runtime.zip',
      version,
      generatedAt: new Date().toISOString(),
    }, null, 2), 'utf8');

    return { enabled: true, home: targetDir, source: 'zip-extracted', version };
  } catch (error) {
    fs.rmSync(tmpRoot, { recursive: true, force: true });
    throw new Error(`Failed to prepare bundled runtime: ${error.message}`);
  } finally {
    fs.rmSync(tmpArchive, { force: true });
  }
}

function firstExistingPath(candidates) {
  for (const candidate of candidates) {
    if (candidate && fs.existsSync(candidate)) {
      return candidate;
    }
  }
  return null;
}

function resolveBundledBrowserInfo(runtimeHome) {
  if (!runtimeHome) {
    return {
      agentBrowserPath: null,
      chromiumPath: null,
      chromiumHome: null,
      ready: false,
    };
  }

  const agentBrowserPath = firstExistingPath([
    path.join(runtimeHome, 'bin', 'agent-browser.exe'),
    path.join(runtimeHome, 'bin', 'agent-browser.cmd'),
    path.join(runtimeHome, 'bin', 'agent-browser'),
  ]);

  const chromiumHome = firstExistingPath([
    path.join(runtimeHome, 'browser', 'chromium'),
    path.join(runtimeHome, 'browser', 'chromium', 'chrome-win64'),
  ]);
  const chromiumPath = firstExistingPath([
    path.join(runtimeHome, 'browser', 'chromium', 'chrome.exe'),
    path.join(runtimeHome, 'browser', 'chromium', 'chrome-win64', 'chrome.exe'),
    path.join(runtimeHome, 'browser', 'chromium', 'chrome'),
  ]);

  return {
    agentBrowserPath,
    chromiumPath,
    chromiumHome,
    ready: !!(agentBrowserPath && chromiumPath),
  };
}

function resolveRuntimeInfo(runtimeMode) {
  const mode = normalizeRuntimeMode(runtimeMode);
  const probe = probeBundledRuntimeAvailability();

  if (mode === 'local') {
    return {
      mode,
      effectiveMode: 'local',
      enabled: false,
      home: null,
      source: 'local',
      bundledAvailable: probe.available,
      bundledSource: probe.source,
      browser: {
        agentBrowserPath: null,
        chromiumPath: null,
        chromiumHome: null,
        ready: false,
      },
    };
  }

  const bundled = ensureBundledRuntime();
  if (mode === 'bundled') {
    if (!bundled.enabled) {
      throw new Error('Runtime mode is "bundled", but bundled runtime package was not found.');
    }
    return {
      ...bundled,
      mode,
      effectiveMode: 'bundled',
      bundledAvailable: true,
      bundledSource: bundled.source || probe.source,
      browser: resolveBundledBrowserInfo(bundled.home),
    };
  }

  if (bundled.enabled) {
    return {
      ...bundled,
      mode,
      effectiveMode: 'bundled',
      bundledAvailable: true,
      bundledSource: bundled.source || probe.source,
      browser: resolveBundledBrowserInfo(bundled.home),
    };
  }

  return {
    mode,
    effectiveMode: 'local',
    enabled: false,
    home: null,
    source: 'none',
    bundledAvailable: probe.available,
    bundledSource: probe.source,
    browser: {
      agentBrowserPath: null,
      chromiumPath: null,
      chromiumHome: null,
      ready: false,
    },
  };
}

/**
 * 获取或生成加密密钥
 * 如果配置文件中没有密钥，自动生成一个 64 字符的 hex 密钥（32 字节）
 */
function getOrCreateEncryptionKey() {
  const config = readAppConfig();

  // 检查是否已有密钥
  if (config.encryptionKey && config.encryptionKey.length === 64) {
    appendDesktopLog('info', 'Using existing encryption key from config');
    return config.encryptionKey;
  }

  // 生成新密钥 (32 字节 = 64 hex 字符)
  const key = crypto.randomBytes(32).toString('hex');
  appendDesktopLog('info', 'Generated new encryption key');

  // 保存到配置文件
  config.encryptionKey = key;
  try {
    writeAppConfig(config);
    appendDesktopLog('info', 'Encryption key saved to config');
  } catch (err) {
    appendDesktopLog('error', `Failed to save encryption key: ${err.message}`);
    // 即使保存失败，也返回密钥继续启动
  }

  return key;
}

function createSplashWindow() {
  splashWindow = new BrowserWindow({
    width: 560,
    height: 420,
    frame: false,
    resizable: false,
    transparent: false,
    alwaysOnTop: true,
    icon: path.join(resourcesPath, 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      nodeIntegration: false,
      contextIsolation: true,
    },
  });

  splashWindow.loadURL(`data:text/html;charset=utf-8,${encodeURIComponent(`
    <!DOCTYPE html>
    <html>
    <head>
      <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
          font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
          display: flex; flex-direction: column;
          align-items: stretch; justify-content: center;
          height: 100vh;
          padding: 24px;
          background: linear-gradient(145deg, #1f2937 0%, #111827 100%);
          color: white;
          -webkit-app-region: drag;
        }
        .card {
          background: rgba(255, 255, 255, 0.08);
          border: 1px solid rgba(255, 255, 255, 0.15);
          border-radius: 12px;
          padding: 18px;
          display: flex;
          flex-direction: column;
          gap: 12px;
          min-height: 0;
          flex: 1;
        }
        .header {
          display: flex;
          align-items: center;
          gap: 10px;
        }
        .spinner {
          width: 20px; height: 20px;
          border: 3px solid rgba(255,255,255,0.3);
          border-top-color: white;
          border-radius: 50%;
          animation: spin 0.8s linear infinite;
        }
        @keyframes spin { to { transform: rotate(360deg); } }
        .status {
          font-size: 14px;
          opacity: 0.95;
          line-height: 1.4;
          -webkit-app-region: no-drag;
        }
        .progress-row {
          display: flex;
          justify-content: space-between;
          align-items: center;
          font-size: 12px;
          opacity: 0.9;
          -webkit-app-region: no-drag;
        }
        .progress-track {
          width: 100%;
          height: 6px;
          border-radius: 999px;
          background: rgba(255, 255, 255, 0.18);
          overflow: hidden;
          -webkit-app-region: no-drag;
        }
        .progress-bar {
          width: 0%;
          height: 100%;
          background: linear-gradient(90deg, #60a5fa, #a78bfa);
          transition: width 180ms ease;
        }
        .logs {
          flex: 1;
          min-height: 0;
          border-radius: 8px;
          border: 1px solid rgba(255,255,255,0.2);
          background: rgba(17, 24, 39, 0.85);
          padding: 10px;
          overflow: auto;
          font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
          font-size: 12px;
          line-height: 1.45;
          -webkit-app-region: no-drag;
          white-space: pre-wrap;
          word-break: break-word;
        }
        .line { margin-bottom: 4px; }
        .line.info { color: rgba(255,255,255,0.86); }
        .line.error { color: #fca5a5; }
      </style>
    </head>
    <body>
      <div class="card">
        <div class="header">
          <div class="spinner"></div>
          <div id="status" class="status">Initializing...</div>
        </div>
        <div class="progress-row">
          <div id="progressText">Step 0/6</div>
          <div id="progressPercent">0%</div>
        </div>
        <div class="progress-track">
          <div id="progressBar" class="progress-bar"></div>
        </div>
        <div id="logs" class="logs"></div>
      </div>

      <script>
        const statusEl = document.getElementById('status');
        const progressTextEl = document.getElementById('progressText');
        const progressPercentEl = document.getElementById('progressPercent');
        const progressBarEl = document.getElementById('progressBar');
        const logsEl = document.getElementById('logs');

        function appendLog(entry) {
          if (!entry || !entry.message) return;

          const line = document.createElement('div');
          line.className = 'line ' + (entry.level === 'error' ? 'error' : 'info');

          const time = entry.timestamp
            ? new Date(entry.timestamp).toLocaleTimeString()
            : new Date().toLocaleTimeString();

          line.textContent = '[' + time + '] ' + entry.message;
          logsEl.appendChild(line);
          logsEl.scrollTop = logsEl.scrollHeight;
        }

        if (window.electron && typeof window.electron.onStartupLog === 'function') {
          window.electron.onStartupLog((entry) => appendLog(entry));
        }

        if (window.electron && typeof window.electron.onStartupStatus === 'function') {
          window.electron.onStartupStatus((payload) => {
            if (payload && payload.message) {
              statusEl.textContent = payload.message;
              if (payload.failed) {
                statusEl.style.color = '#fca5a5';
              }
            }

            if (payload && Number.isFinite(payload.step) && Number.isFinite(payload.total) && payload.total > 0) {
              progressTextEl.textContent = 'Step ' + payload.step + '/' + payload.total;
            }

            if (payload && Number.isFinite(payload.percent)) {
              progressPercentEl.textContent = payload.percent + '%';
              progressBarEl.style.width = payload.percent + '%';
            }
          });
        }
      </script>
    </body>
    </html>
  `)}`);

  splashWindow.webContents.on('did-finish-load', () => {
    sendToSplash('startup:status', currentStartupStatus);
    for (const entry of startupLogs) {
      sendToSplash('startup:log', entry);
    }
  });
}

function startJavaBackend(port, encryptionKey, runtimeInfo) {
  const javaBin = process.platform === 'win32' ? 'java.exe' : 'java';
  const javaExe = path.join(jrePath, 'bin', javaBin);

  const args = [
    '-jar', jarPath,
    `--spring.profiles.active=sqlite`,
    `--server.port=${port}`,
    `--jaguarclaw.config-dir=${dataDir}`,
    `--jaguarclaw.webapp-dir=${webappPath}`,
    `--spring.datasource.url=jdbc:sqlite:${dbPath}`,
    `--tools.workspace=${workspacePath}`,
    `--skills.user-dir=${skillsDir}`,
    `--skills.builtin-dir=${builtinSkillsDir}`,
  ];
  if (runtimeInfo && runtimeInfo.enabled && runtimeInfo.home) {
    args.push('--tools.runtime.enabled=true');
    args.push(`--tools.runtime.home=${runtimeInfo.home}`);
    if (runtimeInfo.browser && runtimeInfo.browser.agentBrowserPath) {
      args.push(`--tools.runtime.agent-browser-executable-path=${runtimeInfo.browser.agentBrowserPath}`);
    }
    if (runtimeInfo.browser && runtimeInfo.browser.chromiumPath) {
      args.push(`--tools.runtime.chromium-executable-path=${runtimeInfo.browser.chromiumPath}`);
    }
    if (runtimeInfo.browser && runtimeInfo.browser.chromiumHome) {
      args.push(`--tools.runtime.chromium-home=${runtimeInfo.browser.chromiumHome}`);
    }
  } else {
    args.push('--tools.runtime.enabled=false');
  }

  // 设置环境变量
  const env = {
    ...process.env,
    NODE_CONSOLE_ENCRYPTION_KEY: encryptionKey,
  };
  if (runtimeInfo && runtimeInfo.enabled && runtimeInfo.home) {
    env.TOOLS_RUNTIME_ENABLED = 'true';
    env.TOOLS_RUNTIME_HOME = runtimeInfo.home;
    env.JAGUAR_RUNTIME_HOME = runtimeInfo.home;
    env.AGENT_BROWSER_PROVIDER = 'kernel';
    env.AGENT_BROWSER_SKIP_INSTALL = '1';
    if (runtimeInfo.browser && runtimeInfo.browser.agentBrowserPath) {
      env.AGENT_BROWSER_EXECUTABLE_PATH = runtimeInfo.browser.agentBrowserPath;
    }
    if (runtimeInfo.browser && runtimeInfo.browser.chromiumPath) {
      env.AGENT_BROWSER_CHROMIUM_PATH = runtimeInfo.browser.chromiumPath;
    }
    if (runtimeInfo.browser && runtimeInfo.browser.chromiumHome) {
      env.AGENT_BROWSER_KERNEL_HOME = runtimeInfo.browser.chromiumHome;
    }
    env.AGENT_BROWSER_HOME = runtimeInfo.home;
  } else {
    env.TOOLS_RUNTIME_ENABLED = 'false';
    delete env.TOOLS_RUNTIME_HOME;
    delete env.JAGUAR_RUNTIME_HOME;
    delete env.AGENT_BROWSER_EXECUTABLE_PATH;
    delete env.AGENT_BROWSER_CHROMIUM_PATH;
    delete env.AGENT_BROWSER_KERNEL_HOME;
    delete env.AGENT_BROWSER_HOME;
    delete env.AGENT_BROWSER_PROVIDER;
    delete env.AGENT_BROWSER_SKIP_INSTALL;
  }

  appendStartupLog('info', `Starting backend process on port ${port}`);
  appendStartupLog('info', `Java executable: ${javaExe}`);
  appendStartupLog('info', `Startup log file: ${startupLogPath}`);
  appendStartupLog('info', `Desktop log file: ${desktopLogPath}`);
  appendStartupLog('info', `Backend bridge log file: ${backendBridgeLogPath}`);
  publishStartupStatus('Starting backend server...', 'backend');

  javaProcess = spawn(javaExe, args, {
    env: env,
    stdio: ['ignore', 'pipe', 'pipe'],
    windowsHide: true,
  });

  javaProcess.stdout.on('data', (data) => {
    const message = data.toString();
    process.stdout.write(`[Java] ${message}`);
    appendBackendBridgeLog('info', message);
    appendStartupLog('info', `[backend] ${message}`);
  });

  javaProcess.stderr.on('data', (data) => {
    const message = data.toString();
    process.stderr.write(`[Java] ${message}`);
    appendBackendBridgeLog('error', message);
    appendStartupLog('error', `[backend] ${message}`);
  });

  javaProcess.on('exit', (code) => {
    appendStartupLog(code === 0 ? 'info' : 'error', `Backend process exited with code ${code}`);
    const hadProcess = javaProcess !== null;
    javaProcess = null;

    // If Java crashes unexpectedly while window is open, show error and quit
    if (mainWindow && !mainWindow.isDestroyed() && code !== 0) {
      const errorMsg = extractErrorMessage();
      dialog.showMessageBox(mainWindow, {
        type: 'error',
        title: 'Backend Crashed',
        message: `The backend service has crashed unexpectedly (exit code: ${code}).`,
        detail: `Recent logs:\n${errorMsg}`,
        buttons: ['Quit', 'View Logs'],
        defaultId: 0,
      }).then(({ response }) => {
        if (response === 1) {
          const { shell } = require('electron');
          shell.openPath(logsDir);
        }
        app.quit();
      });
    } else if (hadProcess && !mainWindow) {
      // Java 进程在主窗口创建之前退出（启动阶段失败）
      // 这种情况已经在 waitForHealth 中处理了，这里不需要额外操作
      publishStartupStatus('Backend startup failed.', null, true);
      appendStartupLog('error', 'Backend process exited during startup phase');
    }
  });
}

/**
 * 显示启动错误对话框
 * @param {string} title - 错误标题
 * @param {string} message - 错误消息
 * @param {string} details - 详细错误信息（可选）
 */
function showStartupError(title, message, details = null) {
  const options = {
    type: 'error',
    title: title,
    message: message,
    buttons: ['Quit', 'View Logs'],
    defaultId: 0,
  };

  if (details) {
    options.detail = `${details}\n\nStartup log file: ${startupLogPath}`;
  } else {
    options.detail = `Startup log file: ${startupLogPath}`;
  }

  dialog.showMessageBox(options).then(({ response }) => {
    if (response === 1) {
      // 打开日志目录
      const { shell } = require('electron');
      shell.openPath(logsDir);
    }
    app.quit();
  });
}

/**
 * 从错误日志中提取关键错误信息
 */
function extractErrorMessage() {
  if (javaErrorLogs.length === 0) {
    return 'No error logs captured. The backend may have failed to start.';
  }

  // 查找关键错误信息
  const errorPatterns = [
    /Exception.*?:/,
    /Error.*?:/,
    /Failed to.*?:/,
    /Could not.*?:/,
    /Unable to.*?:/,
  ];

  for (const log of [...javaErrorLogs].reverse()) {
    for (const pattern of errorPatterns) {
      const match = log.match(pattern);
      if (match) {
        // 提取错误信息及后面的一行
        const lines = log.split('\n');
        return lines.slice(0, 3).join('\n').trim();
      }
    }
  }

  // 如果没有匹配到特定错误，返回最后几条日志
  return javaErrorLogs.slice(-5).join('\n').trim();
}

function waitForHealth(port, timeoutMs = 60000) {
  const startTime = Date.now();
  const interval = 1000;
  let attempts = 0;

  return new Promise((resolve, reject) => {
    function check() {
      attempts += 1;
      const elapsed = Date.now() - startTime;

      if (!javaProcess) {
        const errorMsg = extractErrorMessage();
        return reject(new Error(`Backend process exited before health check passed.\n\nLast logs:\n${errorMsg}`));
      }

      if (elapsed > timeoutMs) {
        const errorMsg = extractErrorMessage();
        return reject(new Error(`Backend health check timed out after ${Math.floor(elapsed / 1000)}s.\n\nLast logs:\n${errorMsg}`));
      }

      const req = http.get(`http://localhost:${port}/actuator/health`, (res) => {
        if (res.statusCode === 200) {
          publishStartupStatus('Backend ready, opening application...', 'ready');
          appendStartupLog('info', 'Health check passed');
          resolve();
        } else {
          if (attempts === 1 || attempts % 5 === 0) {
            const msg = `Waiting for backend health check... (${Math.floor(elapsed / 1000)}s)`;
            publishStartupStatus(msg, 'health');
            appendStartupLog('info', msg);
          }
          setTimeout(check, interval);
        }
      });

      req.on('error', (err) => {
        if (attempts === 1 || attempts % 5 === 0) {
          appendStartupLog('info', `Health check retry: ${err.message}`);
        }
        setTimeout(check, interval);
      });

      req.setTimeout(2000, () => {
        req.destroy();
        setTimeout(check, interval);
      });
    }

    check();
  });
}

function createMainWindow(port) {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 800,
    minWidth: 800,
    minHeight: 600,
    title: APP_DISPLAY_NAME,
    icon: path.join(resourcesPath, 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      nodeIntegration: false,
      contextIsolation: true,
    },
  });

  // Hide the default menu bar
  mainWindow.setMenuBarVisibility(false);

  // Prevent the HTML <title> tag from overriding the window title set by app identity
  mainWindow.on('page-title-updated', (event) => {
    event.preventDefault();
  });

  mainWindow.loadURL(`http://localhost:${port}`);

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

function killJavaProcess() {
  if (!javaProcess) return;

  appendDesktopLog('info', `Stopping backend process ${javaProcess.pid}`);

  try {
    // Windows: use taskkill to kill the process tree
    execSync(`taskkill /pid ${javaProcess.pid} /f /t`, { stdio: 'ignore' });
  } catch {
    // Fallback: try SIGTERM
    try {
      javaProcess.kill('SIGTERM');
    } catch {
      // Process already dead
    }
  }
  javaProcess = null;
}

function setupAutoUpdater() {
  if (!app.isPackaged) {
    appendDesktopLog('info', 'Skipping auto-updater in development mode');
    return;
  }

  autoUpdater.autoDownload = false;
  autoUpdater.autoInstallOnAppQuit = true;

  autoUpdater.on('update-available', (info) => {
    appendDesktopLog('info', `Update available: ${info.version}`);
    dialog
      .showMessageBox(mainWindow, {
        type: 'info',
        title: 'Update Available',
        message: `A new version ${info.version} is available. Download now?`,
        buttons: ['Download', 'Later'],
        defaultId: 0,
        cancelId: 1,
      })
      .then(({ response }) => {
        if (response === 0) {
          appendDesktopLog('info', `Downloading update ${info.version}`);
          autoUpdater.downloadUpdate();
        } else {
          appendDesktopLog('info', `Update ${info.version} postponed by user`);
        }
      });
  });

  autoUpdater.on('update-downloaded', () => {
    appendDesktopLog('info', 'Update downloaded and ready to install');
    dialog
      .showMessageBox(mainWindow, {
        type: 'info',
        title: 'Update Ready',
        message: 'Update downloaded. It will be installed when you close the app.',
        buttons: ['Restart Now', 'Later'],
        defaultId: 0,
        cancelId: 1,
      })
      .then(({ response }) => {
        if (response === 0) {
          appendDesktopLog('info', 'Installing downloaded update now');
          autoUpdater.quitAndInstall();
        } else {
          appendDesktopLog('info', 'Update installation deferred until app quit');
        }
      });
  });

  autoUpdater.on('error', (err) => {
    appendDesktopLog('error', `Auto-updater error: ${err.message}`);
  });

  setTimeout(() => {
    appendDesktopLog('info', 'Checking for application updates');
    autoUpdater.checkForUpdates();
  }, 5000);
}

// IPC Handlers
ipcMain.handle('dialog:selectFolder', async () => {
  const result = await dialog.showOpenDialog(mainWindow, {
    properties: ['openDirectory']
  });

  if (result.canceled) {
    return null;
  }

  // 返回选中的文件夹路径
  return result.filePaths[0];
});

ipcMain.handle('runtime:getConfig', async () => {
  const mode = getConfiguredRuntimeMode();
  const probe = probeBundledRuntimeAvailability();
  const fromCurrentRun = currentRuntimeInfo && currentRuntimeInfo.mode === mode;
  const effectiveMode = fromCurrentRun
    ? currentRuntimeInfo.effectiveMode
    : (mode === 'local' ? 'local' : (probe.available ? 'bundled' : 'local'));

  return {
    mode,
    effectiveMode,
    bundledAvailable: fromCurrentRun ? !!currentRuntimeInfo.bundledAvailable : probe.available,
    bundledSource: fromCurrentRun ? currentRuntimeInfo.bundledSource : probe.source,
    bundledHome: fromCurrentRun
      ? (currentRuntimeInfo.enabled ? currentRuntimeInfo.home : null)
      : (probe.home || null),
    browserReady: fromCurrentRun ? !!currentRuntimeInfo.browser?.ready : false,
    agentBrowserPath: fromCurrentRun ? (currentRuntimeInfo.browser?.agentBrowserPath || null) : null,
    chromiumPath: fromCurrentRun ? (currentRuntimeInfo.browser?.chromiumPath || null) : null,
  };
});

ipcMain.handle('runtime:setMode', async (_event, payload) => {
  const requested = payload && typeof payload.mode === 'string' ? payload.mode : '';
  if (!RUNTIME_MODES.includes(requested)) {
    throw new Error(`Invalid runtime mode: ${requested || '<empty>'}`);
  }
  const mode = setConfiguredRuntimeMode(requested);
  return { mode, restartRequired: true };
});

ipcMain.handle('app:restart', async () => {
  setTimeout(() => {
    app.relaunch();
    app.exit(0);
  }, 100);
  return { accepted: true };
});

ipcMain.handle('app:getInfo', async () => ({
  name: APP_DISPLAY_NAME,
  version: app.getVersion(),
  paths: {
    appData: appDataPath,
    data: dataDir,
    workspace: workspacePath,
    skills: skillsDir,
    logs: logsDir,
    startupLog: startupLogPath,
    desktopLog: desktopLogPath,
    backendBridgeLog: backendBridgeLogPath,
  },
}));

ipcMain.handle('app:openPath', async (_event, payload) => {
  const target = payload && typeof payload.target === 'string' ? payload.target : '';
  const targetMap = {
    appData: appDataPath,
    data: dataDir,
    workspace: workspacePath,
    skills: skillsDir,
    logs: logsDir,
    startupLog: startupLogPath,
    desktopLog: desktopLogPath,
    backendBridgeLog: backendBridgeLogPath,
  };
  const resolved = targetMap[target];
  if (!resolved) {
    throw new Error(`Unsupported path target: ${target || '<empty>'}`);
  }
  const { shell } = require('electron');
  const result = await shell.openPath(resolved);
  return { target, path: resolved, success: !result, error: result || null };
});

app.whenReady().then(async () => {
  try {
    // 确保目录存在
    ensureDirectories();

    // 重置内存日志缓存
    javaErrorLogs = [];
    startupLogs = [];

    appendDesktopLog('info', 'Electron app is ready');
    appendStartupLog('info', `=== ${APP_DISPLAY_NAME} startup ===`);
    appendStartupLog('info', `Log directory: ${logsDir}`);
    publishStartupStatus('Preparing startup environment...', 'prepare');

    const runtimeMode = getConfiguredRuntimeMode();
    appendStartupLog('info', `Runtime mode preference: ${runtimeMode}`);
    publishStartupStatus('Preparing runtime mode...', 'runtime');
    const runtimeInfo = resolveRuntimeInfo(runtimeMode);
    currentRuntimeInfo = runtimeInfo;
    if (runtimeInfo.enabled) {
      appendStartupLog('info', `Bundled runtime enabled: ${runtimeInfo.home} (${runtimeInfo.source})`);
      if (runtimeInfo.browser && runtimeInfo.browser.agentBrowserPath) {
        appendStartupLog('info', `Bundled agent-browser: ${runtimeInfo.browser.agentBrowserPath}`);
      } else {
        appendStartupLog('error', 'Bundled agent-browser executable not found under runtime home');
      }
      if (runtimeInfo.browser && runtimeInfo.browser.chromiumPath) {
        appendStartupLog('info', `Bundled chromium: ${runtimeInfo.browser.chromiumPath}`);
      } else {
        appendStartupLog('error', 'Bundled chromium executable not found under runtime home');
      }
    } else {
      appendStartupLog('info', `Using system runtime (effective mode: ${runtimeInfo.effectiveMode})`);
    }

    // 获取或生成加密密钥
    appendStartupLog('info', 'Loading encryption key...');
    const encryptionKey = getOrCreateEncryptionKey();

    // 创建启动页面
    createSplashWindow();
    publishStartupStatus('Initializing backend startup...', 'setup');
    appendStartupLog('info', 'Splash window is ready');

    // Find available port starting from 18080
    portfinder.basePort = 18080;
    publishStartupStatus('Selecting available service port...', 'port');
    serverPort = await portfinder.getPortPromise();
    appendStartupLog('info', `Using backend port: ${serverPort}`);

    // Start Java backend with encryption key
    startJavaBackend(serverPort, encryptionKey, runtimeInfo);

    // Wait for backend to be ready
    publishStartupStatus('Waiting for backend health check...', 'health');
    await waitForHealth(serverPort);

    // Create main window
    appendStartupLog('info', 'Creating main window');
    createMainWindow(serverPort);
    appendStartupLog('info', 'Startup completed successfully');

    // Close splash
    if (splashWindow && !splashWindow.isDestroyed()) {
      splashWindow.close();
      splashWindow = null;
    }

    // Check for updates
    appendDesktopLog('info', 'Setting up auto-updater');
    setupAutoUpdater();
  } catch (err) {
    appendDesktopLog('error', `Failed to start: ${err && err.stack ? err.stack : (err.message || err)}`);
    appendStartupLog('error', `Startup failed: ${err.message || err}`);
    publishStartupStatus('Startup failed. See error details and logs.', null, true);

    // 显示友好的错误提示
    if (err.message && (err.message.includes('health check timed out') || err.message.includes('exited before health check passed'))) {
      // 健康检查超时，显示详细日志
      showStartupError(
        'Backend Failed to Start',
        `The ${APP_DISPLAY_NAME} backend service failed to start properly.`,
        err.message
      );
    } else if (err.message && err.message.includes('EADDRINUSE')) {
      // 端口被占用
      showStartupError(
        'Port Already in Use',
        `Port ${serverPort} is already being used by another application.`,
        'Please close the other application or change the port in settings.'
      );
    } else {
      // 其他错误
      showStartupError(
        'Startup Error',
        `Failed to start ${APP_DISPLAY_NAME}. Please check the logs for more details.`,
        err.message || 'Unknown error'
      );
    }

    // 清理 Java 进程
    killJavaProcess();
  }
});

app.on('window-all-closed', () => {
  appendDesktopLog('info', 'All windows closed; shutting down application');
  killJavaProcess();
  app.quit();
});

app.on('before-quit', () => {
  appendDesktopLog('info', 'Application is quitting');
  killJavaProcess();
});
