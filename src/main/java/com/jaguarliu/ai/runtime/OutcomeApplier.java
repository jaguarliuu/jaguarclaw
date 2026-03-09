package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.gateway.events.AgentEvent;
import com.jaguarliu.ai.gateway.events.EventBus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 应用运行时决策副作用。
 */
@Component
@RequiredArgsConstructor
public class OutcomeApplier {

    private final EventBus eventBus;

    public String apply(RunContext context, Decision decision, String fallbackMessage) {
        return apply(context, decision, fallbackMessage, true);
    }

    public String apply(RunContext context, Decision decision, String fallbackMessage, boolean publishOutcomeEvent) {
        if (decision == null) {
            return fallbackMessage;
        }
        if (decision.failureCategory() != null) {
            context.recordFailure(decision.failureCategory(), extractFailureDetail(decision, fallbackMessage));
            if (RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT.equals(decision.failureCategory())
                    && decision.continueLoop()) {
                context.recordEnvironmentRepairAttempt();
            }
        } else if (decision.terminal()) {
            context.recordMeaningfulProgress();
        }
        if (decision.outcome() != null) {
            context.setOutcome(decision.outcome());
            if (publishOutcomeEvent) {
                publishOutcomeEvent(
                        context,
                        decision.outcome(),
                        decision.failureCategory() != null ? decision.failureCategory() : (decision.reason() != null ? decision.reason() : "verifier")
                );
            }
            return renderOutcomeMessage(decision.outcome());
        }
        return fallbackMessage;
    }

    private void publishOutcomeEvent(RunContext context, RunOutcome outcome, String reason) {
        if (outcome == null) {
            return;
        }
        eventBus.publish(AgentEvent.runOutcome(
                context.getConnectionId(),
                context.getRunId(),
                outcome.status().name(),
                reason,
                context.getCurrentStep(),
                context.getTotalTokens()
        ));
    }

    private String renderOutcomeMessage(RunOutcome outcome) {
        return RunOutcomeMessageFormatter.render(outcome);
    }

    private String extractFailureDetail(Decision decision, String fallbackMessage) {
        if (decision == null) {
            return fallbackMessage;
        }
        if (decision.outcome() != null && decision.outcome().detail() != null && !decision.outcome().detail().isBlank()) {
            return decision.outcome().detail();
        }
        if (decision.feedback() != null && !decision.feedback().isBlank()) {
            return decision.feedback();
        }
        if (decision.reason() != null && !decision.reason().isBlank()) {
            return decision.reason();
        }
        return fallbackMessage;
    }
}
