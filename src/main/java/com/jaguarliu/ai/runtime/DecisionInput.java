package com.jaguarliu.ai.runtime;

import java.util.List;
import java.util.Set;

/**
 * 运行时决策输入快照。
 */
public record DecisionInput(
        String assistantReply,
        List<String> observations,
        Set<String> runtimeFailureCategories,
        ProgressSnapshot progressSnapshot,
        int currentStep,
        int environmentRepairAttempts,
        boolean hasToolCalls,
        boolean hasPendingSubagents
) {
    public DecisionInput {
        observations = observations == null ? List.of() : List.copyOf(observations);
        runtimeFailureCategories = runtimeFailureCategories == null ? Set.of() : Set.copyOf(runtimeFailureCategories);
    }
}
