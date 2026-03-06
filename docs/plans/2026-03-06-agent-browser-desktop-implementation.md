# Agent Browser Desktop 内置化 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 桌面端提供开箱即用的浏览器自动化能力，默认内置 `agent-browser` + Chromium kernel，用户无需额外下载安装、配置 PATH、执行首次 `install`。

**Architecture:** 采用“应用内置运行时（CLI + kernel）+ Electron 注入运行时路径 + Java 统一可用性判定 + Shell 自动注入 profile/session”的方案。Web 与桌面保持同一 skill 行为，桌面端默认走本地内核 provider，远端 provider 作为后续扩展。

**Tech Stack:** Electron (Node.js scripts), Spring Boot (Java), JUnit 5, existing `BundledRuntimeService` / `SkillGatingService` / `ShellTool`.

---

## 0. 结论先行（选型）

推荐分两层实现：

1. `V1`（本次实现，必须达成）
- 内置 `agent-browser` 可执行文件到 runtime bundle。
- 内置 Chromium kernel 到 runtime bundle（不是首次运行再下载）。
- 启动后端时统一注入 PATH + kernel 显式路径，可过 skill gating。
- 为每个 agent/session 自动注入独立 profile 目录，保留登录态。
- 提供 availability 诊断（缺 binary / 缺 kernel / 权限问题）。

2. `V1.5`（可选增强）
- 增量更新 runtime 组件（CLI 与 Chromium 可分离升级，减少全量安装包体积波动）。
- 多平台打包矩阵（win-x64 先行，后续扩展到 macOS/Linux）。

3. `V2`（可选扩展）
- provider 抽象：本地内置（默认）/ Browserbase / Browserless。

---

### Task 1: Runtime Manifest 与打包入口扩展（把 CLI + Chromium 一并纳入 runtime）

**Files:**
- Modify: `runtime/manifest.json`
- Modify: `runtime/README.md`
- Modify: `electron/scripts/prepare-runtime.js`
- Modify: `electron/scripts/package-runtime.js`
- Test: `electron/scripts` 下新增脚本级 smoke 校验（如 `prepare-runtime --dry-run` CI step）

**Step 1: 写失败验证（打包前置校验）**
- 在 `package-runtime.js` 增加校验：若 manifest 声明了 `agentBrowser.bin` 或 `chromium.bin`，但 staging 中缺失对应文件，则立即报错。

**Step 2: 运行验证确保失败**
- Run: `node electron/scripts/package-runtime.js --src runtime/staging`
- Expected: 在未放入 `agent-browser`/Chromium 时失败并提示缺失路径。

**Step 3: 最小实现**
- `manifest.json` 增加：
  - `agentBrowser`（version/bin）
  - `chromium`（version/root/bin）
- `prepare-runtime.js` 增加下载与解压：
  - `agent-browser` 到 `runtime/staging/bin`
  - Chromium kernel 到 `runtime/staging/browser/chromium`
- `package-runtime.js` 读取并校验 `agentBrowser/chromium` 路径。
- `runtime/README.md` 更新目录结构与构建说明（明确“零安装内核”）。

**Step 4: 回归验证**
- Run: `node electron/scripts/prepare-runtime.js --dry-run`
- Run: `node electron/scripts/package-runtime.js`
- Expected: 生成 `runtime/runtime.zip` 且 summary 包含 `agent-browser` 与 `chromium` 信息。

**Step 5: Commit**
```bash
git add runtime/manifest.json runtime/README.md electron/scripts/prepare-runtime.js electron/scripts/package-runtime.js
git commit -m "feat(runtime): bundle agent-browser and chromium kernel into runtime package"
```

---

### Task 2: Electron 启动链路注入 browser runtime 信息（强制使用内置 kernel）

**Files:**
- Modify: `electron/main.js`

**Step 1: 写失败验证**
- 增加启动日志断言（开发模式）: 记录 `TOOLS_RUNTIME_HOME` 与 `AGENT_BROWSER_*` 关键 env 是否存在。

**Step 2: 运行验证确保失败**
- Run: 桌面端启动流程
- Expected: 当前不会输出 Chromium kernel 就绪信息。

**Step 3: 最小实现**
- 在 `resolveRuntimeInfo` 结果中加入：
  - `agent-browser` 可执行路径
  - Chromium 可执行路径与 kernel 根目录
- `startJavaBackend` 时追加环境变量：
  - `AGENT_BROWSER_EXECUTABLE_PATH`
  - `AGENT_BROWSER_CHROMIUM_PATH`
  - `AGENT_BROWSER_KERNEL_HOME`
  - `AGENT_BROWSER_PROVIDER=kernel`
  - `AGENT_BROWSER_SKIP_INSTALL=1`（禁止运行时安装逻辑）
- 保持现有 `TOOLS_RUNTIME_*` 注入逻辑不破坏。

**Step 4: 回归验证**
- Run: 启动桌面端，检查 startup log。
- Expected: 后端进程拿到 browser runtime 环境变量，且不触发任何 install 提示。

**Step 5: Commit**
```bash
git add electron/main.js
git commit -m "feat(electron): pass bundled chromium runtime env to backend"
```

---

### Task 3: Java 侧 runtime binary/kernel 解析增强（显式路径优先）

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/tools/ToolsProperties.java`
- Modify: `src/main/java/com/jaguarliu/ai/tools/runtime/BundledRuntimeService.java`
- Test: `src/test/java/com/jaguarliu/ai/tools/runtime/BundledRuntimeServiceTest.java`

**Step 1: 写失败测试**
- 测试目标：`resolve/hasBundledBinary("agent-browser")` 可解析到内置 binary。
- 测试目标：Chromium kernel 路径可解析且可读取。
- 测试目标：解析优先级 `explicit env path > bundled runtime > PATH`。

**Step 2: 运行失败测试**
- Run: `./mvnw -Dtest=BundledRuntimeServiceTest test`
- Expected: 新增用例失败。

**Step 3: 最小实现**
- 在 `ToolsProperties.RuntimeProperties` 增加 browser runtime 相关配置。
- 在 `BundledRuntimeService` 增加：
  - `resolveBundledBinary(String)`（CLI）
  - `resolveBundledChromium()`（kernel）
- 保证 `applyToEnvironment` 后 shell 可直接调用 `agent-browser` 且命中内置 Chromium。

**Step 4: 回归验证**
- Run: `./mvnw -Dtest=BundledRuntimeServiceTest test`
- Expected: 通过。

**Step 5: Commit**
```bash
git add src/main/java/com/jaguarliu/ai/tools/ToolsProperties.java src/main/java/com/jaguarliu/ai/tools/runtime/BundledRuntimeService.java src/test/java/com/jaguarliu/ai/tools/runtime/BundledRuntimeServiceTest.java
git commit -m "feat(runtime): resolve bundled browser binary and chromium kernel paths"
```

---

### Task 4: Skill Gating 支持“binary + kernel”双重诊断

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/skills/gating/SkillGatingService.java`
- Modify: `src/main/java/com/jaguarliu/ai/skills/gating/GatingResult.java`
- Test: `src/test/java/com/jaguarliu/ai/skills/gating/SkillGatingServiceTest.java`

**Step 1: 写失败测试**
- 场景：PATH 不含 `agent-browser`，但 bundled runtime 有 binary + kernel 时，gating 通过。
- 场景：binary 存在但 kernel 缺失时，gating 失败并给出清晰原因。

**Step 2: 运行失败测试**
- Run: `./mvnw -Dtest=SkillGatingServiceTest test`
- Expected: 新增用例失败。

**Step 3: 最小实现**
- gating 判定从 `anyBins` 升级为 `binary-ready && kernel-ready`。
- 失败信息中区分：
  - binary 缺失
  - kernel 缺失
  - 路径存在但不可执行/不可访问

**Step 4: 回归验证**
- Run: `./mvnw -Dtest=SkillGatingServiceTest test`
- Expected: 通过。

**Step 5: Commit**
```bash
git add src/main/java/com/jaguarliu/ai/skills/gating/SkillGatingService.java src/main/java/com/jaguarliu/ai/skills/gating/GatingResult.java src/test/java/com/jaguarliu/ai/skills/gating/SkillGatingServiceTest.java
git commit -m "feat(skills): gate agent-browser on bundled binary and chromium readiness"
```

---

### Task 5: Shell 执行自动注入 profile/session（解决登录态留存）

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/tools/builtin/shell/ShellTool.java`
- Modify: `src/main/java/com/jaguarliu/ai/tools/builtin/shell/process/ProcessManager.java`
- Test: `src/test/java/com/jaguarliu/ai/tools/builtin/ShellToolTest.java`

**Step 1: 写失败测试**
- 场景：调用 `agent-browser ...` 时，环境变量自动包含：
  - `AGENT_BROWSER_SESSION`
  - `AGENT_BROWSER_PROFILE`
  - `AGENT_BROWSER_CHROMIUM_PATH`（来自内置 kernel）
- 目录规则：`<appData>/browser-profiles/<agentId>/<sessionId>`（或等价）。

**Step 2: 运行失败测试**
- Run: `./mvnw -Dtest=ShellToolTest test`
- Expected: 新增断言失败。

**Step 3: 最小实现**
- 在构建 `ProcessBuilder` 环境时，根据 `ToolExecutionContext` 注入 session/profile/kernel 环境变量。
- 若 context 缺失，使用安全默认 profile（隔离目录，不落到系统默认用户目录）。

**Step 4: 回归验证**
- Run: `./mvnw -Dtest=ShellToolTest test`
- Expected: 通过。

**Step 5: Commit**
```bash
git add src/main/java/com/jaguarliu/ai/tools/builtin/shell/ShellTool.java src/main/java/com/jaguarliu/ai/tools/builtin/shell/process/ProcessManager.java src/test/java/com/jaguarliu/ai/tools/builtin/ShellToolTest.java
git commit -m "feat(shell): inject bundled chromium profile/session env for agent-browser"
```

---

### Task 6: Skill 文档与可用性提示更新（移除手动安装流程）

**Files:**
- Modify: `.jaguarclaw/skills/agent-browser/SKILL.md`
- Modify: `docs/skills/agent-browser-dependency-management.md`
- Modify: `docs/` 下桌面运行时说明文档（按实际已有文档）

**Step 1: 写失败验证**
- 手工检查：桌面端 skill 说明是否仍以“npm install / 手工下载”为主。

**Step 2: 运行验证确保失败**
- 打开 skill 页面/文档。
- Expected: 当前文案与“零安装内置内核”不一致。

**Step 3: 最小实现**
- 文档改为：
  - Desktop 默认使用内置 `agent-browser + chromium`。
  - 无需用户手动安装浏览器内核。
  - 若不可用，展示诊断路径与修复步骤（runtime 缺失、权限、损坏重建）。
  - 明确 profile/session 留存规则与清理方法。

**Step 4: 回归验证**
- 手工检查文档一致性。

**Step 5: Commit**
```bash
git add .jaguarclaw/skills/agent-browser/SKILL.md docs/skills/agent-browser-dependency-management.md docs
git commit -m "docs(agent-browser): switch desktop docs to bundled chromium zero-install model"
```

---

### Task 7: 桌面端端到端验收与灰度发布

**Files:**
- Modify: `docs/plans/2026-03-06-agent-browser-desktop-implementation.md`（验收记录）
- Optional: 新增 `docs/bugfix/` 验收清单

**Step 1: 写验收清单**
- 场景 A：全新安装，首次使用即可执行 `agent-browser`，不触发安装。
- 场景 B：离线环境（无外网）首次使用仍可启动 Chromium。
- 场景 C：登录后重启应用，登录态仍在。
- 场景 D：main agent / subagent 登录态隔离。
- 场景 E：桌面端无全局 node/npm/PATH 时仍可用。

**Step 2: 执行验收**
- 在 Windows 打包环境执行真实冒烟。

**Step 3: 最小修正**
- 若失败，仅做阻断缺陷修复（不扩 scope）。

**Step 4: 最终验证**
- Run: `./mvnw test`（或至少相关子集）
- Run: 桌面端手工 E2E
- Expected: 全部通过。

**Step 5: Commit**
```bash
git add docs/plans/2026-03-06-agent-browser-desktop-implementation.md
git commit -m "chore(release): validate bundled chromium desktop browser workflow"
```

---

## Provider 扩展（后续，不阻塞 V1）

1. 新增 provider 配置：`kernel`（默认）、`browserbase`、`browserless`。
2. 将 provider 参数透传到 `agent-browser`（CLI 已支持 `-p/--provider`）。
3. 凭据放入现有安全配置存储，不进入 prompt/log。

---

## 风险与边界

1. Runtime 体积会显著增加（内置 Chromium），需制定安装包体积预算与更新策略。
2. Chromium 版本与目标站点兼容性需持续跟进，建议固定 revision 并支持快速热修。
3. 浏览器 profile 含敏感 cookie/token，必须落在 appData 私有目录并支持一键清理。
4. 站点风控策略变化会导致偶发失效，需保留 HITL 与重试链路。

---

## 验收标准（Definition of Done）

1. 桌面端在无全局 `agent-browser`、无全局 Chrome/Chromium 的机器上，skill gating 仍显示可用。
2. `agent-browser open https://example.com` 在桌面端成功执行，并使用内置 Chromium kernel。
3. 首次使用不需要用户手动安装任何浏览器组件，也不要求 PATH 配置。
4. 同一 agent/session 再次访问时登录态可复用；不同 agent 不串号。
5. 不可用时 UI/日志可明确定位原因（binary/kernel/provider/权限）。
6. 相关单元测试与关键冒烟通过。

---

Plan updated and saved to `docs/plans/2026-03-06-agent-browser-desktop-implementation.md`.
