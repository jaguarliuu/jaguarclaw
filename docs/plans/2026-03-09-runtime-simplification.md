# Runtime Simplification: Trust the LLM Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Remove the Verifier LLM layer, PlanEngine, TaskRouter, and pre-loop skill selector — reduce the loop to: step → model has tool calls? execute and continue : return.

**Architecture:** The new loop trusts the model's own stop_reason. When the model stops calling tools, the task is done. Stop conditions are only: user abort, timeout, and maxSteps hard cap. No secondary LLM calls per step.

**Tech Stack:** Java 17, Spring Boot, Lombok, existing ToolExecutor / SkillActivator / SubagentBarrier components (all preserved).

---

### Task 1: Rewrite `doExecuteLoop` in AgentRuntime

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/runtime/AgentRuntime.java`

Replace the entire `doExecuteLoop` method (lines 125–332) with the simplified version below. Do NOT touch any other methods yet — they will be removed in Task 4.

**New `doExecuteLoop` method:**

```java
private String doExecuteLoop(RunContext context, List<LlmRequest.Message> messages) throws TimeoutException {
    log.info("Starting ReAct loop: runId={}, maxSteps={}, timeout={}s",
            context.getRunId(), context.getConfig().getMaxSteps(),
            context.getConfig().getRunTimeoutSeconds());

    List<String> pendingSubRunIds = new ArrayList<>();

    while (true) {
        if (context.isAborted()) {
            throw new CancellationException("Run cancelled by user");
        }
        if (context.isTimedOut()) {
            throw new TimeoutException("ReAct loop timeout after " + context.getElapsedSeconds() + " seconds");
        }
        if (context.isMaxStepsReached()) {
            log.warn("Loop reached max steps: runId={}, maxSteps={}",
                    context.getRunId(), context.getConfig().getMaxSteps());
            StepResult finalResult = executeSingleStep(context, messages);
            return finalResult.content();
        }

        flushHook.checkAndFlush(context.getRunId(), messages);

        StepResult result = executeSingleStep(context, messages);

        if (!result.hasToolCalls()) {
            if (!pendingSubRunIds.isEmpty() && context.isMain()) {
                log.info("Waiting for {} pending subagents: runId={}",
                        pendingSubRunIds.size(), context.getRunId());
                messages.add(LlmRequest.Message.assistant(result.content()));
                String subagentResultsSummary = subagentBarrier.waitForCompletion(pendingSubRunIds, context);
                pendingSubRunIds.clear();
                messages.add(LlmRequest.Message.user(subagentResultsSummary));
                log.info("Subagent results injected, continuing loop: runId={}", context.getRunId());
                continue;
            }
            log.info("Loop completed: model stopped calling tools, runId={}, steps={}",
                    context.getRunId(), context.getCurrentStep());
            return result.content();
        }

        log.info("Step {} has {} tool calls: runId={}",
                context.getCurrentStep(), result.toolCalls().size(), context.getRunId());

        // use_skill tool activation (tool-driven, not an extra LLM call)
        Optional<SkillActivator.SkillActivation> toolActivation =
                skillActivator.detectToolActivation(result.toolCalls(), context);
        if (toolActivation.isPresent()) {
            if (context.getActiveSkill() != null && context.getActiveSkill().hasActiveSkill()) {
                log.info("skill.activation_skipped reason=already_active skill={} runId={}",
                        toolActivation.get().skillName(), context.getRunId());
            } else {
                String skillName = toolActivation.get().skillName();
                List<LlmRequest.Message> cleanHistory = extractHistory(messages);
                Optional<SkillActivator.SkillAwareRequest> skillRequest =
                        skillActivator.applyActivation(toolActivation.get(), cleanHistory,
                                context.getOriginalInput(), context.getAgentId());
                if (skillRequest.isPresent()) {
                    context.incrementSkillActivation(skillName);
                    skillActivator.publishActivationEvent(context, toolActivation.get());
                    messages.clear();
                    messages.addAll(skillRequest.get().messages());
                    Optional<ContextBuilder.SkillAwareRequest> ctxRequest =
                            contextBuilder.handleSkillActivationByName(skillName,
                                    context.getOriginalInput(), cleanHistory, true, context.getAgentId());
                    if (ctxRequest.isPresent()) {
                        context.setActiveSkill(ctxRequest.get());
                        context.setSkillBasePath(ctxRequest.get().skillBasePath());
                    }
                    context.incrementStep();
                    eventBus.publish(AgentEvent.stepCompleted(context.getConnectionId(), context.getRunId(), context.getCurrentStep()));
                    log.info("Re-invoking with skill (via tool activation): {}, runId={}", skillName, context.getRunId());
                    continue;
                }
                log.warn("skill.activation_skipped reason=apply_failed skill={} runId={}", skillName, context.getRunId());
            }
        }

        messages.add(LlmRequest.Message.assistantWithToolCalls(result.toolCalls()));

        List<ToolExecutor.ToolExecutionResult> toolResults =
                toolExecutor.executeToolCalls(context, result.toolCalls());

        boolean shouldStopOnHitlReject = false;
        String rejectedToolName = null;

        for (int i = 0; i < result.toolCalls().size(); i++) {
            ToolCall toolCall = result.toolCalls().get(i);
            ToolExecutor.ToolExecutionResult execResult = toolResults.get(i);
            messages.add(LlmRequest.Message.toolResult(toolCall.getId(), execResult.result().getContent()));

            if (isHitlRejectedResult(execResult)) {
                shouldStopOnHitlReject = true;
                rejectedToolName = toolCall.getName();
            }

            if ("sessions_spawn".equals(toolCall.getName()) && execResult.result().isSuccess()) {
                String subRunId = parseSubRunIdFromToolResult(execResult.result().getContent());
                if (subRunId != null) {
                    pendingSubRunIds.add(subRunId);
                    log.info("Tracked spawned subagent: subRunId={}, runId={}", subRunId, context.getRunId());
                }
            }
        }

        if (shouldStopOnHitlReject) {
            String rejectedTool = rejectedToolName != null ? rejectedToolName : "unknown";
            String stopMessage = "Tool call was rejected by user (" + rejectedTool + "). Execution stopped.";
            messages.add(LlmRequest.Message.assistant(stopMessage));
            log.info("Loop stopped due to HITL rejection: runId={}, tool={}", context.getRunId(), rejectedTool);
            return stopMessage;
        }

        context.incrementStep();
        eventBus.publish(AgentEvent.stepCompleted(context.getConnectionId(), context.getRunId(), context.getCurrentStep()));
    }
}
```

**Verification:** The file should compile at this point (old helper methods are still present).

---

### Task 2: Simplify `executeLoopWithContext` (remove policy gate)

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/runtime/AgentRuntime.java`

Replace the 3-argument `executeLoopWithContext` (lines 95–108) with:

```java
public String executeLoopWithContext(RunContext context, List<LlmRequest.Message> messages,
                                      String originalInput) throws TimeoutException {
    if (originalInput != null) {
        context.setOriginalInput(originalInput);
    }
    return executeLoopWithContext(context, messages);
}
```

The `policySupervisor.evaluate()` call and the `outcomeApplier.apply()` call on policy rejection are removed. If the policy gate is needed in the future, it belongs in a gateway filter, not the runtime loop.

---

### Task 3: Fix `executeSingleStep` (remove plan injection)

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/runtime/AgentRuntime.java`

In `executeSingleStep` (around line 638), change the first line of the method body from:

```java
LlmRequest.LlmRequestBuilder requestBuilder = LlmRequest.builder()
        .messages(enrichMessagesWithPlan(context, messages))
        .toolChoice("auto");
```

to:

```java
LlmRequest.LlmRequestBuilder requestBuilder = LlmRequest.builder()
        .messages(messages)
        .toolChoice("auto");
```

This removes the plan reminder injection. The model no longer receives a separate system-injected plan reminder each step.

---

### Task 4: Remove dead helper methods from AgentRuntime

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/runtime/AgentRuntime.java`

Delete the following methods entirely (they are no longer called):

- `initializeExecutionPlanIfNeeded` (~line 334)
- `enrichMessagesWithPlan` (~line 344)
- `buildPlanReminder` (~line 356)
- `handleDecision` (~line 381)
- `DecisionResolution` private record (~line 427)
- `bindCurrentItemToActiveSkill` (~line 437)
- `syncActiveSkillWithCurrentPlanItem` (~line 448)
- `trySemanticSkillActivation` (~line 499)
- `resolveDecisionStage` (~line 559)
- `applyDecision` (~line 563)
- `applyTerminalPlanEffects` (~line 568)
- `firstNonBlank` (~line 607)
- `recordToolRoundProgress` (~line 619)

Also remove these unused imports (compiler will flag them):
- `import com.jaguarliu.ai.runtime.DecisionAction;` (if present as import)
- Any import for `PlanItem`, `PlanItemStatus`, `DecisionInput`, `DecisionResolution`, `RuntimeFailureCategories`

---

### Task 5: Remove dead field injections from AgentRuntime

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/runtime/AgentRuntime.java`

Remove these injected fields from the class (they are no longer referenced):

```java
// DELETE these lines:
private final DecisionInputFactory decisionInputFactory;
private final OutcomeApplier outcomeApplier;
private final PlanEngine planEngine;
private final LoopOrchestrator loopOrchestrator;
private final ReactEntrySkillSelector reactEntrySkillSelector;
private final DecisionEngine defaultDecisionEngine;
private final PolicySupervisor policySupervisor;
```

Also remove unused fields from the top section if any remain (check `toolDispatcher`, `subagentCompletionTracker`, `sessionFileService` — only remove if they are no longer referenced anywhere in the class).

Also clean up unused imports in the import block. Run a compile to identify which ones to remove.

**After this step:** Run `mvn compile` (or equivalent). Fix any remaining compile errors in AgentRuntime before proceeding.

---

### Task 6: Simplify `AgentRunHandler.executeRun`

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/agent/AgentRunHandler.java`

In `executeRun` (around line 218), replace the entire `TaskRoutingDecision` block and the `switch (routingDecision.getRouteMode())` block with the simplified version below. The handler now always uses the REACT (agent loop) path.

**Replace everything from the `TaskRoutingDecision routingDecision = ...` line through the `yield agentRuntime.executeLoopWithContext(context, messages)` line with:**

```java
AgentContext agentCtx = AgentContext.builder()
        .sessionId(sessionId)
        .runId(runId)
        .connectionId(connectionId)
        .agentId(run.getAgentId())
        .prompt(prompt)
        .excludedMcpServers(excludedMcpServers)
        .build();

AgentStrategy strategy = strategyResolver.resolve(agentCtx);
AgentExecutionPlan plan = strategy.prepare(agentCtx);

List<LlmRequest.Message> messages = new ArrayList<>();
messages.add(LlmRequest.Message.system(plan.getSystemPrompt()));
messages.addAll(historyMessages);
messages.add(currentUserMessage);

log.debug("Context built: strategy={}, history={} messages",
        plan.getStrategyName(), historyMessages.size());

LoopConfig effectiveConfig = plan.getMaxStepsOverride() != null
        ? LoopConfig.withMaxSteps(plan.getMaxStepsOverride(), loopConfig)
        : loopConfig;
context = buildRoutedContext(run, connectionId, sessionId,
        plan.getExcludedMcpServers(), effectiveConfig, modelSelection);
context.setStrategyAllowedTools(plan.getAllowedTools());

String response = agentRuntime.executeLoopWithContext(context, messages);
```

Also delete the `executeDirectRoute` private method entirely (~line 376–415).

---

### Task 7: Simplify `buildRoutedContext` in AgentRunHandler

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/agent/AgentRunHandler.java`

Replace the `buildRoutedContext` method signature and body. The method no longer takes a `routingDecision` parameter and no longer sets `taskContract`:

```java
private RunContext buildRoutedContext(RunEntity run,
                                      String connectionId,
                                      String sessionId,
                                      Set<String> excludedMcpServers,
                                      LoopConfig effectiveConfig,
                                      String modelSelection) {
    RunContext context = RunContext.create(run.getId(), connectionId, sessionId,
            run.getAgentId(), effectiveConfig, cancellationManager);
    context.setExcludedMcpServers(excludedMcpServers);
    context.setOriginalInput(run.getPrompt());
    if (modelSelection != null && !modelSelection.isBlank()) {
        context.setModelSelection(modelSelection);
    }
    return context;
}
```

---

### Task 8: Remove dead field injections from AgentRunHandler

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/agent/AgentRunHandler.java`

Remove these injected fields:

```java
// DELETE these lines:
private final ChatRouter chatRouter;
private final TaskRouter taskRouter;
private final ReactEntrySkillSelector reactEntrySkillSelector;
```

Remove corresponding imports.

Also check if `publishRoutedOutcome` is still called anywhere in the file — if not, delete it too.

**Run `mvn compile` to verify no remaining references.**

---

### Task 9: Simplify `RunContext`

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/runtime/RunContext.java`

**Remove these `@Builder.Default` fields and their Javadoc:**

```java
// DELETE:
private final AtomicInteger repeatedFailureCount = new AtomicInteger(0);
private final AtomicInteger lowProgressRounds = new AtomicInteger(0);
private final AtomicInteger environmentRepairAttempts = new AtomicInteger(0);
private final AtomicReference<String> lastFailureCategory = new AtomicReference<>();
private final AtomicReference<String> lastFailureDetail = new AtomicReference<>();
private final Set<String> runtimeFailureCategories = ConcurrentHashMap.newKeySet();
```

**Remove these `@Setter` fields:**

```java
// DELETE:
private RuntimeDecisionStage runtimeDecisionStage;
private TaskContract taskContract;
private ExecutionPlan executionPlan;
private boolean planInitialized;
private String pendingQuestion;
```

**Remove these methods entirely:**

- `recordFailure(String category)` and `recordFailure(String category, String detail)`
- `recordLowProgressRound()`
- `recordMeaningfulProgress()`
- `recordEnvironmentRepairAttempt()`
- `isRepeatedFailureLimitReached()`
- `isLowProgressLimitReached()`
- `isTokenBudgetReached()`
- `snapshotProgress()`
- `replaceRuntimeFailureCategories(Collection<String> categories)`
- `clearRuntimeFailureCategories()`
- `getRuntimeFailureCategories()`
- `hasRuntimeFailureCategory(String category)`
- `hasExecutionPlan()`
- `currentPlanItem()`

**Remove unused imports:**
- `import com.jaguarliu.ai.runtime.RuntimeDecisionStage;` (if present)
- `import com.jaguarliu.ai.runtime.TaskContract;`
- `import com.jaguarliu.ai.runtime.ExecutionPlan;`
- `import com.jaguarliu.ai.runtime.PlanItem;`
- `import java.util.Collection;`
- Any others flagged by compiler

**Run `mvn compile` to verify.**

---

### Task 10: Simplify `LoopConfig`

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/runtime/LoopConfig.java`

Remove these fields (and any Javadoc/`@Value` annotations above them):

```java
// DELETE:
private int maxRepeatedFailures;
private int maxLowProgressRounds;
private int maxEnvironmentRepairAttempts;
```

Also remove the corresponding getters if Lombok is not generating them (`getMaxRepeatedFailures()`, `getMaxLowProgressRounds()`, `getMaxEnvironmentRepairAttempts()`).

Remove from `application.properties` / `application.yml` (whichever is used):
```
agent.loop.max-repeated-failures=...
agent.loop.max-low-progress-rounds=...
agent.loop.max-environment-repair-attempts=...
```

Also remove the `withMaxSteps` method's reference to these fields if it copies them.

**Run `mvn compile` to verify.**

---

### Task 11: Delete decision-layer files

All references have been removed. Delete these files:

```
src/main/java/com/jaguarliu/ai/runtime/RuntimeDecisionStage.java
src/main/java/com/jaguarliu/ai/runtime/DecisionEngine.java
src/main/java/com/jaguarliu/ai/runtime/LlmRuntimeDecisionStage.java
src/main/java/com/jaguarliu/ai/runtime/HardGuardVerifier.java
src/main/java/com/jaguarliu/ai/runtime/VerifierDecision.java
src/main/java/com/jaguarliu/ai/runtime/Decision.java
src/main/java/com/jaguarliu/ai/runtime/DecisionAction.java
src/main/java/com/jaguarliu/ai/runtime/DecisionInput.java
src/main/java/com/jaguarliu/ai/runtime/DecisionInputFactory.java
src/main/java/com/jaguarliu/ai/runtime/PolicySupervisor.java
src/main/java/com/jaguarliu/ai/runtime/PolicyDecision.java
src/main/java/com/jaguarliu/ai/runtime/OutcomeApplier.java
```

Run: `mvn compile` — fix any remaining references to these classes.

---

### Task 12: Delete plan-layer files

```
src/main/java/com/jaguarliu/ai/runtime/PlanEngine.java
src/main/java/com/jaguarliu/ai/runtime/PlanItem.java
src/main/java/com/jaguarliu/ai/runtime/PlanItemStatus.java
src/main/java/com/jaguarliu/ai/runtime/ExecutionPlan.java
src/main/java/com/jaguarliu/ai/runtime/ExecutionPlanStatus.java
src/main/java/com/jaguarliu/ai/runtime/PlanExecutionMode.java
src/main/java/com/jaguarliu/ai/runtime/TaskContract.java
```

Run: `mvn compile` — fix any remaining references.

---

### Task 13: Delete routing and complexity files

```
src/main/java/com/jaguarliu/ai/runtime/TaskRouter.java
src/main/java/com/jaguarliu/ai/runtime/TaskRoutingDecision.java
src/main/java/com/jaguarliu/ai/runtime/TaskRouteMode.java
src/main/java/com/jaguarliu/ai/runtime/TaskComplexity.java
src/main/java/com/jaguarliu/ai/runtime/ChatRouter.java   (only if no other callers remain)
```

Run: `mvn compile`. If `ChatRouter` has other callers outside `AgentRunHandler`, keep it.

---

### Task 14: Delete pre-loop selector and progress files

```
src/main/java/com/jaguarliu/ai/runtime/ReactEntrySkillSelector.java
src/main/java/com/jaguarliu/ai/runtime/ReactEntrySkillSelection.java
src/main/java/com/jaguarliu/ai/runtime/LoopOrchestrator.java
src/main/java/com/jaguarliu/ai/runtime/StopDecision.java
src/main/java/com/jaguarliu/ai/runtime/RuntimeFailureCategories.java
src/main/java/com/jaguarliu/ai/runtime/RuntimeFailureClassifier.java
src/main/java/com/jaguarliu/ai/runtime/ProgressSnapshot.java
```

Before deleting `RuntimeFailureCategories.java`, check if `ToolExecutor` or `HitlManager` reference it (they may use `HITL_REJECTED` constant). If so, inline the constant string `"hitl_rejected"` directly in `ToolExecutor` before deleting.

Run: `mvn compile` after each deletion batch.

---

### Task 15: Final compile and smoke test

Run:
```bash
mvn compile
```

Expected: BUILD SUCCESS with 0 errors.

Then do a quick manual smoke test:
1. Start the application
2. Send a simple prompt via the WebSocket client
3. Verify: the run starts, the LLM responds, the run completes
4. Verify: no `NullPointerException` or missing bean errors in logs

---

### Task 16: Commit

```bash
git add -p  # stage only runtime package changes
git commit -m "$(cat <<'EOF'
refactor(runtime): trust the LLM — remove verifier, plan engine, and pre-loop routing

Remove DecisionEngine (verifier LLM), PlanEngine (pre-loop plan LLM),
TaskRouter (routing LLM), and ReactEntrySkillSelector (skill LLM).

New loop: model stops calling tools → task complete.
Only stop conditions: user abort, timeout, maxSteps hard cap.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```
