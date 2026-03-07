package com.jaguarliu.ai.runtime;

/**
 * Run 终态语义。
 */
public enum RunOutcomeStatus {
    COMPLETED,
    COMPLETED_WITH_DEGRADATION,
    BLOCKED_BY_ENVIRONMENT,
    BLOCKED_PENDING_USER_DECISION,
    NOT_WORTH_CONTINUING,
    FAILED_UNEXPECTEDLY
}
