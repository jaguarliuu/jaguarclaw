# React Plan Kernel Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Refactor the agent into a `DIRECT`/`REACT` split and add an in-memory execution-plan kernel so long-running React tasks advance item-by-item instead of drifting or stopping early.

**Architecture:** Keep `DIRECT` as a minimal answer-only path. Move all executable work into a single `REACT` runtime path. Inside `REACT`, initialize a lightweight `ExecutionPlan` in `RunContext`, drive each loop iteration against the current plan item, and upgrade runtime decisions from stop/continue into item-level actions (`continue item`, `item done`, `ask user`, `delegate`, `task done`, `stop`).

**Tech Stack:** Java 21, Spring Boot, Reactor, JUnit 5, Mockito, Maven.

---

### Task 1: Collapse top-level routing to `DIRECT` / `REACT`

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/runtime/TaskRouteMode.java`
- Modify: `src/main/java/com/jaguarliu/ai/runtime/TaskRoutingDecision.java`
- Modify: `src/main/java/com/jaguarliu/ai/runtime/TaskRouter.java`
- Modify: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/agent/AgentRunHandler.java`
- Test: `src/test/java/com/jaguarliu/ai/runtime/TaskRouterTest.java`
- Test: `src/test/java/com/jaguarliu/ai/gateway/rpc/handler/agent/AgentRunWithAgentIdTest.java`

**Steps:**
1. Write/adjust failing tests so router only returns `DIRECT` or `REACT`.
2. Update router schema/prompt/fallbacks to model answer-only vs execution-needed.
3. Update handler switch to route only `DIRECT` and `REACT`.
4. Run focused router/handler tests.

### Task 2: Split context building into direct vs react entrypoints

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/runtime/ContextBuilder.java`
- Test: `src/test/java/com/jaguarliu/ai/runtime/ContextBuilderPolicyTest.java`
- Test: `src/test/java/com/jaguarliu/ai/gateway/rpc/handler/agent/AgentRunWithAgentIdTest.java`

**Steps:**
1. Write/adjust failing tests for `buildDirectResponse(...)` and `buildReactEntry(...)`.
2. Implement explicit builder methods.
3. Keep temporary compatibility wrapper only where needed, but stop using complexity to strip capability from executable tasks.
4. Run focused context-builder tests.

### Task 3: Add in-memory execution plan model

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/runtime/ExecutionPlan.java`
- Create: `src/main/java/com/jaguarliu/ai/runtime/ExecutionPlanStatus.java`
- Create: `src/main/java/com/jaguarliu/ai/runtime/PlanItem.java`
- Create: `src/main/java/com/jaguarliu/ai/runtime/PlanItemStatus.java`
- Create: `src/main/java/com/jaguarliu/ai/runtime/PlanExecutionMode.java`
- Create: `src/main/java/com/jaguarliu/ai/runtime/PlanEngine.java`
- Modify: `src/main/java/com/jaguarliu/ai/runtime/RunContext.java`
- Test: `src/test/java/com/jaguarliu/ai/runtime/PlanEngineTest.java`
- Test: `src/test/java/com/jaguarliu/ai/runtime/RunContextTest.java`

**Steps:**
1. Write failing unit tests for plan initialization and item transitions.
2. Implement the minimal plan model with one active item at a time.
3. Store plan state in `RunContext`.
4. Run focused plan/run-context tests.

### Task 4: Upgrade runtime decisions to item-level actions

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/runtime/DecisionAction.java`
- Modify: `src/main/java/com/jaguarliu/ai/runtime/Decision.java`
- Modify: `src/main/java/com/jaguarliu/ai/runtime/DecisionInput.java`
- Modify: `src/main/java/com/jaguarliu/ai/runtime/DecisionInputFactory.java`
- Modify: `src/main/java/com/jaguarliu/ai/runtime/DecisionEngine.java`
- Modify: `src/main/java/com/jaguarliu/ai/runtime/LlmRuntimeDecisionStage.java`
- Modify: `src/main/java/com/jaguarliu/ai/runtime/OutcomeApplier.java`
- Test: `src/test/java/com/jaguarliu/ai/runtime/DecisionTest.java`
- Test: `src/test/java/com/jaguarliu/ai/runtime/DecisionInputFactoryTest.java`
- Test: `src/test/java/com/jaguarliu/ai/runtime/DecisionEngineTest.java`
- Test: `src/test/java/com/jaguarliu/ai/runtime/LlmRuntimeDecisionStageTest.java`
- Test: `src/test/java/com/jaguarliu/ai/runtime/OutcomeApplierTest.java`

**Steps:**
1. Write failing tests for `ITEM_DONE`, `TASK_DONE`, `ASK_USER`, and `BLOCK_ITEM` behavior.
2. Extend decision payloads with plan-aware summary fields.
3. Update verifier prompt/output mapping to choose item-level actions.
4. Run focused decision/verifier tests.

### Task 5: Make `AgentRuntime` advance plan items instead of stopping early

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/runtime/AgentRuntime.java`
- Modify: `src/main/java/com/jaguarliu/ai/runtime/SystemPromptBuilder.java`
- Test: `src/test/java/com/jaguarliu/ai/runtime/AgentRuntimeTest.java`

**Steps:**
1. Write failing tests for: initializing a plan, not ending when current item finishes, and only ending when all items are done.
2. Initialize plan at React entry.
3. Inject current plan item summary into each runtime step request without polluting persisted chat history.
4. Consume decision actions to advance items, ask user, stop, or finish.
5. Downgrade the old “plain text mandatory plan” prompt rule so planning becomes runtime state, not only assistant prose.
6. Run focused runtime tests.

### Task 6: Bind subagent and HITL outcomes to plan items

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/runtime/AgentRuntime.java`
- Modify: `src/main/java/com/jaguarliu/ai/runtime/ToolExecutor.java`
- Test: `src/test/java/com/jaguarliu/ai/runtime/AgentRuntimeTest.java`
- Test: `src/test/java/com/jaguarliu/ai/tools/builtin/SessionsSpawnToolTest.java`

**Steps:**
1. Write failing tests for plan items delegated to subagents and blocked by HITL rejection.
2. Record `subRunId` on delegated items.
3. Mark item blocked or in-progress based on HITL/subagent results.
4. Run focused runtime/subagent tests.

### Task 7: Run focused regression suite and update docs

**Files:**
- Modify: `docs/plans/2026-03-09-ralph-agent-core-architecture.md`
- Modify: `docs/plans/2026-03-09-ralph-main-flow-refactor-followup.md`
- Test: `src/test/java/com/jaguarliu/ai/runtime/TaskRouterTest.java`
- Test: `src/test/java/com/jaguarliu/ai/runtime/ContextBuilderPolicyTest.java`
- Test: `src/test/java/com/jaguarliu/ai/runtime/PlanEngineTest.java`
- Test: `src/test/java/com/jaguarliu/ai/runtime/DecisionTest.java`
- Test: `src/test/java/com/jaguarliu/ai/runtime/DecisionEngineTest.java`
- Test: `src/test/java/com/jaguarliu/ai/runtime/LlmRuntimeDecisionStageTest.java`
- Test: `src/test/java/com/jaguarliu/ai/runtime/AgentRuntimeTest.java`
- Test: `src/test/java/com/jaguarliu/ai/gateway/rpc/handler/agent/AgentRunWithAgentIdTest.java`

**Steps:**
1. Run the focused regression suite after implementation.
2. Update architecture docs to reflect `DIRECT`/`REACT` and in-memory plan kernel.
3. Stop only when focused regressions pass.
