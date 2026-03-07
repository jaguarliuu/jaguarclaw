package com.jaguarliu.ai.runtime;

/**
 * Run 的结构化结果。
 */
public record RunOutcome(RunOutcomeStatus status, String message, String detail) {

    public static RunOutcome completed(String message) {
        return new RunOutcome(RunOutcomeStatus.COMPLETED, message, null);
    }

    public static RunOutcome blockedByEnvironment(String detail) {
        return new RunOutcome(
                RunOutcomeStatus.BLOCKED_BY_ENVIRONMENT,
                "Task blocked by environment",
                detail
        );
    }
}
