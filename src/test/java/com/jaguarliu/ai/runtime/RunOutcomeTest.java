package com.jaguarliu.ai.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("RunOutcome Tests")
class RunOutcomeTest {

    @Test
    @DisplayName("should create completed outcome")
    void shouldCreateCompletedOutcome() {
        RunOutcome outcome = RunOutcome.completed("done");

        assertEquals(RunOutcomeStatus.COMPLETED, outcome.status());
        assertEquals("done", outcome.message());
        assertNull(outcome.detail());
    }

    @Test
    @DisplayName("should create blocked by environment outcome")
    void shouldCreateBlockedByEnvironmentOutcome() {
        RunOutcome outcome = RunOutcome.blockedByEnvironment("missing wkhtmltopdf");

        assertEquals(RunOutcomeStatus.BLOCKED_BY_ENVIRONMENT, outcome.status());
        assertEquals("Task blocked by environment", outcome.message());
        assertEquals("missing wkhtmltopdf", outcome.detail());
    }
}
