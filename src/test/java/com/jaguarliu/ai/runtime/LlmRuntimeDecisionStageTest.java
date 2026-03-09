package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.llm.StructuredOutputExecutor;
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
@DisplayName("LlmRuntimeDecisionStage Tests")
class LlmRuntimeDecisionStageTest {

    @Mock
    private StructuredOutputExecutor structuredOutputExecutor;

    private LlmRuntimeDecisionStage verifier;
    private RunContext context;

    @BeforeEach
    void setUp() {
        verifier = new LlmRuntimeDecisionStage(structuredOutputExecutor);
        context = RunContext.create(
                "run-1", "conn-1", "session-1",
                LoopConfig.builder().maxEnvironmentRepairAttempts(2).build(),
                new CancellationManager()
        );
        context.setOriginalInput("请把 README.md 导出成 PDF");
    }

    @Test
    @DisplayName("should map structured LLM decision to blocked by environment")
    void shouldMapStructuredDecisionToBlockedByEnvironment() {
        when(structuredOutputExecutor.execute(any(), eq(VerifierDecision.class))).thenReturn(
                StructuredLlmResult.<VerifierDecision>builder()
                        .value(VerifierDecision.builder()
                                .terminal(true)
                                .shouldContinue(false)
                                .outcome(RunOutcomeStatus.BLOCKED_BY_ENVIRONMENT.name())
                                .failureCategory(RuntimeFailureCategories.HARD_ENVIRONMENT_BLOCK)
                                .reason("workspace is not writable")
                                .userMessage("Task blocked by environment")
                                .confidence(0.98)
                                .build())
                        .rawText("{}")
                        .nativeStructuredOutput(true)
                        .fallbackUsed(false)
                        .build()
        );

        Decision result = verifier.verify(context, null, List.of("workspace is not writable"));

        assertTrue(result.terminal());
        assertEquals(RunOutcomeStatus.BLOCKED_BY_ENVIRONMENT, result.outcome().status());
        assertEquals(RuntimeFailureCategories.HARD_ENVIRONMENT_BLOCK, result.failureCategory());
        assertEquals("workspace is not writable", result.outcome().detail());
    }

    @Test
    @DisplayName("should preserve repairable failure category on continue")
    void shouldPreserveRepairableFailureCategoryOnContinue() {
        when(structuredOutputExecutor.execute(any(), eq(VerifierDecision.class))).thenReturn(
                StructuredLlmResult.<VerifierDecision>builder()
                        .value(VerifierDecision.builder()
                                .terminal(false)
                                .shouldContinue(true)
                                .failureCategory(RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT)
                                .reason("launcher missing but alternatives may exist")
                                .userMessage("search repository for an alternative launcher")
                                .confidence(0.91)
                                .build())
                        .rawText("{}")
                        .nativeStructuredOutput(true)
                        .fallbackUsed(false)
                        .build()
        );

        Decision result = verifier.verify(context, null, List.of("launcher unavailable"));

        assertFalse(result.terminal());
        assertTrue(result.continueLoop());
        assertEquals(RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT, result.failureCategory());
        assertEquals("search repository for an alternative launcher", result.feedback());
    }

    @Test
    @DisplayName("should map structured LLM decision to blocked pending user decision")
    void shouldMapStructuredDecisionToBlockedPendingUserDecision() {
        when(structuredOutputExecutor.execute(any(), eq(VerifierDecision.class))).thenReturn(
                StructuredLlmResult.<VerifierDecision>builder()
                        .value(VerifierDecision.builder()
                                .terminal(true)
                                .shouldContinue(false)
                                .outcome(RunOutcomeStatus.BLOCKED_PENDING_USER_DECISION.name())
                                .failureCategory(RuntimeFailureCategories.USER_DECISION_REQUIRED)
                                .reason("RSS.app requires a paid plan")
                                .userMessage("Task requires user decision")
                                .confidence(0.96)
                                .build())
                        .rawText("{}")
                        .nativeStructuredOutput(true)
                        .fallbackUsed(false)
                        .build()
        );

        Decision result = verifier.verify(context, null, List.of("RSS.app requires paid plan"));

        assertTrue(result.terminal());
        assertEquals(RunOutcomeStatus.BLOCKED_PENDING_USER_DECISION, result.outcome().status());
        assertEquals(RuntimeFailureCategories.USER_DECISION_REQUIRED, result.failureCategory());
    }

    @Test
    @DisplayName("should degrade to continue when provider or parsing fails")
    void shouldDegradeToContinueWhenProviderFails() {
        when(structuredOutputExecutor.execute(any(), eq(VerifierDecision.class)))
                .thenThrow(new RuntimeException("provider unavailable"));

        Decision result = verifier.verify(context, null, List.of("tool failed"));

        assertFalse(result.terminal());
        assertTrue(result.continueLoop());
    }
}
