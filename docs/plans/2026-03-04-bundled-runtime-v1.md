# Bundled Runtime V1 (Windows-First) Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a bundled Node/Python runtime execution path so shell tools and skill binary checks work without requiring user-level Node/Python installation.

**Architecture:** Add runtime configuration under `tools.runtime`, implement a `BundledRuntimeService` to resolve runtime home/bin paths and inject environment variables, then integrate it into sync (`shell`) and async (`shell_start`) process execution. Update skill gating binary detection to check bundled runtime binaries before PATH checks.

**Tech Stack:** Java 24, Spring Boot configuration properties, JUnit 5 unit tests.

---

### Task 1: Add runtime configuration model

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/tools/ToolsProperties.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/jaguarliu/ai/tools/runtime/BundledRuntimeServiceTest.java`

**Step 1: Write the failing test**

Create `BundledRuntimeServiceTest` asserting:
- runtime disabled by default
- runtime enabled with configured home/bin-paths
- OS-default bin paths are derived when `bin-paths` is empty

**Step 2: Run test to verify it fails**

Run: `mvn -Dtest=BundledRuntimeServiceTest test`
Expected: FAIL because service does not exist.

**Step 3: Write minimal implementation**

- Add nested `RuntimeProperties` to `ToolsProperties`:
  - `enabled` (default `false`)
  - `home` (default empty)
  - `binPaths` (list, default empty)
- Add `tools.runtime` section in `application.yml` with env-overridable defaults.

**Step 4: Run test to verify it passes**

Run: `mvn -Dtest=BundledRuntimeServiceTest test`
Expected: PASS.

**Step 5: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/tools/ToolsProperties.java src/main/resources/application.yml src/test/java/com/jaguarliu/ai/tools/runtime/BundledRuntimeServiceTest.java
# commit later as part of grouped feature commit
```

### Task 2: Implement bundled runtime resolver/injector

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/tools/runtime/BundledRuntimeService.java`
- Test: `src/test/java/com/jaguarliu/ai/tools/runtime/BundledRuntimeServiceTest.java`

**Step 1: Write the failing test**

Add tests for:
- `applyToEnvironment` prefixes PATH/Path with bundled runtime bin dirs
- `hasBundledBinary("python")` / `hasBundledBinary("node")` detection via temporary fake executables

**Step 2: Run test to verify it fails**

Run: `mvn -Dtest=BundledRuntimeServiceTest test`
Expected: FAIL due missing logic.

**Step 3: Write minimal implementation**

Implement service methods:
- `isEnabled()`
- `resolveRuntimeHome()`
- `resolveBinPaths()`
- `applyToEnvironment(Map<String,String>)`
- `hasBundledBinary(String)`

**Step 4: Run test to verify it passes**

Run: `mvn -Dtest=BundledRuntimeServiceTest test`
Expected: PASS.

**Step 5: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/tools/runtime/BundledRuntimeService.java src/test/java/com/jaguarliu/ai/tools/runtime/BundledRuntimeServiceTest.java
# commit later as part of grouped feature commit
```

### Task 3: Inject runtime env into shell execution

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/tools/builtin/shell/ShellTool.java`
- Modify: `src/main/java/com/jaguarliu/ai/tools/builtin/shell/process/ProcessManager.java`
- Test: `src/test/java/com/jaguarliu/ai/tools/builtin/ShellToolTest.java`

**Step 1: Write the failing test**

Add `ShellToolTest` case:
- when runtime enabled with fake bin path, `script_content` output of PATH contains the fake bin path.

**Step 2: Run test to verify it fails**

Run: `mvn -Dtest=ShellToolTest test`
Expected: FAIL because PATH injection not implemented.

**Step 3: Write minimal implementation**

- Inject `BundledRuntimeService` into `ShellTool` and `ProcessManager`
- Call `bundledRuntimeService.applyToEnvironment(pb.environment())` before process start.

**Step 4: Run test to verify it passes**

Run: `mvn -Dtest=ShellToolTest test`
Expected: PASS.

**Step 5: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/tools/builtin/shell/ShellTool.java src/main/java/com/jaguarliu/ai/tools/builtin/shell/process/ProcessManager.java src/test/java/com/jaguarliu/ai/tools/builtin/ShellToolTest.java
# commit later as part of grouped feature commit
```

### Task 4: Update skill gating binary checks

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/skills/gating/SkillGatingService.java`
- Modify: `src/test/java/com/jaguarliu/ai/skills/gating/SkillGatingServiceTest.java`

**Step 1: Write the failing test**

Add test to verify:
- If bundled runtime reports binary present, gating `bins` check passes even if PATH check might fail.

**Step 2: Run test to verify it fails**

Run: `mvn -Dtest=SkillGatingServiceTest test`
Expected: FAIL because service currently only checks PATH.

**Step 3: Write minimal implementation**

- Inject optional `BundledRuntimeService` into `SkillGatingService`
- In `isBinaryExists`, check bundled runtime first, then fallback to PATH.

**Step 4: Run test to verify it passes**

Run: `mvn -Dtest=SkillGatingServiceTest test`
Expected: PASS.

**Step 5: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/skills/gating/SkillGatingService.java src/test/java/com/jaguarliu/ai/skills/gating/SkillGatingServiceTest.java
# commit later as part of grouped feature commit
```

### Task 5: Verify feature behavior end-to-end at unit level

**Files:**
- Test: `src/test/java/com/jaguarliu/ai/tools/runtime/BundledRuntimeServiceTest.java`
- Test: `src/test/java/com/jaguarliu/ai/tools/builtin/ShellToolTest.java`
- Test: `src/test/java/com/jaguarliu/ai/skills/gating/SkillGatingServiceTest.java`

**Step 1: Run targeted test suite**

Run:
- `mvn -Dtest=BundledRuntimeServiceTest test`
- `mvn -Dtest=ShellToolTest test`
- `mvn -Dtest=SkillGatingServiceTest test`

Expected: all PASS.

**Step 2: Run combined command**

Run: `mvn -Dtest=BundledRuntimeServiceTest,ShellToolTest,SkillGatingServiceTest test`
Expected: PASS with no regressions in touched modules.

**Step 3: Commit**

```bash
git add docs/plans/2026-03-04-bundled-runtime-v1.md
git commit -m "feat(tools): add bundled runtime execution path for shell and skill gating"
```

### Task 6: Add desktop bundled-runtime bootstrap (runtime.zip extraction)

**Files:**
- Modify: `electron/main.js`

**Step 1: Add startup bootstrap logic**

- Detect bundled runtime source from:
  - `process.resourcesPath/runtime.zip` (preferred)
  - `app.asar/resources/runtime.zip` (fallback)
  - `runtime/` directory (dev fallback)
- Resolve runtime version via `runtime.version` file or zip metadata fallback.
- On first launch extract `runtime.zip` into:
  - `%APPDATA%/JaguarClaw/runtime/<version>/`
- Write `.ready.marker` under extracted runtime directory.

**Step 2: Integrate with backend startup**

- Pass runtime settings to Java backend:
  - `--tools.runtime.enabled=true`
  - `--tools.runtime.home=<extracted path>`
- Mirror with env vars:
  - `TOOLS_RUNTIME_ENABLED=true`
  - `TOOLS_RUNTIME_HOME=<path>`
  - `JAGUAR_RUNTIME_HOME=<path>`

**Step 3: Surface startup status**

- Add dedicated splash progress stage for runtime preparation.
- Log runtime source and prepared path.

### Task 7: Wire optional runtime bundle into Electron build scripts

**Files:**
- Modify: `electron/scripts/build.js`
- Modify: `electron/scripts/build-local.js`

**Step 1: Copy optional runtime bundle**

- If `runtime/runtime.zip` exists at project root:
  - copy to `electron/resources/runtime.zip`
  - copy optional `runtime/runtime.version` to `electron/resources/runtime.version`
- If not present:
  - remove stale runtime bundle files from `electron/resources/`
  - continue build (runtime bundle remains optional)

**Step 2: Verify script syntax**

Run:
- `node --check electron/main.js`
- `node --check electron/scripts/build.js`
- `node --check electron/scripts/build-local.js`

Expected: PASS (no syntax errors).

### Task 8: Add runtime content baseline and packager script

**Files:**
- Create: `runtime/manifest.json`
- Create: `runtime/requirements.txt`
- Create: `runtime/README.md`
- Create: `electron/scripts/package-runtime.js`
- Modify: `electron/package.json`
- Modify: `electron/scripts/build.js`
- Modify: `electron/scripts/build-local.js`
- Modify: `.gitignore`

**Step 1: Define bundled package baseline**

- Add Python data/file-processing baseline package list in `runtime/manifest.json` and `runtime/requirements.txt`.
- Keep Node global packages empty by default (built-in Node only).

**Step 2: Implement runtime packager**

- Create `package-runtime.js` to:
  - validate `runtime/staging` structure and required binaries,
  - produce `runtime/runtime.zip`,
  - write `runtime/runtime.version` from manifest version.

**Step 3: Wire build scripts**

- Add `npm run package-runtime` in Electron scripts.
- In desktop build scripts, auto-package runtime when `runtime/staging` exists.
- Continue treating runtime as optional if no bundle exists.

**Step 4: Verify**

Run:
- `node --check electron/scripts/package-runtime.js`
- `node --check electron/scripts/build.js`
- `node --check electron/scripts/build-local.js`

### Task 9: Fully automate runtime download + staging assembly

**Files:**
- Create: `electron/scripts/prepare-runtime.js`
- Modify: `electron/package.json`
- Modify: `electron/scripts/build.js`
- Modify: `electron/scripts/build-local.js`
- Modify: `runtime/README.md`
- Modify: `.gitignore`

**Step 1: Implement runtime prepare script**

- Download official Node and Python embeddable archives based on manifest version spec.
- Extract archives to `runtime/staging/node` and `runtime/staging/python`.
- Bootstrap pip and install packages from `runtime/requirements.txt`.
- Persist prepared marker for idempotent reruns.

**Step 2: Wire to build flows**

- Add `npm run prepare-runtime`.
- In build scripts, auto-run prepare step when both `runtime/staging` and `runtime.zip` are absent.
- Keep runtime still optional (build can proceed without runtime bundle if prepare is intentionally skipped).

**Step 3: Verify**

Run:
- `node --check electron/scripts/prepare-runtime.js`
- `node electron/scripts/prepare-runtime.js --dry-run`
