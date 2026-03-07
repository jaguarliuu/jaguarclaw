package com.jaguarliu.ai.runtime;

/**
 * 运行时进展快照。
 */
public record ProgressSnapshot(
        int repeatedFailureCount,
        String lastFailureCategory,
        int lowProgressRounds,
        int environmentRepairAttempts,
        int totalTokens
) {
}
