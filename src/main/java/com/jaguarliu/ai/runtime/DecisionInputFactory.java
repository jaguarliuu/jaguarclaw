package com.jaguarliu.ai.runtime;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 构建运行时决策输入。
 */
@Component
public class DecisionInputFactory {

    public DecisionInput fromAssistantStep(RunContext context, String assistantReply) {
        ProgressSnapshot snapshot = context.snapshotProgress();
        return new DecisionInput(
                assistantReply,
                List.of(),
                context.getRuntimeFailureCategories(),
                snapshot,
                context.getCurrentStep(),
                snapshot.environmentRepairAttempts(),
                false,
                false
        );
    }

    public DecisionInput fromToolRound(
            RunContext context,
            List<ToolExecutor.ToolExecutionResult> toolResults,
            Collection<String> pendingSubRunIds
    ) {
        List<ToolExecutor.ToolExecutionResult> safeResults = toolResults == null ? List.of() : toolResults;
        ProgressSnapshot snapshot = context.snapshotProgress();
        Set<String> categories = safeResults.stream()
                .map(ToolExecutor.ToolExecutionResult::failureCategory)
                .filter(category -> category != null && !category.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<String> observations = safeResults.stream()
                .map(execResult -> execResult.result() != null ? execResult.result().getContent() : null)
                .filter(content -> content != null && !content.isBlank())
                .toList();
        return new DecisionInput(
                null,
                observations,
                categories,
                snapshot,
                context.getCurrentStep(),
                snapshot.environmentRepairAttempts(),
                !safeResults.isEmpty(),
                pendingSubRunIds != null && !pendingSubRunIds.isEmpty()
        );
    }

    public DecisionInput fromVerifierInput(RunContext context, String assistantReply, List<String> observations) {
        ProgressSnapshot snapshot = context.snapshotProgress();
        List<String> safeObservations = observations == null ? List.of() : observations.stream()
                .filter(item -> item != null && !item.isBlank())
                .toList();
        boolean hasAssistantReply = assistantReply != null && !assistantReply.isBlank();
        return new DecisionInput(
                assistantReply,
                safeObservations,
                context.getRuntimeFailureCategories(),
                snapshot,
                context.getCurrentStep(),
                snapshot.environmentRepairAttempts(),
                !safeObservations.isEmpty(),
                false
        );
    }
}
