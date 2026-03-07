package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.llm.StructuredOutputExecutor;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.llm.model.StructuredLlmResult;
import com.jaguarliu.ai.llm.model.StructuredOutputSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 基于 LLM 的任务验证器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmTaskVerifier implements TaskVerifier {

    private static final Map<String, Object> VERIFIER_DECISION_SCHEMA = buildSchema();

    private final StructuredOutputExecutor structuredOutputExecutor;

    @Override
    public VerificationResult verify(RunContext context, String assistantReply, List<String> observations) {
        boolean hasAssistantReply = assistantReply != null && !assistantReply.isBlank();
        boolean hasObservations = observations != null && observations.stream().anyMatch(item -> item != null && !item.isBlank());
        if (!hasAssistantReply && !hasObservations) {
            return VerificationResult.continueLoop(null);
        }

        try {
            StructuredLlmResult<VerifierDecision> result = structuredOutputExecutor.execute(
                    buildVerifierRequest(context, assistantReply, observations),
                    VerifierDecision.class
            );
            return mapDecision(result != null ? result.getValue() : null, assistantReply);
        } catch (Exception e) {
            log.warn("LLM verifier degraded to safe continue: runId={}, error={}",
                    context.getRunId(), e.getMessage());
            return VerificationResult.continueLoop(null);
        }
    }

    private LlmRequest buildVerifierRequest(RunContext context, String assistantReply, List<String> observations) {
        List<LlmRequest.Message> messages = new ArrayList<>();
        messages.add(LlmRequest.Message.system("""
                You are a runtime verifier for an autonomous agent.
                Decide whether the task is completed, blocked by environment, blocked pending user decision,
                not worth continuing, failed unexpectedly, or should continue.
                Do not mark completed only because the assistant produced a reply.
                Prefer BLOCKED_BY_ENVIRONMENT for missing commands, missing files, permission or sandbox limits.
                Prefer BLOCKED_PENDING_USER_DECISION for paid plans, subscriptions, approvals, login, or user choices.
                Return only the structured decision.
                """.trim()));
        messages.add(LlmRequest.Message.user(buildVerificationPrompt(context, assistantReply, observations)));

        LlmRequest.LlmRequestBuilder builder = LlmRequest.builder()
                .messages(messages)
                .temperature(0.0)
                .maxTokens(400)
                .structuredOutput(StructuredOutputSpec.builder()
                        .name("verifier_decision")
                        .jsonSchema(VERIFIER_DECISION_SCHEMA)
                        .strict(true)
                        .fallbackToPromptJson(true)
                        .build());

        applyModelSelection(builder, context);
        return builder.build();
    }

    private String buildVerificationPrompt(RunContext context, String assistantReply, List<String> observations) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Original task:\n")
                .append(context.getOriginalInput() != null ? context.getOriginalInput() : "")
                .append("\n\n");

        if (assistantReply != null && !assistantReply.isBlank()) {
            prompt.append("Assistant reply:\n")
                    .append(assistantReply)
                    .append("\n\n");
        }

        prompt.append("Observed signals:\n");
        if (observations != null && !observations.isEmpty()) {
            observations.stream()
                    .filter(item -> item != null && !item.isBlank())
                    .forEach(item -> prompt.append("- ").append(item).append("\n"));
        } else {
            prompt.append("- none\n");
        }

        ProgressSnapshot progress = context.snapshotProgress();
        prompt.append("\nProgress snapshot:\n")
                .append("- repeatedFailureCount: ").append(progress.repeatedFailureCount()).append("\n")
                .append("- lastFailureCategory: ").append(progress.lastFailureCategory()).append("\n")
                .append("- lowProgressRounds: ").append(progress.lowProgressRounds()).append("\n")
                .append("- environmentRepairAttempts: ").append(progress.environmentRepairAttempts()).append("\n");

        prompt.append("\nRespond with the structured decision fields only.");
        return prompt.toString();
    }

    private void applyModelSelection(LlmRequest.LlmRequestBuilder builder, RunContext context) {
        if (context.getModelSelection() == null || context.getModelSelection().isBlank()) {
            return;
        }
        String[] parts = context.getModelSelection().split(":", 2);
        if (parts.length == 2) {
            builder.providerId(parts[0]);
            builder.model(parts[1]);
        }
    }

    private VerificationResult mapDecision(VerifierDecision decision, String assistantReply) {
        if (decision == null) {
            return VerificationResult.continueLoop(null);
        }

        RunOutcomeStatus status = parseOutcomeStatus(decision.getOutcome());
        if (Boolean.TRUE.equals(decision.getTerminal()) && status != null) {
            return VerificationResult.terminal(
                    buildOutcome(status, decision, assistantReply),
                    decision.getFailureCategory()
            );
        }

        if (Boolean.TRUE.equals(decision.getShouldContinue())) {
            return VerificationResult.continueLoop(decision.getUserMessage());
        }

        return VerificationResult.continueLoop(null);
    }

    private RunOutcome buildOutcome(RunOutcomeStatus status, VerifierDecision decision, String assistantReply) {
        String detail = decision.getReason();
        String message = decision.getUserMessage();
        if (message == null || message.isBlank()) {
            message = defaultMessage(status, assistantReply);
        }
        return new RunOutcome(status, message, detail);
    }

    private String defaultMessage(RunOutcomeStatus status, String assistantReply) {
        return switch (status) {
            case COMPLETED -> assistantReply != null && !assistantReply.isBlank() ? assistantReply : "Task completed";
            case COMPLETED_WITH_DEGRADATION -> "Task completed with degradation";
            case BLOCKED_BY_ENVIRONMENT -> "Task blocked by environment";
            case BLOCKED_PENDING_USER_DECISION -> "Task requires user decision";
            case NOT_WORTH_CONTINUING -> "Task is not worth continuing";
            case FAILED_UNEXPECTEDLY -> "Task failed unexpectedly";
        };
    }

    private RunOutcomeStatus parseOutcomeStatus(String outcome) {
        if (outcome == null || outcome.isBlank()) {
            return null;
        }
        try {
            return RunOutcomeStatus.valueOf(outcome.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("Unknown verifier outcome status: {}", outcome);
            return null;
        }
    }

    private static Map<String, Object> buildSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("terminal", Map.of("type", "boolean"));
        properties.put("shouldContinue", Map.of("type", "boolean"));
        properties.put("outcome", Map.of(
                "type", "string",
                "enum", Arrays.stream(RunOutcomeStatus.values()).map(Enum::name).toList()
        ));
        properties.put("failureCategory", Map.of("type", "string"));
        properties.put("reason", Map.of("type", "string"));
        properties.put("userMessage", Map.of("type", "string"));
        properties.put("confidence", Map.of("type", "number"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", properties);
        schema.put("required", List.of("terminal", "shouldContinue"));
        return schema;
    }
}
