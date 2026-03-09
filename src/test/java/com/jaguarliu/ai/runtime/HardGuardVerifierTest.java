package com.jaguarliu.ai.runtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("HardGuardVerifier Tests")
class HardGuardVerifierTest {

    private HardGuardVerifier verifier;
    private RunContext context;

    @BeforeEach
    void setUp() {
        verifier = new HardGuardVerifier();
        context = RunContext.create(
                "r1", "c1", "s1",
                LoopConfig.builder().build(),
                new CancellationManager()
        );
    }

    @Test
    @DisplayName("should not auto complete only because assistant replied")
    void shouldNotAutoCompleteOnlyBecauseAssistantReplied() {
        Decision result = verifier.verify(context, "Here is the answer", List.of());

        assertFalse(result.terminal());
        assertTrue(result.continueLoop());
    }

    @Test
    @DisplayName("should not decide repairable environment strategy")
    void shouldNotDecideRepairableEnvironmentStrategy() {
        context.replaceRuntimeFailureCategories(List.of(RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT));

        Decision result = verifier.verify(context, null,
                List.of("agent-browser launcher is unavailable"));

        assertFalse(result.terminal());
        assertTrue(result.continueLoop());
        assertNull(result.failureCategory());
    }

    @Test
    @DisplayName("should return blocked by environment for hard environment block")
    void shouldReturnBlockedByEnvironmentForHardEnvironmentBlock() {
        context.replaceRuntimeFailureCategories(List.of(RuntimeFailureCategories.HARD_ENVIRONMENT_BLOCK));

        Decision result = verifier.verify(context, null,
                List.of("workspace access denied"));

        assertTrue(result.terminal());
        assertFalse(result.continueLoop());
        assertEquals(RunOutcomeStatus.BLOCKED_BY_ENVIRONMENT, result.outcome().status());
        assertEquals(RuntimeFailureCategories.HARD_ENVIRONMENT_BLOCK, result.failureCategory());
    }

    @Test
    @DisplayName("should return blocked pending user decision for user decision signals")
    void shouldReturnBlockedPendingUserDecisionForUserDecisionSignals() {
        context.replaceRuntimeFailureCategories(List.of(RuntimeFailureCategories.USER_DECISION_REQUIRED));

        Decision result = verifier.verify(context, null,
                List.of("approval required"));

        assertTrue(result.terminal());
        assertEquals(RunOutcomeStatus.BLOCKED_PENDING_USER_DECISION, result.outcome().status());
        assertEquals(RuntimeFailureCategories.USER_DECISION_REQUIRED, result.failureCategory());
    }

    @Test
    @DisplayName("should continue loop when no terminal signal is found")
    void shouldContinueLoopWhenNoTerminalSignalIsFound() {
        Decision result = verifier.verify(context, null, List.of("tool executed"));

        assertFalse(result.terminal());
        assertTrue(result.continueLoop());
    }
}
