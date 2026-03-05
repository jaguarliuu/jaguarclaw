# Cancel/Run Isolation Fix Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix the bug where canceling run A in a session and then sending unrelated request B still appears to resume A and ignore B.

**Architecture:** Apply a dual fix. Backend enforces stronger cancel semantics (queued run cancels immediately, running run gets fast-path cancellation checks, canceled queued run is skipped before execution). Frontend enforces strict runId isolation for streaming/tool/skill events so stale events from old runs cannot pollute the active run UI.

**Tech Stack:** Spring Boot WebFlux, Reactor, JUnit 5 + Mockito, Vue 3 + TypeScript.

---

### Task 1: Add failing backend tests for cancel semantics

**Files:**
- Create: `src/test/java/com/jaguarliu/ai/gateway/rpc/handler/agent/AgentCancelHandlerTest.java`

**Step 1: Write failing tests**
- queued run: `agent.cancel` should transition run status to `canceled` immediately and not call `CancellationManager.requestCancel`.
- running run: `agent.cancel` should keep status transition to runtime and call `CancellationManager.requestCancel`.

**Step 2: Run test to verify it fails**
- Run: `mvn -Dtest=AgentCancelHandlerTest test`
- Expected: FAIL before implementation.

### Task 2: Implement backend cancel semantics and early skip

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/agent/AgentCancelHandler.java`
- Modify: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/agent/AgentRunHandler.java`
- Modify: `src/main/java/com/jaguarliu/ai/runtime/AgentRuntime.java`
- Modify: `src/main/java/com/jaguarliu/ai/runtime/ToolExecutor.java`

**Step 1: Implement queued/running split in cancel handler**
- queued: `runService.updateStatus(..., CANCELED)` and return success.
- running: keep existing `requestCancel` behavior.

**Step 2: Skip canceled run before execution starts**
- At run execution start, reload run status from DB and return early for `canceled`.

**Step 3: Improve cancellation responsiveness**
- Add cancellation checks during streaming loop and before each tool call.

### Task 3: Fix frontend stale event contamination

**Files:**
- Modify: `jaguarclaw-ui/src/composables/useChat.ts`

**Step 1: Add runId guard for main-run event handlers**
- `assistant.delta`, `tool.confirm_request`, `tool.call`, `tool.result`, `skill.activated`, `file.created` should only mutate main stream when `event.runId === currentRun.id`.

**Step 2: Preserve subagent routing path**
- Keep existing subagent event path unchanged.

### Task 4: Verify

**Step 1: Backend tests**
- Run: `mvn -Dtest=AgentCancelHandlerTest test`

**Step 2: Build/type checks**
- Run: `mvn -DskipTests compile`
- Run: `cd jaguarclaw-ui && npm run type-check`

**Step 3: Manual scenario verification checklist**
- Cancel A while running; immediately send B in same session.
- Ensure old A deltas/tools do not appear in B stream.
- Ensure queued A cancel does not execute later.
