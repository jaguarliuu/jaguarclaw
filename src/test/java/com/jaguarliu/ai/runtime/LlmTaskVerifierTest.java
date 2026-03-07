package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.llm.LlmClient;
import com.jaguarliu.ai.llm.model.StructuredLlmResult;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LlmTaskVerifier Tests")
class LlmTaskVerifierTest {

    @Mock
    private LlmClient llmClient;

    private LlmTaskVerifier verifier;
    private RunContext context;

    @BeforeEach
    void setUp() {
        verifier = new LlmTaskVerifier(llmClient);
        context = RunContext.create(
                "run-1", "conn-1", "session-1",
                LoopConfig.builder().build(),
                new CancellationManager()
        );
        context.setOriginalInput("请把 README.md 导出成 PDF");
    }

    @Test
    @DisplayName("should map structured LLM decision to blocked by environment")
    void shouldMapStructuredDecisionToBlockedByEnvironment() {
        when(llmClient.structured(any(), eq(VerifierDecision.class))).thenReturn(
                StructuredLlmResult.<VerifierDecision>builder()
                        .value(VerifierDecision.builder()
                                .terminal(true)
                                .shouldContinue(false)
                                .outcome(RunOutcomeStatus.BLOCKED_BY_ENVIRONMENT.name())
                                .failureCategory("environment_missing")
                                .reason("wkhtmltopdf is not installed")
                                .userMessage("Task blocked by environment")
                                .confidence(0.98)
                                .build())
                        .rawText("{}")
                        .nativeStructuredOutput(true)
                        .fallbackUsed(false)
                        .build()
        );

        VerificationResult result = verifier.verify(context, null, List.of("wkhtmltopdf: command not found"));

        assertTrue(result.terminal());
        assertEquals(RunOutcomeStatus.BLOCKED_BY_ENVIRONMENT, result.outcome().status());
        assertEquals("environment_missing", result.failureCategory());
        assertEquals("wkhtmltopdf is not installed", result.outcome().detail());
    }

    @Test
    @DisplayName("should map structured LLM decision to blocked pending user decision")
    void shouldMapStructuredDecisionToBlockedPendingUserDecision() {
        when(llmClient.structured(any(), eq(VerifierDecision.class))).thenReturn(
                StructuredLlmResult.<VerifierDecision>builder()
                        .value(VerifierDecision.builder()
                                .terminal(true)
                                .shouldContinue(false)
                                .outcome(RunOutcomeStatus.BLOCKED_PENDING_USER_DECISION.name())
                                .failureCategory("user_decision_required")
                                .reason("RSS.app requires a paid plan")
                                .userMessage("Task requires user decision")
                                .confidence(0.96)
                                .build())
                        .rawText("{}")
                        .nativeStructuredOutput(true)
                        .fallbackUsed(false)
                        .build()
        );

        VerificationResult result = verifier.verify(context, null, List.of("RSS.app requires paid plan"));

        assertTrue(result.terminal());
        assertEquals(RunOutcomeStatus.BLOCKED_PENDING_USER_DECISION, result.outcome().status());
        assertEquals("user_decision_required", result.failureCategory());
    }

    @Test
    @DisplayName("should degrade to continue when provider or parsing fails")
    void shouldDegradeToContinueWhenProviderFails() {
        when(llmClient.structured(any(), eq(VerifierDecision.class)))
                .thenThrow(new RuntimeException("provider unavailable"));

        VerificationResult result = verifier.verify(context, null, List.of("tool failed"));

        assertFalse(result.terminal());
        assertTrue(result.continueLoop());
    }
}
