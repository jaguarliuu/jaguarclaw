# Ralph Loop Decision Flow Refactor Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Status:** Partially superseded by implementation. Keep this file as the original execution plan plus final status notes.

**Goal:** Refactor Ralph loop so runtime stop/continue decisions are LLM-led, with rules limited to hard safety/user-decision guards, and remove premature termination on repairable environment failures.

**Architecture:** Keep `TaskRouter` responsible only for execution path selection, keep `LoopOrchestrator` responsible only for budget/timeout/cancellation limits, and move runtime semantic assessment into a single verifier path led by `LlmRuntimeDecisionStage`. Convert keyword classification from a terminal rule source into non-terminal failure hints that inform verifier prompts and progress accounting.

**Tech Stack:** Java 21, Spring Boot, Reactor, JUnit 5, Mockito, Maven

---

## Final Status Summary

### Implemented
- Runtime decisions are now centered on structured tool semantics plus LLM-led semantic judgment.
- `ToolResult.failureCategory` is the main structured signal source.
- `DecisionInput` and `DecisionInputFactory` now unify runtime decision inputs.
- `DecisionEngine` is now the single semantic decision entry point.
- `OutcomeApplier` is now the single decision side-effect sink.
- `ProgressSnapshot` now owns repair-budget / repeated-failure stop predicates.
- `RuntimeDecisionStage` now returns `Decision` directly.
- `VerificationResult` has been removed from the main runtime path.

### Implemented But Different From Original Plan
- Original `Task 2` proposed strengthening `RuntimeFailureClassifier`.
- Final implementation intentionally did the opposite: the classifier was kept out of the main runtime path to avoid reintroducing text-driven stop logic.
- Original verifier composition remains in class names for compatibility in a few places, but behavior is now routed through `DecisionEngine`.

### Not Done From Original Plan
- The optional rename pass was only partially realized.
- `HardGuardVerifier` has been renamed and now matches its actual responsibility.
- The original per-task commit steps were not performed.

### Current Architecture Outcome
- `AgentRuntime`: orchestration
- `DecisionInputFactory`: signal collection
- `DecisionEngine`: unified decision
- `OutcomeApplier`: unified side effects
- `LoopOrchestrator`: budget / timeout / cancellation only
- `RuntimeFailureClassifier`: deprecated and not used by the main runtime decision flow

---

## Original Task Checklist

### Task 1: Lock the target behavior with regression tests
**Status:** Done

### Task 2: Split failure hints from hard-stop rules
**Status:** Replaced by better architecture
- The original text-classifier-based split was intentionally abandoned.
- Structured tool-native categories replaced classifier-driven inference in the main path.

### Task 3: Shrink hard guard verifier into a hard-guard verifier
**Status:** Done

### Task 4: Make `DecisionEngine` LLM-led for repairable failures
**Status:** Done, then superseded
- Behavior achieved.
- Final implementation now routes through `DecisionEngine`.

### Task 5: Upgrade `LlmRuntimeDecisionStage` to drive repair behavior
**Status:** Done

### Task 6: Activate environment repair accounting inside the runtime loop
**Status:** Done

### Task 7: Remove duplicate runtime blocking from `PolicySupervisor`
**Status:** Done

### Task 8: Relax loop stop budgets during active repair
**Status:** Done

### Task 9: Normalize runtime outcomes and message rendering
**Status:** Done

### Task 10: Run focused regression suite and document behavior changes
**Status:** Done
- Focused and broader regression suites were run during implementation.
- Design outcome is summarized in this file and the follow-up plan file.

### Task 11: Optional cleanup rename pass
**Status:** Partially done
- `VerificationResult` removal is done.
- Broader class rename cleanup remains optional follow-up.

---

## Notes for the Next Engineer
- Do not reintroduce regex or keyword-driven runtime stop logic.
- Do not use command-name special cases to decide whether the loop should stop.
- If a tool can know the failure cause structurally, emit `failureCategory` there.
- Keep `LoopOrchestrator` focused on budgets, not runtime semantics.
- Keep `DecisionEngine` as the only semantic decision entry point.
- Keep `OutcomeApplier` as the only decision side-effect entry point.
