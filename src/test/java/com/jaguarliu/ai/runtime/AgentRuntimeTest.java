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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentRuntime Tests")
class AgentRuntimeTest {

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
    @Mock private SubagentBarrier subagentBarrier;
    @Mock private TaskVerifier taskVerifier;
    @Mock private PolicySupervisor policySupervisor;
    @Mock private LlmTaskVerifier llmTaskVerifier;

    @Test
    @DisplayName("should detect HITL rejection marker from tool result")
    void shouldDetectHitlRejectionMarker() {
        ToolResult result = ToolResult.error(ToolExecutor.HITL_REJECTED_MARKER + ": rejected");
        assertTrue(AgentRuntime.isHitlRejectedResult(result));
    }

    @Test
    @DisplayName("should not treat normal tool errors as HITL rejection")
    void shouldIgnoreGenericToolErrors() {
        ToolResult result = ToolResult.error("Command blocked by safety policy");
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
                        "environment_missing"
                )
        ));
        when(taskVerifier.verify(any(), any(), any())).thenReturn(
                VerificationResult.terminal(
                        RunOutcome.blockedByEnvironment("wkhtmltopdf: command not found"),
                        "environment_missing"
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
    @DisplayName("should surface README missing as blocked by environment through composite verifier")
    void shouldSurfaceReadmeMissingAsBlockedByEnvironmentThroughCompositeVerifier() throws Exception {
        TaskVerifier compositeVerifier = new CompositeTaskVerifier(new DefaultTaskVerifier(), llmTaskVerifier);
        AgentRuntime runtime = createRuntime(compositeVerifier);
        RunContext context = RunContext.create(
                "run-3", "conn-3", "session-3",
                LoopConfig.builder().build(),
                new CancellationManager()
        );
        context.setOriginalInput("请用 wkhtmltopdf 把 README.md 导出成 PDF");

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
                        ToolResult.error("Error: File not found: README.md"),
                        "tool_error"
                )
        ));
        when(flushHook.checkAndFlush(any(), any())).thenReturn(false);

        String result = runtime.executeLoopWithContext(context,
                new ArrayList<>(List.of(LlmRequest.Message.user("请用 wkhtmltopdf 把 README.md 导出成 PDF"))));

        assertTrue(result.contains("README.md"));
        assertTrue(context.hasOutcome());
        assertEquals(RunOutcomeStatus.BLOCKED_BY_ENVIRONMENT, context.getOutcome().status());
        verify(llmTaskVerifier, never()).verify(any(), any(), any());
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
                        "tool_error"
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
        AgentRuntime runtime = createRuntime(taskVerifier, realLoopOrchestrator);
        RunContext context = RunContext.create(
                "run-2", "conn-2", "session-2",
                LoopConfig.builder().maxRepeatedFailures(2).build(),
                new CancellationManager()
        );
        context.recordFailure("environment_missing");
        context.recordFailure("environment_missing");

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

    private AgentRuntime createRuntime() {
        return createRuntime(taskVerifier, loopOrchestrator);
    }

    private AgentRuntime createRuntime(TaskVerifier verifier) {
        return createRuntime(verifier, loopOrchestrator);
    }

    private AgentRuntime createRuntime(TaskVerifier verifier, LoopOrchestrator orchestrator) {
        return new AgentRuntime(
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
                verifier,
                policySupervisor
        );
    }
}
