# Ralph Main Flow Refactor Follow-Up

**Goal:** Document the current post-refactor Ralph main flow so future work continues from the new architecture rather than from the original mixed runtime flow.

**Status:** Current architecture snapshot after Batch A-F implementation.

---

## Main Flow

```text
LoopOrchestrator.checkStopDecision()
  -> AgentRuntime.executeSingleStep()
  -> ToolExecutor.executeToolCalls()           [when tool calls exist]
  -> DecisionInputFactory.build(...)
  -> DecisionEngine.decide(...)
  -> OutcomeApplier.apply(...)
  -> loop continue / stop
```

---

## Component Responsibilities

### `AgentRuntime`
- Owns loop orchestration only
- Calls step execution, tool execution, decision input construction, decision engine, outcome applier
- Does not own semantic decision logic
- Does not own direct progress / outcome side-effect rules anymore

### `DecisionInputFactory`
- Converts runtime state into immutable decision input snapshots
- Centralizes assistant-round, tool-round, and verifier-entry signal gathering
- Prevents signal assembly from being duplicated in `AgentRuntime`

### `DecisionEngine`
- Single semantic runtime decision entry point
- Runs hard guard first
- Runs LLM semantic judgment second when needed
- Returns unified `Decision`

### `HardGuardVerifier`
- Behaves as a hard guard verifier
- Only handles hard environment block and user decision required
- Must not decide repair strategy for repairable failures

### `LlmRuntimeDecisionStage`
- Handles semantic runtime judgment
- Consumes structured runtime categories and progress context
- Decides complete / continue / blocked / degraded / not worth continuing

### `OutcomeApplier`
- Single sink for decision side effects
- Updates `RunContext`
- Applies failure accounting
- Applies repair attempt accounting
- Persists terminal outcome state
- Publishes runtime outcome events when applicable
- Selects visible final message for terminal outcomes

### `ProgressSnapshot`
- Encapsulates repeated-failure / repair-budget / low-progress stop predicates
- Keeps budget-related logic out of `LoopOrchestrator`

### `LoopOrchestrator`
- Budget / timeout / cancellation / step limit only
- Consumes `ProgressSnapshot` results instead of reasoning about runtime semantics directly

---

## Explicit Non-Goals
- No regex-driven runtime stop logic
- No keyword-driven runtime stop logic
- No command-name-specific stop logic
- No text classifier in the main runtime path
- No duplication of outcome side effects across runtime layers

---

## Decision Boundaries

### Tool Layer
- Emits structured `failureCategory` if the failure cause is known structurally
- Does not decide whether the loop should continue

### Runtime Decision Layer
- Uses `DecisionInput` + LLM + hard guards
- Decides whether to continue, stop, or ask the user

### Budget Layer
- Uses `ProgressSnapshot` and loop configuration only
- Decides whether continuing is still within budget

---

## Current Remaining Follow-Ups

### Optional Naming Cleanup
- `HardGuardVerifier` rename is already complete
- `DecisionEngine` now directly implements `RuntimeDecisionStage`; remaining cleanup is naming consistency only

### Optional API Cleanup
- Consider removing old verifier-shaped method names such as `applyDecision(...)` call sites that still carry historical naming semantics
- Consider introducing a more explicit `HardGuardDecisionEngine` internal helper only if it improves clarity without re-fragmenting the flow

### Optional Documentation Cleanup
- Add a short section to `README.md` and `README.zh-CN.md` describing:
  - structured tool-native failure categories
  - LLM-led semantic runtime decisions
  - budget-only orchestrator semantics

---

## Guardrails For Future Changes
- If a new tool cannot know failure semantics structurally, prefer leaving category unset instead of inventing text parsing in the runtime
- If a loop-stop rule is about semantics, it belongs in `DecisionEngine`
- If a loop-stop rule is about budget or repeated attempts, it belongs in `ProgressSnapshot` / `LoopOrchestrator`
- If a change needs to update outcome state, it should likely go through `OutcomeApplier`
