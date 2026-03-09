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

    @Test
    @DisplayName("should publish structured outcome payload for blocked environment")
    void shouldPublishStructuredOutcomePayloadForBlockedEnvironment() {
        OutcomeApplier applier = new OutcomeApplier(eventBus);
        RunContext context = RunContext.create(
                "run-4", "conn-4", "session-4",
                LoopConfig.builder().build(),
                new CancellationManager()
        );
        context.setExecutionPlan(ExecutionPlan.builder()
                .goal("open zhihu")
                .status(ExecutionPlanStatus.BLOCKED)
                .currentItemId("item-1")
                .items(new java.util.ArrayList<>(java.util.List.of(
                        PlanItem.builder().id("item-1").title("open zhihu").status(PlanItemStatus.BLOCKED).executionMode(PlanExecutionMode.MAIN_AGENT).build()
                )))
                .revision(1)
                .build());
        context.setPendingQuestion("Please confirm whether to allow access to www.zhihu.com.");

        RunOutcome outcome = RunOutcome.blockedByEnvironment("Domain 'www.zhihu.com' is not in the trusted list.");
        applier.apply(
                context,
                Decision.terminal(outcome, RuntimeFailureCategories.HARD_ENVIRONMENT_BLOCK, "trusted_list_block"),
                "fallback"
        );

        ArgumentCaptor<AgentEvent> captor = ArgumentCaptor.forClass(AgentEvent.class);
        verify(eventBus, atLeastOnce()).publish(captor.capture());
        AgentEvent.RunOutcomeData data = (AgentEvent.RunOutcomeData) captor.getAllValues().stream()
                .filter(event -> event.getType() == AgentEvent.EventType.RUN_OUTCOME)
                .findFirst()
                .orElseThrow()
                .getData();
        assertEquals(RunOutcomeStatus.BLOCKED_BY_ENVIRONMENT.name(), data.getStatus());
        assertTrue(data.getMessage().contains("environment blocked this action"));
        assertTrue(data.getDetail().contains("trusted list"));
        assertEquals("Please confirm whether to allow access to www.zhihu.com.", data.getPendingQuestion());
        assertEquals(ExecutionPlanStatus.BLOCKED.name(), data.getPlanStatus());
        assertEquals("item-1", data.getCurrentItemId());
    }
}
