package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.gateway.events.AgentEvent;
import com.jaguarliu.ai.gateway.events.EventBus;
import com.jaguarliu.ai.llm.LlmClient;
import com.jaguarliu.ai.llm.model.LlmChunk;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.llm.model.LlmResponse;
import com.jaguarliu.ai.llm.model.ToolCall;
import com.jaguarliu.ai.memory.flush.PreCompactionFlushHook;
import com.jaguarliu.ai.session.SessionFileService;
import com.jaguarliu.ai.subagent.SubagentCompletionTracker;
import com.jaguarliu.ai.tools.ToolDispatcher;
import com.jaguarliu.ai.tools.ToolRegistry;
import com.jaguarliu.ai.tools.ToolResult;
import com.jaguarliu.ai.tools.ToolVisibilityResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentRuntime Tests")
class AgentRuntimeTest {

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(planEngine.createInitialPlan(any(), any())).thenAnswer(invocation -> {
            RunContext context = invocation.getArgument(0);
            String goal = context.getOriginalInput();
            return ExecutionPlan.builder()
                    .goal(goal)
                    .status(ExecutionPlanStatus.ACTIVE)
                    .currentItemId("item-1")
                    .items(new java.util.ArrayList<>(java.util.List.of(
                            PlanItem.builder().id("item-1").title(goal != null ? goal : "task").status(PlanItemStatus.IN_PROGRESS).executionMode(PlanExecutionMode.MAIN_AGENT).build()
                    )))
                    .revision(1)
                    .build();
        });
        org.mockito.Mockito.lenient().when(planEngine.markDone(any(), any(), any())).thenAnswer(invocation -> {
            ExecutionPlan plan = invocation.getArgument(0);
            String itemId = invocation.getArgument(1);
            String note = invocation.getArgument(2);
            for (PlanItem item : plan.getItems()) {
                if (itemId.equals(item.getId())) {
                    item.setStatus(PlanItemStatus.DONE);
                    item.setNotes(note);
                }
            }
            if (plan.allItemsDone()) {
                plan.setStatus(ExecutionPlanStatus.COMPLETED);
                plan.setCurrentItemId(null);
            }
            return plan;
        });
        org.mockito.Mockito.lenient().when(planEngine.advance(any())).thenAnswer(invocation -> {
            ExecutionPlan plan = invocation.getArgument(0);
            for (PlanItem item : plan.getItems()) {
                if (item.getStatus() == PlanItemStatus.PENDING) {
                    item.setStatus(PlanItemStatus.IN_PROGRESS);
                    plan.setCurrentItemId(item.getId());
                    plan.setStatus(ExecutionPlanStatus.ACTIVE);
                    return plan;
                }
            }
            if (plan.allItemsDone()) {
                plan.setStatus(ExecutionPlanStatus.COMPLETED);
                plan.setCurrentItemId(null);
            }
            return plan;
        });
        org.mockito.Mockito.lenient().when(planEngine.markBlocked(any(), any(), any())).thenAnswer(invocation -> {
            ExecutionPlan plan = invocation.getArgument(0);
            String itemId = invocation.getArgument(1);
            String reason = invocation.getArgument(2);
            for (PlanItem item : plan.getItems()) {
                if (itemId.equals(item.getId())) {
                    item.setStatus(PlanItemStatus.BLOCKED);
                    item.setNotes(reason);
                }
            }
            plan.setStatus(ExecutionPlanStatus.BLOCKED);
            return plan;
        });
        org.mockito.Mockito.lenient().when(planEngine.bindSkill(any(), any(), any())).thenAnswer(invocation -> {
            ExecutionPlan plan = invocation.getArgument(0);
            String itemId = invocation.getArgument(1);
            String skillName = invocation.getArgument(2);
            for (PlanItem item : plan.getItems()) {
                if (itemId.equals(item.getId())) {
                    item.setSkillName(skillName);
                }
            }
            return plan;
        });
    }

    @Mock private LlmClient llmClient;
    @Mock private ToolRegistry toolRegistry;
    @Mock private ToolDispatcher toolDispatcher;
    @Mock private EventBus eventBus;
    @Mock private HitlManager hitlManager;
    @Mock private ContextBuilder contextBuilder;
    @Mock private PreCompactionFlushHook flushHook;
    @Mock private SubagentCompletionTracker subagentCompletionTracker;
    @Mock private SessionFileService sessionFileService;
    @Mock private LoopOrchestrator loopOrchestrator;
    @Mock private ToolExecutor toolExecutor;
    @Mock private SkillActivator skillActivator;
    @Mock private PlanEngine planEngine;
    @Mock private SubagentBarrier subagentBarrier;
    @Mock private ReactEntrySkillSelector reactEntrySkillSelector;
    @Mock private DecisionEngine defaultDecisionEngine;
    @Mock private PolicySupervisor policySupervisor;
    @Mock private LlmRuntimeDecisionStage llmRuntimeDecisionStage;

    @Test
    @DisplayName("should detect HITL rejection marker from tool result")
    void shouldDetectHitlRejectionMarker() {
        ToolExecutor.ToolExecutionResult result = new ToolExecutor.ToolExecutionResult("call-1", ToolResult.error("rejected", RuntimeFailureCategories.HITL_REJECTED), RuntimeFailureCategories.HITL_REJECTED);
        assertTrue(AgentRuntime.isHitlRejectedResult(result));
    }

    @Test
    @DisplayName("should not treat normal tool errors as HITL rejection")
    void shouldIgnoreGenericToolErrors() {
        ToolExecutor.ToolExecutionResult result = new ToolExecutor.ToolExecutionResult("call-1", ToolResult.error("Command blocked by safety policy"), RuntimeFailureCategories.POLICY_BLOCK);
        assertFalse(AgentRuntime.isHitlRejectedResult(result));
    }

    @Test
    @DisplayName("should return blocked outcome message when verifier stops run")
    void shouldReturnBlockedOutcomeMessageWhenVerifierStopsRun() throws Exception {
        AgentRuntime runtime = createRuntime();
        RunContext context = RunContext.create(
                "run-1", "conn-1", "session-1",
                LoopConfig.builder().build(),
                new CancellationManager()
        );
        context.setOriginalInput("整理新闻并导出 PDF");

        ToolCall toolCall = ToolCall.builder()
                .id("call-1")
                .function(ToolCall.FunctionCall.builder()
                        .name("bash")
                        .arguments("{\"command\":\"wkhtmltopdf\"}")
                        .build())
                .build();

        when(loopOrchestrator.checkStopDecision(any())).thenReturn(StopDecision.continueLoop());
        when(toolRegistry.toOpenAiTools(any(ToolVisibilityResolver.VisibilityRequest.class))).thenReturn(List.of());
        when(toolRegistry.listVisibleToolNames(any())).thenReturn(Set.of("bash"));
        when(llmClient.stream(any())).thenReturn(Flux.just(LlmChunk.builder()
                .toolCalls(List.of(toolCall))
                .usage(LlmResponse.Usage.builder().promptTokens(10).completionTokens(5).build())
                .done(true)
                .build()));
        when(skillActivator.detectToolActivation(any(), any())).thenReturn(Optional.empty());
        when(toolExecutor.executeToolCalls(any(), any())).thenReturn(List.of(
                new ToolExecutor.ToolExecutionResult(
                        "call-1",
                        ToolResult.error("wkhtmltopdf: command not found"),
                        RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT
                )
        ));
        when(defaultDecisionEngine.verify(any(), any(), any())).thenReturn(
                Decision.terminal(
                        RunOutcome.blockedByEnvironment("wkhtmltopdf: command not found"),
                        RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT,
                        "wkhtmltopdf: command not found"
                )
        );
        when(flushHook.checkAndFlush(any(), any())).thenReturn(false);

        String result = runtime.executeLoopWithContext(context,
                new ArrayList<>(List.of(LlmRequest.Message.user("整理新闻并导出 PDF"))));

        assertTrue(result.contains("wkhtmltopdf"));
        assertTrue(context.hasOutcome());
        assertEquals(RunOutcomeStatus.BLOCKED_BY_ENVIRONMENT, context.getOutcome().status());

        ArgumentCaptor<AgentEvent> captor = ArgumentCaptor.forClass(AgentEvent.class);
        verify(eventBus, atLeastOnce()).publish(captor.capture());
        boolean found = captor.getAllValues().stream()
                .anyMatch(event -> event.getType() == AgentEvent.EventType.RUN_OUTCOME
                        && ((AgentEvent.RunOutcomeData) event.getData()).getStatus()
                        .equals(RunOutcomeStatus.BLOCKED_BY_ENVIRONMENT.name()));
        assertTrue(found);
    }

    @Test
    @DisplayName("should allow llm verifier to decide README missing through decision engine")
    void shouldAllowLlmVerifierToDecideReadmeMissingThroughDecisionEngine() throws Exception {
        RuntimeDecisionStage decisionEngine = new DecisionEngine(new DecisionInputFactory(), new HardGuardVerifier(), llmRuntimeDecisionStage);
        AgentRuntime runtime = createRuntime((DecisionEngine) decisionEngine);
        RunContext context = RunContext.create(
                "run-3", "conn-3", "session-3",
                LoopConfig.builder().build(),
                new CancellationManager()
        );
        context.setOriginalInput("请用 wkhtmltopdf 把 README.md 导出成 PDF");
        context.recordEnvironmentRepairAttempt();

        ToolCall toolCall = ToolCall.builder()
                .id("call-3")
                .function(ToolCall.FunctionCall.builder()
                        .name("read_file")
                        .arguments("{\"path\":\"README.md\"}")
                        .build())
                .build();

        when(loopOrchestrator.checkStopDecision(any())).thenReturn(StopDecision.continueLoop());
        when(toolRegistry.toOpenAiTools(any(ToolVisibilityResolver.VisibilityRequest.class))).thenReturn(List.of());
        when(toolRegistry.listVisibleToolNames(any())).thenReturn(Set.of("read_file"));
        when(llmClient.stream(any())).thenReturn(Flux.just(LlmChunk.builder()
                .toolCalls(List.of(toolCall))
                .usage(LlmResponse.Usage.builder().promptTokens(10).completionTokens(5).build())
                .done(true)
                .build()));
        when(skillActivator.detectToolActivation(any(), any())).thenReturn(Optional.empty());
        when(toolExecutor.executeToolCalls(any(), any())).thenReturn(List.of(
                new ToolExecutor.ToolExecutionResult(
                        "call-3",
                        ToolResult.error("Error: File not found: README.md", RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT),
                        RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT
                )
        ));
        when(llmRuntimeDecisionStage.verify(any(), any(), any())).thenReturn(
                Decision.terminal(
                        RunOutcome.blockedByEnvironment("README.md was not found after recovery attempts"),
                        RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT,
                        "README.md was not found after recovery attempts"
                )
        );
        when(flushHook.checkAndFlush(any(), any())).thenReturn(false);

        String result = runtime.executeLoopWithContext(context,
                new ArrayList<>(List.of(LlmRequest.Message.user("请用 wkhtmltopdf 把 README.md 导出成 PDF"))));

        assertTrue(result.contains("README.md"));
        assertTrue(context.hasOutcome());
        assertEquals(RunOutcomeStatus.BLOCKED_BY_ENVIRONMENT, context.getOutcome().status());
        verify(llmRuntimeDecisionStage).verify(any(), any(), any());
    }

    @Test
    @DisplayName("should record environment repair attempt when verifier continues repairable failure")
    void shouldRecordEnvironmentRepairAttemptWhenVerifierContinuesRepairableFailure() {
        AgentRuntime runtime = createRuntime();
        RunContext context = RunContext.create(
                "run-4", "conn-4", "session-4",
                LoopConfig.builder().build(),
                new CancellationManager()
        );

        String result = runtime.applyDecision(
                context,
                Decision.continueWithFeedback("search for alternative launcher", RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT, null),
                "search for alternative launcher"
        );

        assertEquals("search for alternative launcher", result);
        assertEquals(1, context.snapshotProgress().environmentRepairAttempts());
        assertEquals(RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT, context.snapshotProgress().lastFailureCategory());
    }

    @Test
    @DisplayName("should record low progress after repeated no-op tool rounds")
    void shouldRecordLowProgressAfterRepeatedNoOpToolRounds() {
        AgentRuntime runtime = createRuntime();
        RunContext context = RunContext.create(
                "run-1", "conn-1", "session-1",
                LoopConfig.builder().maxLowProgressRounds(2).build(),
                new CancellationManager()
        );

        List<ToolExecutor.ToolExecutionResult> noProgress = List.of(
                new ToolExecutor.ToolExecutionResult(
                        "call-1",
                        ToolResult.error("noop"),
                        RuntimeFailureCategories.TOOL_ERROR
                )
        );

        runtime.recordToolRoundProgress(context, noProgress);
        runtime.recordToolRoundProgress(context, noProgress);

        assertTrue(context.isLowProgressLimitReached());
    }

    @Test
    @DisplayName("should publish stop reason when budget is exceeded")
    void shouldPublishStopReasonWhenBudgetIsExceeded() throws Exception {
        LoopOrchestrator realLoopOrchestrator = new LoopOrchestrator(eventBus);
        AgentRuntime runtime = createRuntime(defaultDecisionEngine, realLoopOrchestrator);
        RunContext context = RunContext.create(
                "run-2", "conn-2", "session-2",
                LoopConfig.builder().maxRepeatedFailures(2).build(),
                new CancellationManager()
        );
        context.recordFailure(RuntimeFailureCategories.TOOL_ERROR);
        context.recordFailure(RuntimeFailureCategories.TOOL_ERROR);

        String result = runtime.executeLoopWithContext(context,
                new ArrayList<>(List.of(LlmRequest.Message.user("导出 PDF"))));

        assertTrue(result.contains("Repeated failure") || result.contains("environment"));

        ArgumentCaptor<AgentEvent> captor = ArgumentCaptor.forClass(AgentEvent.class);
        verify(eventBus, atLeastOnce()).publish(captor.capture());
        AgentEvent outcomeEvent = captor.getAllValues().stream()
                .filter(event -> event.getType() == AgentEvent.EventType.RUN_OUTCOME)
                .findFirst()
                .orElseThrow();
        AgentEvent.RunOutcomeData data = (AgentEvent.RunOutcomeData) outcomeEvent.getData();
        assertEquals(RunOutcomeStatus.NOT_WORTH_CONTINUING.name(), data.getStatus());
        assertEquals("repeated_failures", data.getReason());
    }


    @Test
    @DisplayName("should initialize execution plan when entering react loop")
    void shouldInitializeExecutionPlanWhenEnteringReactLoop() throws Exception {
        AgentRuntime runtime = createRuntime();
        RunContext context = RunContext.create(
                "run-plan", "conn-plan", "session-plan",
                LoopConfig.builder().build(),
                new CancellationManager()
        );
        context.setOriginalInput("打开浏览器访问知乎");

        when(loopOrchestrator.checkStopDecision(any())).thenReturn(StopDecision.continueLoop());
        when(toolRegistry.toOpenAiTools(any(ToolVisibilityResolver.VisibilityRequest.class))).thenReturn(List.of());
        when(toolRegistry.listVisibleToolNames(any())).thenReturn(Set.of());
        when(llmClient.stream(any())).thenReturn(Flux.just(LlmChunk.builder()
                .delta("done")
                .usage(LlmResponse.Usage.builder().promptTokens(1).completionTokens(1).build())
                .done(true)
                .build()));
        when(flushHook.checkAndFlush(any(), any())).thenReturn(false);
        when(defaultDecisionEngine.verify(any(), any(), any())).thenReturn(
                Decision.taskDone(RunOutcome.completed("done"), "completed", "item-1")
        );

        runtime.executeLoopWithContext(context, new ArrayList<>(List.of(LlmRequest.Message.user("打开浏览器访问知乎"))));

        assertTrue(context.hasExecutionPlan());
        assertTrue(context.isPlanInitialized());
        verify(planEngine).createInitialPlan(any(), any());
    }

    @Test
    @DisplayName("should advance plan when decision marks current item done")
    void shouldAdvancePlanWhenDecisionMarksCurrentItemDone() throws Exception {
        AgentRuntime runtime = createRuntime();
        RunContext context = RunContext.create(
                "run-plan-2", "conn-plan-2", "session-plan-2",
                LoopConfig.builder().build(),
                new CancellationManager()
        );
        context.setOriginalInput("完成两个步骤");
        context.setExecutionPlan(ExecutionPlan.builder()
                .goal("完成两个步骤")
                .status(ExecutionPlanStatus.ACTIVE)
                .currentItemId("item-1")
                .items(new java.util.ArrayList<>(List.of(
                        PlanItem.builder().id("item-1").title("step 1").status(PlanItemStatus.IN_PROGRESS).executionMode(PlanExecutionMode.MAIN_AGENT).build(),
                        PlanItem.builder().id("item-2").title("step 2").status(PlanItemStatus.PENDING).executionMode(PlanExecutionMode.MAIN_AGENT).build()
                )))
                .revision(1)
                .build());
        context.setPlanInitialized(true);

        when(loopOrchestrator.checkStopDecision(any())).thenReturn(StopDecision.continueLoop());
        when(toolRegistry.toOpenAiTools(any(ToolVisibilityResolver.VisibilityRequest.class))).thenReturn(List.of());
        when(toolRegistry.listVisibleToolNames(any())).thenReturn(Set.of());
        when(llmClient.stream(any())).thenReturn(Flux.just(LlmChunk.builder()
                .delta("step 1 finished")
                .usage(LlmResponse.Usage.builder().promptTokens(1).completionTokens(1).build())
                .done(true)
                .build()));
        when(flushHook.checkAndFlush(any(), any())).thenReturn(false);
        when(defaultDecisionEngine.verify(any(), any(), any())).thenReturn(Decision.itemDone("step 1 done", "item-1"), Decision.taskDone(RunOutcome.completed("all done"), "finished", "item-2"));

        String result = runtime.executeLoopWithContext(context, new ArrayList<>(List.of(LlmRequest.Message.user("完成两个步骤"))));

        assertEquals("all done", result);
        assertEquals(PlanItemStatus.DONE, context.getExecutionPlan().getItems().get(0).getStatus());
        assertEquals("item-2", context.getExecutionPlan().getCurrentItemId());
    }


    @Test
    @DisplayName("should mark current plan item blocked when HITL rejects tool")
    void shouldMarkCurrentPlanItemBlockedWhenHitlRejectsTool() throws Exception {
        AgentRuntime runtime = createRuntime();
        RunContext context = RunContext.create(
                "run-hitl", "conn-hitl", "session-hitl",
                LoopConfig.builder().build(),
                new CancellationManager()
        );
        context.setOriginalInput("执行高风险命令");
        context.setExecutionPlan(ExecutionPlan.builder()
                .goal("执行高风险命令")
                .status(ExecutionPlanStatus.ACTIVE)
                .currentItemId("item-1")
                .items(new java.util.ArrayList<>(List.of(
                        PlanItem.builder().id("item-1").title("执行命令").status(PlanItemStatus.IN_PROGRESS).executionMode(PlanExecutionMode.MAIN_AGENT).build()
                )))
                .revision(1)
                .build());
        context.setPlanInitialized(true);

        ToolCall toolCall = ToolCall.builder()
                .id("call-hitl")
                .function(ToolCall.FunctionCall.builder()
                        .name("remote_exec")
                        .arguments("{\"node\":\"prod\",\"command\":\"rm -rf /tmp/x\"}")
                        .build())
                .build();

        when(loopOrchestrator.checkStopDecision(any())).thenReturn(StopDecision.continueLoop());
        when(toolRegistry.toOpenAiTools(any(ToolVisibilityResolver.VisibilityRequest.class))).thenReturn(List.of());
        when(toolRegistry.listVisibleToolNames(any())).thenReturn(Set.of("remote_exec"));
        when(llmClient.stream(any())).thenReturn(Flux.just(LlmChunk.builder()
                .toolCalls(List.of(toolCall))
                .usage(LlmResponse.Usage.builder().promptTokens(1).completionTokens(1).build())
                .done(true)
                .build()));
        when(skillActivator.detectToolActivation(any(), any())).thenReturn(Optional.empty());
        when(toolExecutor.executeToolCalls(any(), any())).thenReturn(List.of(
                new ToolExecutor.ToolExecutionResult(
                        "call-hitl",
                        ToolResult.error("rejected", RuntimeFailureCategories.HITL_REJECTED),
                        RuntimeFailureCategories.HITL_REJECTED
                )
        ));
        when(flushHook.checkAndFlush(any(), any())).thenReturn(false);

        String result = runtime.executeLoopWithContext(context, new ArrayList<>(List.of(LlmRequest.Message.user("执行高风险命令"))));

        assertTrue(result.contains("rejected by user"));
        assertEquals(PlanItemStatus.BLOCKED, context.currentPlanItem().orElseThrow().getStatus());
    }

    @Test
    @DisplayName("should continue loop after single tool failure instead of stopping")
    void shouldContinueLoopAfterSingleToolFailureInsteadOfStopping() throws Exception {
        DecisionEngine decisionEngine = new DecisionEngine(new DecisionInputFactory(), new HardGuardVerifier(), llmRuntimeDecisionStage);
        AgentRuntime runtime = createRuntime(decisionEngine);
        RunContext context = RunContext.create(
                "run-recover", "conn-recover", "session-recover",
                LoopConfig.builder().maxRepeatedFailures(2).build(),
                new CancellationManager()
        );
        context.setOriginalInput("打开浏览器访问知乎");

        ToolCall toolCall = ToolCall.builder()
                .id("call-browser")
                .function(ToolCall.FunctionCall.builder()
                        .name("shell")
                        .arguments("{\"command\":\"agent-browser open https://www.zhihu.com\"}")
                        .build())
                .build();

        when(loopOrchestrator.checkStopDecision(any())).thenReturn(
                StopDecision.continueLoop(),
                StopDecision.continueLoop(),
                StopDecision.continueLoop()
        );
        when(toolRegistry.toOpenAiTools(any(ToolVisibilityResolver.VisibilityRequest.class))).thenReturn(List.of());
        when(toolRegistry.listVisibleToolNames(any())).thenReturn(Set.of("shell"));
        when(llmClient.stream(any())).thenReturn(
                Flux.just(LlmChunk.builder()
                        .toolCalls(List.of(toolCall))
                        .usage(LlmResponse.Usage.builder().promptTokens(10).completionTokens(5).build())
                        .done(true)
                        .build()),
                Flux.just(LlmChunk.builder()
                        .delta("已完成")
                        .usage(LlmResponse.Usage.builder().promptTokens(5).completionTokens(5).build())
                        .done(true)
                        .build())
        );
        when(skillActivator.detectToolActivation(any(), any())).thenReturn(Optional.empty());
        when(toolExecutor.executeToolCalls(any(), any())).thenReturn(List.of(
                new ToolExecutor.ToolExecutionResult(
                        "call-browser",
                        ToolResult.error("Daemon failed to start", RuntimeFailureCategories.TOOL_ERROR),
                        RuntimeFailureCategories.TOOL_ERROR
                )
        ));
        when(llmRuntimeDecisionStage.verify(any(), any(), any())).thenReturn(
                Decision.terminal(
                        new RunOutcome(RunOutcomeStatus.NOT_WORTH_CONTINUING, "agent-browser daemon failed", "agent-browser daemon failed"),
                        RuntimeFailureCategories.TOOL_ERROR,
                        "agent-browser daemon failed"
                ),
                Decision.taskDone(RunOutcome.completed("已完成"), "done", "item-1")
        );
        when(flushHook.checkAndFlush(any(), any())).thenReturn(false);

        String result = runtime.executeLoopWithContext(context,
                new ArrayList<>(List.of(LlmRequest.Message.user("打开浏览器访问知乎"))));

        assertEquals("已完成", result);
        verify(llmClient, org.mockito.Mockito.times(2)).stream(any());
    }


    @Test
    @DisplayName("should activate semantic skill during runtime before final direct answer")
    void shouldActivateSemanticSkillDuringRuntimeBeforeFinalDirectAnswer() throws Exception {
        AgentRuntime runtime = createRuntime();
        RunContext context = RunContext.create(
                "run-semantic-skill", "conn-semantic-skill", "session-semantic-skill",
                LoopConfig.builder().build(),
                new CancellationManager()
        );
        context.setOriginalInput("打开浏览器访问知乎");

        when(loopOrchestrator.checkStopDecision(any())).thenReturn(
                StopDecision.continueLoop(),
                StopDecision.continueLoop(),
                StopDecision.continueLoop()
        );
        when(toolRegistry.toOpenAiTools(any(ToolVisibilityResolver.VisibilityRequest.class))).thenReturn(List.of());
        when(toolRegistry.listVisibleToolNames(any())).thenReturn(Set.of());
        when(llmClient.stream(any())).thenReturn(
                Flux.just(LlmChunk.builder()
                        .delta("我来帮你打开浏览器")
                        .usage(LlmResponse.Usage.builder().promptTokens(1).completionTokens(1).build())
                        .done(true)
                        .build()),
                Flux.just(LlmChunk.builder()
                        .delta("已完成")
                        .usage(LlmResponse.Usage.builder().promptTokens(1).completionTokens(1).build())
                        .done(true)
                        .build())
        );
        when(flushHook.checkAndFlush(any(), any())).thenReturn(false);
        when(reactEntrySkillSelector.select(eq("打开浏览器访问知乎"), anyList(), anyString(), nullable(String.class)))
                .thenReturn(Optional.of(ReactEntrySkillSelection.builder()
                        .skillName("agent-browser")
                        .reason("browser task")
                        .confidence(0.95)
                        .build()));
        LlmRequest skillRequest = LlmRequest.builder()
                .messages(List.of(
                        LlmRequest.Message.system("skill system"),
                        LlmRequest.Message.user("打开浏览器访问知乎")
                ))
                .build();
        when(contextBuilder.handleSkillActivationByName(eq("agent-browser"), eq("打开浏览器访问知乎"), anyList(), eq(true), anyString()))
                .thenReturn(Optional.of(new ContextBuilder.SkillAwareRequest(
                        skillRequest,
                        "agent-browser",
                        Set.of("shell"),
                        Set.of(),
                        java.nio.file.Path.of("/tmp/agent-browser")
                )));
        when(defaultDecisionEngine.verify(any(), any(), any())).thenReturn(
                Decision.taskDone(RunOutcome.completed("已完成"), "done", "item-1")
        );

        String result = runtime.executeLoopWithContext(context, new ArrayList<>(List.of(LlmRequest.Message.user("打开浏览器访问知乎"))));

        assertEquals("已完成", result);
        assertEquals("agent-browser", context.getActiveSkill().activeSkillName());
        verify(llmClient, org.mockito.Mockito.times(2)).stream(any());
    }


    @Test
    @DisplayName("should bind entry active skill to current plan item when plan initializes")
    void shouldBindEntryActiveSkillToCurrentPlanItemWhenPlanInitializes() throws Exception {
        AgentRuntime runtime = createRuntime();
        RunContext context = RunContext.create(
                "run-bind-plan", "conn-bind-plan", "session-bind-plan",
                LoopConfig.builder().build(),
                new CancellationManager()
        );
        context.setOriginalInput("打开浏览器访问知乎");
        context.setActiveSkill(new ContextBuilder.SkillAwareRequest(
                LlmRequest.builder().messages(List.of()).build(),
                "agent-browser",
                Set.of("shell"),
                Set.of(),
                java.nio.file.Path.of("/tmp/agent-browser")
        ));
        context.setSkillBasePath(java.nio.file.Path.of("/tmp/agent-browser"));

        when(loopOrchestrator.checkStopDecision(any())).thenReturn(StopDecision.continueLoop());
        when(toolRegistry.toOpenAiTools(any(ToolVisibilityResolver.VisibilityRequest.class))).thenReturn(List.of());
        when(toolRegistry.listVisibleToolNames(any())).thenReturn(Set.of());
        when(llmClient.stream(any())).thenReturn(Flux.just(LlmChunk.builder()
                .delta("done")
                .usage(LlmResponse.Usage.builder().promptTokens(1).completionTokens(1).build())
                .done(true)
                .build()));
        when(flushHook.checkAndFlush(any(), any())).thenReturn(false);
        when(defaultDecisionEngine.verify(any(), any(), any())).thenReturn(
                Decision.taskDone(RunOutcome.completed("done"), "done", "item-1")
        );

        runtime.executeLoopWithContext(context, new ArrayList<>(List.of(LlmRequest.Message.user("打开浏览器访问知乎"))));

        assertEquals("agent-browser", context.currentPlanItem().orElseThrow().getSkillName());
    }

    @Test
    @DisplayName("should activate skill bound to current plan item")
    void shouldActivateSkillBoundToCurrentPlanItem() throws Exception {
        AgentRuntime runtime = createRuntime();
        RunContext context = RunContext.create(
                "run-plan-bound-skill", "conn-plan-bound-skill", "session-plan-bound-skill",
                LoopConfig.builder().build(),
                new CancellationManager()
        );
        context.setOriginalInput("打开浏览器访问知乎");
        context.setExecutionPlan(ExecutionPlan.builder()
                .goal("打开浏览器访问知乎")
                .status(ExecutionPlanStatus.ACTIVE)
                .currentItemId("item-1")
                .items(new java.util.ArrayList<>(List.of(
                        PlanItem.builder().id("item-1").title("打开浏览器访问知乎").status(PlanItemStatus.IN_PROGRESS).executionMode(PlanExecutionMode.MAIN_AGENT).skillName("agent-browser").build()
                )))
                .revision(1)
                .build());
        context.setPlanInitialized(true);

        LlmRequest skillRequest = LlmRequest.builder()
                .messages(List.of(LlmRequest.Message.system("skill system"), LlmRequest.Message.user("打开浏览器访问知乎")))
                .build();
        when(contextBuilder.handleSkillActivationByName(eq("agent-browser"), eq("打开浏览器访问知乎"), anyList(), eq(true), anyString()))
                .thenReturn(Optional.of(new ContextBuilder.SkillAwareRequest(
                        skillRequest,
                        "agent-browser",
                        Set.of("shell"),
                        Set.of(),
                        java.nio.file.Path.of("/tmp/agent-browser")
                )));
        when(loopOrchestrator.checkStopDecision(any())).thenReturn(StopDecision.continueLoop());
        when(toolRegistry.toOpenAiTools(any(ToolVisibilityResolver.VisibilityRequest.class))).thenReturn(List.of());
        when(toolRegistry.listVisibleToolNames(any())).thenReturn(Set.of());
        when(llmClient.stream(any())).thenReturn(Flux.just(LlmChunk.builder()
                .delta("done")
                .usage(LlmResponse.Usage.builder().promptTokens(1).completionTokens(1).build())
                .done(true)
                .build()));
        when(flushHook.checkAndFlush(any(), any())).thenReturn(false);
        when(defaultDecisionEngine.verify(any(), any(), any())).thenReturn(
                Decision.taskDone(RunOutcome.completed("done"), "done", "item-1")
        );

        runtime.executeLoopWithContext(context, new ArrayList<>(List.of(LlmRequest.Message.user("打开浏览器访问知乎"))));

        assertEquals("agent-browser", context.getActiveSkill().activeSkillName());
    }


    private AgentRuntime createRuntime() {
        return createRuntime(defaultDecisionEngine, loopOrchestrator);
    }

    private AgentRuntime createRuntime(DecisionEngine verifier) {
        return createRuntime(verifier, loopOrchestrator);
    }

    private AgentRuntime createRuntime(DecisionEngine verifier, LoopOrchestrator orchestrator) {
        return new AgentRuntime(
                new DecisionInputFactory(),
                new OutcomeApplier(eventBus),
                planEngine,
                llmClient,
                toolRegistry,
                toolDispatcher,
                eventBus,
                LoopConfig.builder().build(),
                new CancellationManager(),
                hitlManager,
                contextBuilder,
                flushHook,
                subagentCompletionTracker,
                sessionFileService,
                orchestrator,
                toolExecutor,
                skillActivator,
                subagentBarrier,
                reactEntrySkillSelector,
                verifier,
                policySupervisor
        );
    }
}
