package com.jaguarliu.ai.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Decision Tests")
class DecisionTest {

    @Test
    @DisplayName("should create continue decision with feedback")
    void shouldCreateContinueDecisionWithFeedback() {
        Decision decision = Decision.continueWithFeedback(
                "try alternative launcher",
                RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT,
                "launcher missing"
        );

        assertFalse(decision.terminal());
        assertTrue(decision.continueLoop());
        assertEquals("try alternative launcher", decision.feedback());
        assertEquals(RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT, decision.failureCategory());
        assertEquals("launcher missing", decision.reason());
        assertNull(decision.outcome());
    }

    @Test
    @DisplayName("should create terminal decision with outcome")
    void shouldCreateTerminalDecisionWithOutcome() {
        RunOutcome outcome = RunOutcome.blockedByEnvironment("workspace access denied");

        Decision decision = Decision.terminal(
                outcome,
                RuntimeFailureCategories.HARD_ENVIRONMENT_BLOCK,
                "workspace access denied"
        );

        assertTrue(decision.terminal());
        assertFalse(decision.continueLoop());
        assertEquals(outcome, decision.outcome());
        assertEquals(RuntimeFailureCategories.HARD_ENVIRONMENT_BLOCK, decision.failureCategory());
        assertEquals("workspace access denied", decision.reason());
    }
}
