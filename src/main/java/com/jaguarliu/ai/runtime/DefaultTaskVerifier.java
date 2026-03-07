package com.jaguarliu.ai.runtime;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认运行时验证器，只保留高置信度规则。
 */
@Component
public class DefaultTaskVerifier implements TaskVerifier {

    @Override
    public VerificationResult verify(RunContext context, String assistantReply, List<String> observations) {
        List<String> signals = new ArrayList<>();
        if (observations != null) {
            signals.addAll(observations);
        }
        if (assistantReply != null && !assistantReply.isBlank()) {
            signals.add(assistantReply);
        }

        for (String signal : signals) {
            String category = RuntimeFailureClassifier.inferFailureCategory(signal);
            if ("environment_missing".equals(category)) {
                return VerificationResult.terminal(
                        RunOutcome.blockedByEnvironment(signal),
                        category
                );
            }
            if ("user_decision_required".equals(category)) {
                return VerificationResult.terminal(
                        new RunOutcome(
                                RunOutcomeStatus.BLOCKED_PENDING_USER_DECISION,
                                "Task requires user decision",
                                signal
                        ),
                        category
                );
            }
        }

        return VerificationResult.continueLoop(null);
    }
}
