package com.jaguarliu.ai.runtime;

/**
 * 任务执行约束摘要。
 */
public record TaskContract(
        String goal,
        TaskComplexity complexity,
        boolean allowDegradedCompletion,
        boolean requiresUserDecision
) {
}
