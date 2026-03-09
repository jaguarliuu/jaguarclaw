package com.jaguarliu.ai.runtime;

/**
 * 运行时进展快照。
 */
public record ProgressSnapshot(
        int repeatedFailureCount,
        String lastFailureCategory,
        String lastFailureDetail,
        int lowProgressRounds,
        int environmentRepairAttempts,
        int totalTokens
) {

    public boolean hasRepairBudgetRemaining(int maxEnvironmentRepairAttempts) {
        return RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT.equals(lastFailureCategory)
                && maxEnvironmentRepairAttempts > 0
                && environmentRepairAttempts < maxEnvironmentRepairAttempts;
    }

    public boolean shouldStopForRepeatedFailures(int maxRepeatedFailures, int maxEnvironmentRepairAttempts) {
        if (maxRepeatedFailures <= 0 || repeatedFailureCount < maxRepeatedFailures) {
            return false;
        }
        return !hasRepairBudgetRemaining(maxEnvironmentRepairAttempts);
    }

    public boolean shouldStopForLowProgress(int maxLowProgressRounds) {
        return maxLowProgressRounds > 0 && lowProgressRounds >= maxLowProgressRounds;
    }
}
