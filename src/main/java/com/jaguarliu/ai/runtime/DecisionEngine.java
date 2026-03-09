package com.jaguarliu.ai.runtime;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 统一运行时决策引擎。
 */
@Component
@Primary
@RequiredArgsConstructor
public class DecisionEngine implements RuntimeDecisionStage {

    private final DecisionInputFactory decisionInputFactory;
    private final HardGuardVerifier hardGuardVerifier;
    private final LlmRuntimeDecisionStage llmRuntimeDecisionStage;

    @Override
    public Decision verify(RunContext context, String assistantReply, List<String> observations) {
        return decide(context, decisionInputFactory.fromVerifierInput(context, assistantReply, observations));
    }

    public Decision decide(RunContext context, DecisionInput input) {
        Decision guardDecision = hardGuardVerifier.verify(
                context,
                input != null ? input.assistantReply() : null,
                input != null ? input.observations() : null
        );
        if (guardDecision.terminal()) {
            return guardDecision;
        }
        if (!shouldConsultLlm(input)) {
            return guardDecision;
        }

        Decision llmDecision = llmRuntimeDecisionStage.verify(
                context,
                input.assistantReply(),
                input.observations()
        );
        Decision resolved = llmDecision != null ? llmDecision : guardDecision;
        return softenRecoverableFailureDecision(context, input, resolved);
    }

    boolean shouldConsultLlm(DecisionInput input) {
        if (input == null) {
            return false;
        }
        if (input.assistantReply() != null && !input.assistantReply().isBlank()) {
            return true;
        }
        if (input.observations() != null && input.observations().stream().anyMatch(item -> item != null && !item.isBlank())) {
            return true;
        }
        return input.runtimeFailureCategories() != null && !input.runtimeFailureCategories().isEmpty();
    }

    private Decision softenRecoverableFailureDecision(RunContext context, DecisionInput input, Decision decision) {
        if (decision == null || !isRecoverableFailureRound(context, input) || !shouldOverrideTerminalLikeDecision(decision)) {
            return decision;
        }
        String failureCategory = preferredRecoverableFailureCategory(input, decision);
        return Decision.continueWithFeedback(
                buildRecoverableFailureFeedback(failureCategory, decision),
                failureCategory,
                decision.reason()
        );
    }

    private boolean isRecoverableFailureRound(RunContext context, DecisionInput input) {
        if (input == null || input.runtimeFailureCategories() == null || input.runtimeFailureCategories().isEmpty()) {
            return false;
        }
        if (input.runtimeFailureCategories().contains(RuntimeFailureCategories.HARD_ENVIRONMENT_BLOCK)
                || input.runtimeFailureCategories().contains(RuntimeFailureCategories.USER_DECISION_REQUIRED)
                || input.runtimeFailureCategories().contains(RuntimeFailureCategories.POLICY_BLOCK)
                || input.runtimeFailureCategories().contains(RuntimeFailureCategories.HITL_REJECTED)) {
            return false;
        }
        if (input.runtimeFailureCategories().contains(RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT)) {
            int attempts = input.environmentRepairAttempts();
            int maxAttempts = context != null && context.getConfig() != null
                    ? context.getConfig().getMaxEnvironmentRepairAttempts()
                    : 0;
            return maxAttempts <= 0 || attempts < maxAttempts;
        }
        if (input.runtimeFailureCategories().contains(RuntimeFailureCategories.TOOL_ERROR)) {
            int repeatedFailures = input.progressSnapshot() != null ? input.progressSnapshot().repeatedFailureCount() : 0;
            int maxRepeatedFailures = context != null && context.getConfig() != null
                    ? context.getConfig().getMaxRepeatedFailures()
                    : 0;
            return maxRepeatedFailures <= 0 || repeatedFailures < maxRepeatedFailures;
        }
        return false;
    }

    private boolean shouldOverrideTerminalLikeDecision(Decision decision) {
        if (decision == null) {
            return false;
        }
        if (decision.action() == DecisionAction.CONTINUE_ITEM
                || decision.action() == DecisionAction.ITEM_DONE
                || decision.action() == DecisionAction.DELEGATE_ITEM) {
            return false;
        }
        if (RuntimeFailureCategories.HARD_ENVIRONMENT_BLOCK.equals(decision.failureCategory())
                || RuntimeFailureCategories.USER_DECISION_REQUIRED.equals(decision.failureCategory())
                || RuntimeFailureCategories.POLICY_BLOCK.equals(decision.failureCategory())
                || RuntimeFailureCategories.HITL_REJECTED.equals(decision.failureCategory())) {
            return false;
        }
        return true;
    }

    private String preferredRecoverableFailureCategory(DecisionInput input, Decision decision) {
        if (input != null && input.runtimeFailureCategories() != null
                && input.runtimeFailureCategories().contains(RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT)) {
            return RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT;
        }
        if (decision != null && RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT.equals(decision.failureCategory())) {
            return RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT;
        }
        return RuntimeFailureCategories.TOOL_ERROR;
    }

    private String buildRecoverableFailureFeedback(String failureCategory, Decision decision) {
        String prefix = RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT.equals(failureCategory)
                ? "The last tool call failed due to a repairable environment or runtime issue. Diagnose the root cause, attempt a repair or safe fallback, and continue execution."
                : "The last tool call failed. Analyze the failure, adjust the approach, and continue execution.";
        String detail = extractFailureDetail(decision);
        if (detail == null || detail.isBlank()) {
            return prefix + " Do not end the task after a single tool failure.";
        }
        return prefix + " Failure detail: " + detail + ". Do not end the task after a single tool failure.";
    }

    private String extractFailureDetail(Decision decision) {
        if (decision == null) {
            return null;
        }
        if (decision.reason() != null && !decision.reason().isBlank()) {
            return decision.reason().trim();
        }
        if (decision.outcome() != null && decision.outcome().detail() != null && !decision.outcome().detail().isBlank()) {
            return decision.outcome().detail().trim();
        }
        if (decision.feedback() != null && !decision.feedback().isBlank()) {
            return decision.feedback().trim();
        }
        return null;
    }
}
