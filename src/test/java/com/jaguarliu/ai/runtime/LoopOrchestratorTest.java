package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.gateway.events.EventBus;
import com.jaguarliu.ai.llm.model.LlmResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("LoopOrchestrator Tests")
class LoopOrchestratorTest {

    private final EventBus eventBus = mock(EventBus.class);
    private final LoopOrchestrator orchestrator = new LoopOrchestrator(eventBus);

    @Test
    @DisplayName("should stop when repeated failure budget is exceeded")
    void shouldStopWhenRepeatedFailureBudgetIsExceeded() {
        RunContext context = RunContext.create(
                "r1", "c1", "s1",
                LoopConfig.builder().maxRepeatedFailures(2).build(),
                new CancellationManager()
        );
        context.recordFailure("environment_missing");
        context.recordFailure("environment_missing");

        StopDecision decision = orchestrator.checkStopDecision(context);

        assertTrue(decision.stop());
        assertEquals(RunOutcomeStatus.NOT_WORTH_CONTINUING, decision.outcome().status());
    }

    @Test
    @DisplayName("should stop when token budget is exceeded")
    void shouldStopWhenTokenBudgetIsExceeded() {
        RunContext context = RunContext.create(
                "r1", "c1", "s1",
                LoopConfig.builder().maxTokens(100).build(),
                new CancellationManager()
        );
        context.addUsage(LlmResponse.Usage.builder()
                .promptTokens(90)
                .completionTokens(20)
                .build());

        StopDecision decision = orchestrator.checkStopDecision(context);

        assertTrue(decision.stop());
        assertEquals(RunOutcomeStatus.NOT_WORTH_CONTINUING, decision.outcome().status());
    }

    @Test
    @DisplayName("should continue when no budget is exhausted")
    void shouldContinueWhenNoBudgetIsExhausted() {
        RunContext context = RunContext.create(
                "r1", "c1", "s1",
                LoopConfig.builder().maxRepeatedFailures(3).maxTokens(1000).build(),
                new CancellationManager()
        );

        StopDecision decision = orchestrator.checkStopDecision(context);

        assertFalse(decision.stop());
        assertEquals("continue", decision.reason());
    }
}
