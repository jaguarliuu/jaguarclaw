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
import java.util.stream.Collectors;

/**
 * 基于 LLM 的任务验证器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmRuntimeDecisionStage implements RuntimeDecisionStage {

    private static final Map<String, Object> VERIFIER_DECISION_SCHEMA = buildSchema();

    private final StructuredOutputExecutor structuredOutputExecutor;

    @Override
    public Decision verify(RunContext context, String assistantReply, List<String> observations) {
        boolean hasAssistantReply = assistantReply != null && !assistantReply.isBlank();
        boolean hasObservations = observations != null && observations.stream().anyMatch(item -> item != null && !item.isBlank());
        if (!hasAssistantReply && !hasObservations) {
            return Decision.continueSilently();
        }

        try {
            StructuredLlmResult<VerifierDecision> result = structuredOutputExecutor.execute(
                    buildVerifierRequest(context, assistantReply, observations),
                    VerifierDecision.class
            );
            return mapDecision(context, result != null ? result.getValue() : null, assistantReply);
        } catch (Exception e) {
            log.warn("LLM verifier degraded to safe continue: runId={}, error={}",
                    context.getRunId(), e.getMessage());
            return Decision.continueSilently();
        }
    }

    private LlmRequest buildVerifierRequest(RunContext context, String assistantReply, List<String> observations) {
        List<LlmRequest.Message> messages = new ArrayList<>();
        messages.add(LlmRequest.Message.system("""
                You are a runtime verifier for an autonomous agent.
                Supervise execution item-by-item for a long-running task.
                Decide whether the current plan item should continue, be marked done, ask the user,
                be blocked, be delegated, complete the whole task, or stop.
                Do not mark the whole task done unless the execution plan is effectively complete.
                Use structured runtime failure categories as the primary signal when they are available.
                Return only the structured decision.
                """.trim()));
        messages.add(LlmRequest.Message.user(buildVerificationPrompt(context, assistantReply, observations)));

        LlmRequest.LlmRequestBuilder builder = LlmRequest.builder()
                .messages(messages)
                .temperature(0.0)
                .maxTokens(500)
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

        if (context.hasExecutionPlan()) {
            ExecutionPlan plan = context.getExecutionPlan();
            prompt.append("Execution plan status:\n")
                    .append("- planStatus: ").append(plan.getStatus()).append("\n")
                    .append("- currentItemId: ").append(plan.getCurrentItemId()).append("\n")
                    .append("- remainingItems: ").append(plan.remainingItemsCount()).append("\n")
                    .append("- blockedItems: ").append(plan.blockedItemsCount()).append("\n");
            context.currentPlanItem().ifPresent(item -> prompt.append("- currentItemTitle: ").append(item.getTitle()).append("\n"));
            String completed = plan.getItems().stream()
                    .filter(item -> item.getStatus() == PlanItemStatus.DONE)
                    .map(PlanItem::getTitle)
                    .collect(Collectors.joining(", "));
            String remaining = plan.getItems().stream()
                    .filter(item -> item.getStatus() != PlanItemStatus.DONE && item.getStatus() != PlanItemStatus.CANCELLED)
                    .map(item -> item.getId() + ":" + item.getTitle() + "(" + item.getStatus() + ")")
                    .collect(Collectors.joining(", "));
            prompt.append("- completedItems: ").append(completed.isBlank() ? "none" : completed).append("\n")
                    .append("- remainingItemSummary: ").append(remaining.isBlank() ? "none" : remaining).append("\n\n");
        }

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
                .append("- environmentRepairAttempts: ").append(progress.environmentRepairAttempts()).append("\n")
                .append("- maxEnvironmentRepairAttempts: ").append(context.getConfig().getMaxEnvironmentRepairAttempts()).append("\n");

        prompt.append("\nStructured runtime failure categories:\n");
        if (context.getRuntimeFailureCategories().isEmpty()) {
            prompt.append("- none\n");
        } else {
            context.getRuntimeFailureCategories()
                    .forEach(category -> prompt.append("- ").append(category).append("\n"));
        }

        prompt.append("\nPrefer ITEM_DONE over TASK_DONE when the current item is done but plan work remains.\n");
        prompt.append("Respond with the structured decision fields only.");
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

    private Decision mapDecision(RunContext context, VerifierDecision decision, String assistantReply) {
        if (decision == null) {
            return Decision.continueSilently();
        }

        DecisionAction action = parseAction(decision.getAction());
        if (action != null) {
            return switch (action) {
                case CONTINUE_ITEM -> Decision.continueWithFeedback(decision.getUserMessage(), decision.getFailureCategory(), decision.getReason());
                case ITEM_DONE -> Decision.itemDone(decision.getReason(), decision.getTargetItemId());
                case ASK_USER -> Decision.askUser(decision.getUserMessage(), decision.getFailureCategory(), decision.getReason(), decision.getTargetItemId());
                case BLOCK_ITEM -> Decision.blockItem(decision.getUserMessage(), decision.getFailureCategory(), decision.getReason(), decision.getTargetItemId());
                case DELEGATE_ITEM -> Decision.delegateItem(decision.getUserMessage(), decision.getReason(), decision.getTargetItemId());
                case TASK_DONE -> Decision.taskDone(buildOutcome(RunOutcomeStatus.COMPLETED, decision, assistantReply), decision.getReason(), decision.getTargetItemId());
                case STOP -> Decision.terminal(buildOutcome(parseOutcomeStatus(decision.getOutcome()) != null ? parseOutcomeStatus(decision.getOutcome()) : RunOutcomeStatus.NOT_WORTH_CONTINUING, decision, assistantReply), decision.getFailureCategory(), decision.getReason());
            };
        }

        RunOutcomeStatus status = parseOutcomeStatus(decision.getOutcome());
        if (Boolean.TRUE.equals(decision.getTerminal()) && status != null) {
            return Decision.terminal(
                    buildOutcome(status, decision, assistantReply),
                    decision.getFailureCategory(),
                    decision.getReason()
            );
        }

        if (Boolean.TRUE.equals(decision.getShouldContinue())) {
            return Decision.continueWithFeedback(decision.getUserMessage(), decision.getFailureCategory(), decision.getReason());
        }

        if (context.hasExecutionPlan() && context.getExecutionPlan().allItemsDone()) {
            return Decision.taskDone(RunOutcome.completed(defaultMessage(RunOutcomeStatus.COMPLETED, assistantReply)), decision.getReason(), decision.getTargetItemId());
        }
        return Decision.continueSilently();
    }

    private DecisionAction parseAction(String action) {
        if (action == null || action.isBlank()) {
            return null;
        }
        try {
            return DecisionAction.valueOf(action.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("Unknown verifier action: {}", action);
            return null;
        }
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
        properties.put("action", Map.of(
                "type", "string",
                "enum", Arrays.stream(DecisionAction.values()).map(Enum::name).toList()
        ));
        properties.put("outcome", Map.of(
                "type", "string",
                "enum", Arrays.stream(RunOutcomeStatus.values()).map(Enum::name).toList()
        ));
        properties.put("failureCategory", Map.of("type", "string"));
        properties.put("reason", Map.of("type", "string"));
        properties.put("userMessage", Map.of("type", "string"));
        properties.put("targetItemId", Map.of("type", "string"));
        properties.put("confidence", Map.of("type", "number"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", properties);
        schema.put("required", List.of("terminal", "shouldContinue"));
        return schema;
    }
}
