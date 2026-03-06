# Logging Split Rotation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Split Electron and Spring Boot logs by responsibility and add local file rotation/retention.

**Architecture:** Electron gets a small local log manager responsible for writing, rotating, and pruning `startup`, `desktop`, and `backend-bridge` logs under the app data logs directory. Spring Boot moves from `application.yml` file logging to `logback-spring.xml` with separate rolling appenders for app, RPC, and runtime categories, all under the existing config-dir logs folder.

**Tech Stack:** Electron main process (Node.js), Spring Boot, Logback, JUnit, Maven.

---

### Task 1: Add failing tests for Electron log file routing

**Files:**
- Create: `electron/scripts/test-log-manager.test.js`
- Create: `electron/scripts/lib/log-manager.js`

**Step 1: Write the failing test**
- Cover routing to named files, size rotation trigger, and retention pruning.

**Step 2: Run test to verify it fails**
- Run: `node --test electron/scripts/test-log-manager.test.js`

**Step 3: Write minimal implementation**
- Add a small `createLogManager()` helper with `append(channel, level, message)`.

**Step 4: Run test to verify it passes**
- Run: `node --test electron/scripts/test-log-manager.test.js`

### Task 2: Wire Electron main process to split logs

**Files:**
- Modify: `electron/main.js`
- Reuse: `electron/scripts/lib/log-manager.js`

**Step 1: Write or extend failing test if needed**
- Validate startup log reset behavior becomes startup-session append/rotate behavior instead.

**Step 2: Implement minimal wiring**
- Replace single-file writes with dedicated channels.
- Route Java stdout/stderr to `backend-bridge`.
- Route startup lifecycle to `startup`.
- Route generic desktop lifecycle to `desktop`.

**Step 3: Verify behavior**
- Run targeted Node tests / `node --check electron/main.js`.

### Task 3: Add Spring rolling split log configuration

**Files:**
- Modify: `src/main/resources/application.yml`
- Create: `src/main/resources/logback-spring.xml`

**Step 1: Write failing/verification test or config assertion**
- Add a small resource presence/config test if there is a suitable place.

**Step 2: Implement minimal config**
- Remove single-file-only logging from YAML.
- Add appenders for `app.log`, `rpc.log`, `runtime.log` with 10MB / 7 days / 20 archives.

**Step 3: Verify config**
- Run targeted Maven tests.

### Task 4: Add startup diagnostics for log locations

**Files:**
- Modify: `electron/main.js`

**Step 1: Write minimal failing assertion if testable**
- Ensure startup diagnostics include log file paths.

**Step 2: Implement**
- Print resolved log directory and channel files during startup.

**Step 3: Verify**
- Run targeted tests and syntax checks.

### Task 5: Final verification

**Files:**
- Verify only

**Step 1: Run focused tests**
- `node --test electron/scripts/test-log-manager.test.js`
- `mvn -q -Dtest=SkillGatingServiceTest,BundledRuntimeServiceTest test`
- Add logging-related Maven test once introduced.

**Step 2: Run syntax/config checks**
- `node --check electron/main.js`

**Step 3: Review diff**
- Confirm only logging-related files changed, plus the earlier gating fix already in progress.
