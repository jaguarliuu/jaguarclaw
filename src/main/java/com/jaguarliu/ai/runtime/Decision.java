package com.jaguarliu.ai.runtime;

/**
 * 统一运行时决策。
 */
public record Decision(
        boolean terminal,
        boolean continueLoop,
        RunOutcome outcome,
        String failureCategory,
        String feedback,
        String reason
) {

    public static Decision continueSilently() {
        return new Decision(false, true, null, null, null, null);
    }

    public static Decision continueWithFeedback(String feedback, String failureCategory, String reason) {
        return new Decision(false, true, null, failureCategory, feedback, reason);
    }

    public static Decision terminal(RunOutcome outcome, String failureCategory, String reason) {
        return new Decision(true, false, outcome, failureCategory, null, reason);
    }

}
