# Agent Browser Desktop 内置化 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 让桌面端在不依赖用户全局 PATH 的情况下可直接使用 `agent-browser`，并支持按 agent/session 持久化登录态与可观测诊断。

**Architecture:** 采用“应用内置运行时 + 后端统一解析二进制 + Shell 注入 profile 环境变量”的方案。Electron 负责准备/注入 runtime，Java 侧负责 gating 和命令执行环境一致性，最终让 Web 端与桌面端行为一致。V1 先落地本地内置执行链路；远端 Browserbase/Browserless 作为后续可选 provider。

**Tech Stack:** Electron (Node.js scripts), Spring Boot (Java), JUnit 5, existing `BundledRuntimeService` / `SkillGatingService` / `ShellTool`.

---

## 0. 结论先行（选型）

推荐分两层实现：

1. `V1`（本次实现）
- 内置 `agent-browser` 可执行文件到 runtime bundle。
- 启动后端时统一注入 PATH，可过 skill gating。
- 为每个 agent/session 自动注入独立 profile 目录，保留登录态。
- 提供 availability 诊断（缺 browser kernel / 缺 binary / 权限问题）。

2. `V1.5`（可选增强）
- 首次使用自动执行 `agent-browser install`（或内置 Chromium 包）以免人工安装 kernel。

3. `V2`（可选扩展）
- provider 抽象：本地内置（默认）/ Browserbase / Browserless。

---

### Task 1: Runtime Manifest 与打包入口扩展（把 agent-browser 纳入 runtime）

**Files:**
- Modify: `runtime/manifest.json`
- Modify: `runtime/README.md`
- Modify: `electron/scripts/prepare-runtime.js`
- Modify: `electron/scripts/package-runtime.js`
- Test: `electron/scripts` 下新增脚本级 smoke 校验（如 `prepare-runtime --dry-run` CI step）

**Step 1: 写失败验证（打包前置校验）**
- 在 `package-runtime.js` 增加校验：若 manifest 声明了 `agentBrowser.bin`，但 staging 中不存在，则报错。

**Step 2: 运行验证确保失败**
- Run: `node electron/scripts/package-runtime.js --src runtime/staging`
- Expected: 在未放入 `agent-browser` 时失败并提示缺失路径。

**Step 3: 最小实现**
- `manifest.json` 增加 `agentBrowser` 段（version/bin）。
- `prepare-runtime.js` 增加下载 agent-browser 二进制到 `runtime/staging/bin`。
- `package-runtime.js` 读取并校验 `agentBrowser.bin`。
- `runtime/README.md` 更新目录结构与打包说明。

**Step 4: 回归验证**
- Run: `node electron/scripts/prepare-runtime.js --dry-run`
- Run: `node electron/scripts/package-runtime.js`
- Expected: 生成 `runtime/runtime.zip` 且 summary 包含 agent-browser 信息。

**Step 5: Commit**
```bash
git add runtime/manifest.json runtime/README.md electron/scripts/prepare-runtime.js electron/scripts/package-runtime.js
git commit -m "feat(runtime): bundle agent-browser binary into runtime package"
```

---

### Task 2: Electron 启动链路注入 browser runtime 信息

**Files:**
- Modify: `electron/main.js`

**Step 1: 写失败验证**
- 增加启动日志断言（开发模式）: 记录 `TOOLS_RUNTIME_HOME` 与 `AGENT_BROWSER_*` 关键 env 是否存在。

**Step 2: 运行验证确保失败**
- Run: 桌面端启动流程
- Expected: 当前不会输出 browser runtime 就绪信息。

**Step 3: 最小实现**
- 在 `resolveRuntimeInfo` 结果中加入 browser 相关路径（例如 runtime 下 `bin/agent-browser(.cmd|.exe)`）。
- `startJavaBackend` 时追加环境变量：
  - `AGENT_BROWSER_EXECUTABLE_PATH`（如需要）
  - `AGENT_BROWSER_PROVIDER=kernel`（默认）
- 保持现有 `TOOLS_RUNTIME_*` 注入逻辑不破坏。

**Step 4: 回归验证**
- Run: 启动桌面端，检查 startup log。
- Expected: 后端进程拿到 browser runtime 环境变量。

**Step 5: Commit**
```bash
git add electron/main.js
git commit -m "feat(electron): pass bundled agent-browser runtime env to backend"
```

---

### Task 3: Java 侧 runtime binary 解析增强（显式路径优先）

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/tools/ToolsProperties.java`
- Modify: `src/main/java/com/jaguarliu/ai/tools/runtime/BundledRuntimeService.java`
- Test: `src/test/java/com/jaguarliu/ai/tools/runtime/BundledRuntimeServiceTest.java`

**Step 1: 写失败测试**
- 测试目标：`resolve/hasBundledBinary("agent-browser")` 在配置了 browser bin path 后返回 true。
- 测试目标：binary 解析优先级 `explicit path > bundled bins > PATH`。

**Step 2: 运行失败测试**
- Run: `./mvnw -Dtest=BundledRuntimeServiceTest test`
- Expected: 新增用例失败。

**Step 3: 最小实现**
- 在 `ToolsProperties.RuntimeProperties` 增加 browser 相关配置（如 `browserBinPaths` 或统一 `binPaths` 约定扩展）。
- 在 `BundledRuntimeService` 增加 `resolveBundledBinary(String)`，返回绝对路径（Optional）。
- 保证 `applyToEnvironment` 注入后 shell 可直接调用 `agent-browser`。

**Step 4: 回归验证**
- Run: `./mvnw -Dtest=BundledRuntimeServiceTest test`
- Expected: 通过。

**Step 5: Commit**
```bash
git add src/main/java/com/jaguarliu/ai/tools/ToolsProperties.java src/main/java/com/jaguarliu/ai/tools/runtime/BundledRuntimeService.java src/test/java/com/jaguarliu/ai/tools/runtime/BundledRuntimeServiceTest.java
git commit -m "feat(runtime): add explicit bundled binary resolution for agent-browser"
```

---

### Task 4: Skill Gating 支持“可解析二进制路径”诊断

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/skills/gating/SkillGatingService.java`
- Modify: `src/main/java/com/jaguarliu/ai/skills/gating/GatingResult.java`
- Test: `src/test/java/com/jaguarliu/ai/skills/gating/SkillGatingServiceTest.java`

**Step 1: 写失败测试**
- 场景：PATH 不含 `agent-browser`，但 bundled runtime 有该 binary 时，`anyBins` 通过。
- 场景：缺 binary 时 failure reason 包含明确诊断建议。

**Step 2: 运行失败测试**
- Run: `./mvnw -Dtest=SkillGatingServiceTest test`
- Expected: 新增用例失败。

**Step 3: 最小实现**
- 使用 `bundledRuntimeService.resolveBundledBinary` 参与判定，而不只做 `hasBundledBinary`。
- 失败信息中区分：
  - binary 缺失
  - binary 存在但不可执行/不可访问（如后续可检测）

**Step 4: 回归验证**
- Run: `./mvnw -Dtest=SkillGatingServiceTest test`
- Expected: 通过。

**Step 5: Commit**
```bash
git add src/main/java/com/jaguarliu/ai/skills/gating/SkillGatingService.java src/main/java/com/jaguarliu/ai/skills/gating/GatingResult.java src/test/java/com/jaguarliu/ai/skills/gating/SkillGatingServiceTest.java
git commit -m "feat(skills): improve agent-browser gating with bundled binary diagnostics"
```

---

### Task 5: Shell 执行自动注入 profile/session（解决登录态留存）

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/tools/builtin/shell/ShellTool.java`
- Modify: `src/main/java/com/jaguarliu/ai/tools/builtin/shell/process/ProcessManager.java`
- Test: `src/test/java/com/jaguarliu/ai/tools/builtin/ShellToolTest.java`

**Step 1: 写失败测试**
- 场景：当命令行调用 `agent-browser ...` 时，环境变量自动包含：
  - `AGENT_BROWSER_SESSION`
  - `AGENT_BROWSER_PROFILE`
- 目录规则：`<appData>/browser-profiles/<agentId>/<sessionId>`（或等价）

**Step 2: 运行失败测试**
- Run: `./mvnw -Dtest=ShellToolTest test`
- Expected: 新增断言失败。

**Step 3: 最小实现**
- 在构建 `ProcessBuilder` 环境时，根据 `ToolExecutionContext` 注入 session/profile 环境变量。
- 若 context 缺失，使用安全默认 profile（隔离目录，不落到系统默认用户目录）。

**Step 4: 回归验证**
- Run: `./mvnw -Dtest=ShellToolTest test`
- Expected: 通过。

**Step 5: Commit**
```bash
git add src/main/java/com/jaguarliu/ai/tools/builtin/shell/ShellTool.java src/main/java/com/jaguarliu/ai/tools/builtin/shell/process/ProcessManager.java src/test/java/com/jaguarliu/ai/tools/builtin/ShellToolTest.java
git commit -m "feat(shell): inject agent-browser session/profile env for persistent auth"
```

---

### Task 6: Skill 文档与可用性提示更新（去掉用户手动安装心智负担）

**Files:**
- Modify: `.jaguarclaw/skills/agent-browser/SKILL.md`
- Modify: `docs/` 下桌面运行时说明文档（按实际已有文档）

**Step 1: 写失败验证**
- 手工检查：桌面端 skill 说明是否仍以“必须手动 npm install”为主。

**Step 2: 运行验证确保失败**
- 打开 skill 页面/文档。
- Expected: 当前文案与内置方案不一致。

**Step 3: 最小实现**
- 文档改为：
  - Desktop 默认使用内置 `agent-browser`。
  - 如不可用，显示诊断路径和自愈步骤。
  - 明确 profile/session 留存规则与清理方法。

**Step 4: 回归验证**
- 手工检查文档一致性。

**Step 5: Commit**
```bash
git add .jaguarclaw/skills/agent-browser/SKILL.md docs
git commit -m "docs(agent-browser): update desktop built-in runtime and profile behavior"
```

---

### Task 7: 桌面端端到端验收与灰度发布

**Files:**
- Modify: `docs/plans/2026-03-06-agent-browser-desktop-implementation.md`（验收记录）
- Optional: 新增 `docs/bugfix/` 验收清单

**Step 1: 写验收清单**
- 场景 A：全新安装，直接运行 agent-browser。
- 场景 B：登录后重启应用，登录态仍在。
- 场景 C：main agent / subagent 隔离。
- 场景 D：桌面端无全局 node/npm/PATH 时仍可用。

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
git commit -m "chore(release): validate desktop agent-browser built-in workflow"
```

---

## Provider 扩展（后续，不阻塞 V1）

1. 新增 provider 配置：`kernel`（默认）、`browserbase`、`browserless`。
2. 将 provider 参数透传到 `agent-browser`（CLI 已支持 `-p/--provider`）。
3. 凭据放入现有安全配置存储，不进入 prompt/log。

---

## 风险与边界

1. Runtime 体积明显增加（内置 Chromium 时尤甚），需在安装包大小与开箱即用之间做策略切换。
2. 浏览器 profile 含敏感 cookie/token，必须落在 appData 私有目录并支持一键清理。
3. 站点风控策略变化会导致偶发失效，需保留 HITL 与重试链路。

---

## 验收标准（Definition of Done）

1. 桌面端在无全局 `agent-browser` 的机器上，skill gating 显示可用。
2. `agent-browser open https://example.com` 在桌面端成功执行。
3. 同一 agent/session 再次访问时登录态可复用；不同 agent 不串号。
4. 不可用时 UI/日志可明确定位原因（binary/kernel/provider/权限）。
5. 相关单元测试与关键冒烟通过。

---

Plan complete and saved to `docs/plans/2026-03-06-agent-browser-desktop-implementation.md`.
