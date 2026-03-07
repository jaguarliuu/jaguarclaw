package com.jaguarliu.ai.runtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompositeTaskVerifier Tests")
class CompositeTaskVerifierTest {

    @Mock
    private DefaultTaskVerifier defaultTaskVerifier;

    @Mock
    private LlmTaskVerifier llmTaskVerifier;

    private CompositeTaskVerifier verifier;
    private RunContext context;

    @BeforeEach
    void setUp() {
        verifier = new CompositeTaskVerifier(defaultTaskVerifier, llmTaskVerifier);
        context = RunContext.create(
                "run-1", "conn-1", "session-1",
                LoopConfig.builder().build(),
                new CancellationManager()
        );
    }

    @Test
    @DisplayName("should short circuit when rule verifier returns terminal outcome")
    void shouldShortCircuitWhenRuleVerifierReturnsTerminalOutcome() {
        VerificationResult ruleResult = VerificationResult.terminal(
                RunOutcome.blockedByEnvironment("wkhtmltopdf missing"),
                "environment_missing"
        );
        when(defaultTaskVerifier.verify(any(), any(), any())).thenReturn(ruleResult);

        VerificationResult result = verifier.verify(context, null, List.of("wkhtmltopdf: command not found"));

        assertTrue(result.terminal());
        assertEquals(RunOutcomeStatus.BLOCKED_BY_ENVIRONMENT, result.outcome().status());
        verify(llmTaskVerifier, never()).verify(any(), any(), any());
    }

    @Test
    @DisplayName("should use llm verifier when rule verifier cannot resolve terminal outcome")
    void shouldUseLlmVerifierWhenRuleVerifierCannotResolve() {
        context.recordFailure("tool_error");
        when(defaultTaskVerifier.verify(any(), any(), any()))
                .thenReturn(VerificationResult.continueLoop(null));
        when(llmTaskVerifier.verify(any(), any(), any())).thenReturn(
                VerificationResult.terminal(
                        new RunOutcome(RunOutcomeStatus.BLOCKED_PENDING_USER_DECISION,
                                "Task requires user decision",
                                "RSS.app requires paid plan"),
                        "user_decision_required"
                )
        );

        VerificationResult result = verifier.verify(context, null, List.of("RSS.app requires paid plan"));

        assertTrue(result.terminal());
        assertEquals(RunOutcomeStatus.BLOCKED_PENDING_USER_DECISION, result.outcome().status());
    }

    @Test
    @DisplayName("should skip llm verifier for successful tool rounds without risk signals")
    void shouldSkipLlmVerifierForSuccessfulToolRoundsWithoutRiskSignals() {
        when(defaultTaskVerifier.verify(any(), any(), any()))
                .thenReturn(VerificationResult.continueLoop(null));

        VerificationResult result = verifier.verify(context, null, List.of("tool executed successfully"));

        assertFalse(result.terminal());
        assertTrue(result.continueLoop());
        verify(llmTaskVerifier, never()).verify(any(), any(), any());
    }
}
