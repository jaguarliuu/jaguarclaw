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
        return llmDecision != null ? llmDecision : guardDecision;
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
}
