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
    @DisplayName("should not auto complete only because assistant replied")
    void shouldNotAutoCompleteOnlyBecauseAssistantReplied() {
        VerificationResult result = verifier.verify(context, "Here is the answer", List.of());

        assertFalse(result.terminal());
        assertTrue(result.continueLoop());
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
    @DisplayName("should return blocked by environment for localized windows errors")
    void shouldReturnBlockedByEnvironmentForLocalizedWindowsErrors() {
        VerificationResult result = verifier.verify(context, null,
                List.of("'wkhtmltopdf' 不是内部或外部命令，也不是可运行的程序"));

        assertTrue(result.terminal());
        assertEquals(RunOutcomeStatus.BLOCKED_BY_ENVIRONMENT, result.outcome().status());
    }

    @Test
    @DisplayName("should return blocked by environment for missing file signals")
    void shouldReturnBlockedByEnvironmentForMissingFileSignals() {
        VerificationResult result = verifier.verify(context, null,
                List.of("Error: File not found: README.md"));

        assertTrue(result.terminal());
        assertEquals(RunOutcomeStatus.BLOCKED_BY_ENVIRONMENT, result.outcome().status());
    }

    @Test
    @DisplayName("should continue loop when no terminal signal is found")
    void shouldContinueLoopWhenNoTerminalSignalIsFound() {
        VerificationResult result = verifier.verify(context, null, List.of("tool executed"));

        assertFalse(result.terminal());
        assertTrue(result.continueLoop());
    }
}
