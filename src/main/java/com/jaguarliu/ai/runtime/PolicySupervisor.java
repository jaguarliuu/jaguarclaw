package com.jaguarliu.ai.runtime;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 运行时 fallback policy，只处理高置信硬信号。
 */
@Component
public class PolicySupervisor {

    public PolicyDecision evaluate(String userPrompt, List<String> observations) {
        if (observations != null) {
            for (String observation : observations) {
                String category = RuntimeFailureClassifier.inferFailureCategory(observation);
                if ("environment_missing".equals(category)) {
                    return PolicyDecision.blocked(
                            TaskComplexity.EXTERNAL_DEPENDENCY,
                            RunOutcome.blockedByEnvironment(observation),
                            userPrompt
                    );
                }
                if ("user_decision_required".equals(category)) {
                    return PolicyDecision.blocked(
                            TaskComplexity.EXTERNAL_DEPENDENCY,
                            new RunOutcome(
                                    RunOutcomeStatus.BLOCKED_PENDING_USER_DECISION,
                                    "Task requires user decision",
                                    observation
                            ),
                            userPrompt
                    );
                }
            }
        }
        return PolicyDecision.heavy(userPrompt == null ? "" : userPrompt.trim());
    }
}
