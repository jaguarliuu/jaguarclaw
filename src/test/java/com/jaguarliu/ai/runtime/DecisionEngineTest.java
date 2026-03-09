package com.jaguarliu.ai.runtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DecisionEngine Tests")
class DecisionEngineTest {

    @Mock
    private HardGuardVerifier hardGuardVerifier;

    @Mock
    private LlmRuntimeDecisionStage llmRuntimeDecisionStage;

    private DecisionInputFactory decisionInputFactory;
    private DecisionEngine decisionEngine;
    private RunContext context;

    @BeforeEach
    void setUp() {
        decisionInputFactory = new DecisionInputFactory();
        decisionEngine = new DecisionEngine(decisionInputFactory, hardGuardVerifier, llmRuntimeDecisionStage);
        context = RunContext.create(
                "run-1", "conn-1", "session-1",
                LoopConfig.builder().build(),
                new CancellationManager()
        );
    }

    @Test
    @DisplayName("should short circuit on hard guard terminal decision")
    void shouldShortCircuitOnHardGuardTerminalDecision() {
        when(hardGuardVerifier.verify(any(), any(), any())).thenReturn(
                Decision.terminal(
                        RunOutcome.blockedByEnvironment("workspace access denied"),
                        RuntimeFailureCategories.HARD_ENVIRONMENT_BLOCK,
                        "workspace access denied"
                )
        );

        Decision decision = decisionEngine.decide(context, new DecisionInput(
                null,
                List.of("workspace access denied"),
                Set.of(RuntimeFailureCategories.HARD_ENVIRONMENT_BLOCK),
                context.snapshotProgress(),
                context.getCurrentStep(),
                0,
                true,
                false
        ));

        assertTrue(decision.terminal());
        assertEquals(RunOutcomeStatus.BLOCKED_BY_ENVIRONMENT, decision.outcome().status());
        verify(llmRuntimeDecisionStage, never()).verify(any(), any(), any());
    }

    @Test
    @DisplayName("should delegate repairable round to llm decision")
    void shouldDelegateRepairableRoundToLlmDecision() {
        when(hardGuardVerifier.verify(any(), any(), any()))
                .thenReturn(Decision.continueSilently());
        when(llmRuntimeDecisionStage.verify(any(), any(), any()))
                .thenReturn(Decision.continueWithFeedback(
                        "try alternative launcher",
                        RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT,
                        "launcher missing"
                ));

        Decision decision = decisionEngine.decide(context, new DecisionInput(
                null,
                List.of("launcher unavailable"),
                Set.of(RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT),
                context.snapshotProgress(),
                context.getCurrentStep(),
                0,
                true,
                false
        ));

        assertFalse(decision.terminal());
        assertTrue(decision.continueLoop());
        assertEquals("try alternative launcher", decision.feedback());
        assertEquals(RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT, decision.failureCategory());
        verify(llmRuntimeDecisionStage).verify(any(), any(), any());
    }
}
