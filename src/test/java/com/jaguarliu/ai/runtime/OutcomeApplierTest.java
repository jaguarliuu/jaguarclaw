package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.gateway.events.AgentEvent;
import com.jaguarliu.ai.gateway.events.EventBus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutcomeApplier Tests")
class OutcomeApplierTest {

    @Mock
    private EventBus eventBus;

    @Test
    @DisplayName("should record repair attempt when continue repairable decision is applied")
    void shouldRecordRepairAttemptWhenContinueRepairableDecisionIsApplied() {
        OutcomeApplier applier = new OutcomeApplier(eventBus);
        RunContext context = RunContext.create(
                "run-1", "conn-1", "session-1",
                LoopConfig.builder().build(),
                new CancellationManager()
        );

        String result = applier.apply(
                context,
                Decision.continueWithFeedback(
                        "search for alternative launcher",
                        RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT,
                        "launcher missing"
                ),
                "search for alternative launcher"
        );

        assertEquals("search for alternative launcher", result);
        assertEquals(1, context.snapshotProgress().environmentRepairAttempts());
        assertEquals(RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT, context.snapshotProgress().lastFailureCategory());
    }

    @Test
    @DisplayName("should persist terminal outcome and publish event")
    void shouldPersistTerminalOutcomeAndPublishEvent() {
        OutcomeApplier applier = new OutcomeApplier(eventBus);
        RunContext context = RunContext.create(
                "run-2", "conn-2", "session-2",
                LoopConfig.builder().build(),
                new CancellationManager()
        );
        RunOutcome outcome = RunOutcome.blockedByEnvironment("workspace access denied");

        String result = applier.apply(
                context,
                Decision.terminal(
                        outcome,
                        RuntimeFailureCategories.HARD_ENVIRONMENT_BLOCK,
                        "workspace access denied"
                ),
                "fallback"
        );

        assertEquals("I couldn't continue because the current environment blocked this action. Details: workspace access denied", result);
        assertEquals(outcome, context.getOutcome());

        ArgumentCaptor<AgentEvent> captor = ArgumentCaptor.forClass(AgentEvent.class);
        verify(eventBus, atLeastOnce()).publish(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .anyMatch(event -> event.getType() == AgentEvent.EventType.RUN_OUTCOME));
    }

    @Test
    @DisplayName("should publish terminal reason when failure category is absent")
    void shouldPublishTerminalReasonWhenFailureCategoryIsAbsent() {
        OutcomeApplier applier = new OutcomeApplier(eventBus);
        RunContext context = RunContext.create(
                "run-3", "conn-3", "session-3",
                LoopConfig.builder().build(),
                new CancellationManager()
        );
        RunOutcome outcome = new RunOutcome(
                RunOutcomeStatus.NOT_WORTH_CONTINUING,
                "Task is not worth continuing",
                "Repeated failure category: tool_error"
        );

        String result = applier.apply(
                context,
                Decision.terminal(outcome, null, "repeated_failures"),
                outcome.detail()
        );

        ArgumentCaptor<AgentEvent> captor = ArgumentCaptor.forClass(AgentEvent.class);
        verify(eventBus, atLeastOnce()).publish(captor.capture());
        assertEquals("I stopped here because continuing automatically is unlikely to help. Details: Repeated failure category: tool_error", result);

        AgentEvent outcomeEvent = captor.getAllValues().stream()
                .filter(event -> event.getType() == AgentEvent.EventType.RUN_OUTCOME)
                .findFirst()
                .orElseThrow();
        AgentEvent.RunOutcomeData data = (AgentEvent.RunOutcomeData) outcomeEvent.getData();
        assertEquals("repeated_failures", data.getReason());
    }
}
