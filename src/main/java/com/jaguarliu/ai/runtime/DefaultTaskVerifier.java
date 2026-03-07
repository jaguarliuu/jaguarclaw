package com.jaguarliu.ai.runtime;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 默认运行时验证器，优先处理显式终态与常见环境阻塞。
 */
@Component
public class DefaultTaskVerifier implements TaskVerifier {

    @Override
    public VerificationResult verify(RunContext context, String assistantReply, List<String> observations) {
        if (assistantReply != null && !assistantReply.isBlank()) {
            return VerificationResult.completed(assistantReply);
        }

        if (observations != null) {
            for (String observation : observations) {
                if (observation == null || observation.isBlank()) {
                    continue;
                }
                String normalized = observation.toLowerCase(Locale.ROOT);
                if (normalized.contains("command not found")
                        || normalized.contains("not installed")
                        || normalized.contains("missing ")
                        || normalized.contains("permission denied")
                        || normalized.contains("login required")) {
                    return VerificationResult.terminal(
                            RunOutcome.blockedByEnvironment(observation),
                            "environment_missing"
                    );
                }
                if (normalized.contains("paid plan")
                        || normalized.contains("subscription")
                        || normalized.contains("requires payment")) {
                    return VerificationResult.terminal(
                            new RunOutcome(
                                    RunOutcomeStatus.BLOCKED_PENDING_USER_DECISION,
                                    "Task requires user decision",
                                    observation
                            ),
                            "user_decision_required"
                    );
                }
            }
        }

        return VerificationResult.continueLoop(null);
    }
}
