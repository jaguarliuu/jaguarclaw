package com.jaguarliu.ai.runtime;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 组合验证器：先走高置信度规则，再按需走 LLM。
 */
@Component
@Primary
@RequiredArgsConstructor
public class CompositeTaskVerifier implements TaskVerifier {

    private final DefaultTaskVerifier defaultTaskVerifier;
    private final LlmTaskVerifier llmTaskVerifier;

    @Override
    public VerificationResult verify(RunContext context, String assistantReply, List<String> observations) {
        VerificationResult ruleResult = defaultTaskVerifier.verify(context, assistantReply, observations);
        if (ruleResult.terminal()) {
            return ruleResult;
        }
        if (!shouldConsultLlm(context, assistantReply, observations)) {
            return ruleResult;
        }

        VerificationResult llmResult = llmTaskVerifier.verify(context, assistantReply, observations);
        return llmResult != null ? llmResult : ruleResult;
    }

    boolean shouldConsultLlm(RunContext context, String assistantReply, List<String> observations) {
        if (assistantReply != null && !assistantReply.isBlank()) {
            return true;
        }
        if (observations == null || observations.stream().noneMatch(item -> item != null && !item.isBlank())) {
            return false;
        }
        ProgressSnapshot progress = context.snapshotProgress();
        return progress.lastFailureCategory() != null
                || progress.repeatedFailureCount() > 0
                || progress.lowProgressRounds() > 0;
    }
}
