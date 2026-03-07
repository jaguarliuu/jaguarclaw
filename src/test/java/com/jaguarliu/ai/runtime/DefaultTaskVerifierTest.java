package com.jaguarliu.ai.runtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DefaultTaskVerifier Tests")
class DefaultTaskVerifierTest {

    private DefaultTaskVerifier verifier;
    private RunContext context;

    @BeforeEach
    void setUp() {
        verifier = new DefaultTaskVerifier();
        context = RunContext.create(
                "r1", "c1", "s1",
                LoopConfig.builder().build(),
                new CancellationManager()
        );
    }

    @Test
    @DisplayName("should return completed when assistant reply is final")
    void shouldReturnCompletedWhenAssistantReplyIsFinal() {
        VerificationResult result = verifier.verify(context, "Here is the answer", List.of());

        assertTrue(result.terminal());
        assertFalse(result.continueLoop());
        assertEquals(RunOutcomeStatus.COMPLETED, result.outcome().status());
        assertEquals("Here is the answer", result.outcome().message());
    }

    @Test
    @DisplayName("should return blocked by environment for known dependency errors")
    void shouldReturnBlockedByEnvironmentForKnownDependencyErrors() {
        VerificationResult result = verifier.verify(context, null,
                List.of("wkhtmltopdf: command not found"));

        assertTrue(result.terminal());
        assertFalse(result.continueLoop());
        assertEquals(RunOutcomeStatus.BLOCKED_BY_ENVIRONMENT, result.outcome().status());
        assertEquals("environment_missing", result.failureCategory());
    }

    @Test
    @DisplayName("should continue loop when no terminal signal is found")
    void shouldContinueLoopWhenNoTerminalSignalIsFound() {
        VerificationResult result = verifier.verify(context, null, List.of("tool executed"));

        assertFalse(result.terminal());
        assertTrue(result.continueLoop());
    }
}
