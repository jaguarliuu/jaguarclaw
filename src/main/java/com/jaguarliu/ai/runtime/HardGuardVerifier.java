package com.jaguarliu.ai.runtime;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认运行时验证器，只处理硬阻断信号。
 */
@Component
public class HardGuardVerifier implements RuntimeDecisionStage {

    @Override
    public Decision verify(RunContext context, String assistantReply, List<String> observations) {
        List<String> signals = new ArrayList<>();
        if (observations != null) {
            signals.addAll(observations);
        }
        if (assistantReply != null && !assistantReply.isBlank()) {
            signals.add(assistantReply);
        }

        String detail = signals.stream()
                .filter(signal -> signal != null && !signal.isBlank())
                .findFirst()
                .orElse(null);

        if (context != null && context.hasRuntimeFailureCategory(RuntimeFailureCategories.HARD_ENVIRONMENT_BLOCK)) {
            return Decision.terminal(
                    RunOutcome.blockedByEnvironment(detail),
                    RuntimeFailureCategories.HARD_ENVIRONMENT_BLOCK,
                    detail
            );
        }
        if (context != null && context.hasRuntimeFailureCategory(RuntimeFailureCategories.USER_DECISION_REQUIRED)) {
            return Decision.terminal(
                    new RunOutcome(
                            RunOutcomeStatus.BLOCKED_PENDING_USER_DECISION,
                            "Task requires user decision",
                            detail
                    ),
                    RuntimeFailureCategories.USER_DECISION_REQUIRED,
                    detail
            );
        }

        return Decision.continueSilently();
    }
}
