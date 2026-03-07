package com.jaguarliu.ai.runtime;

/**
 * 验证结果。
 */
public record VerificationResult(
        boolean terminal,
        boolean continueLoop,
        RunOutcome outcome,
        String feedback,
        String failureCategory
) {

    public static VerificationResult completed(String message) {
        return new VerificationResult(true, false, RunOutcome.completed(message), null, null);
    }

    public static VerificationResult terminal(RunOutcome outcome, String failureCategory) {
        return new VerificationResult(true, false, outcome, null, failureCategory);
    }

    public static VerificationResult continueLoop(String feedback) {
        return new VerificationResult(false, true, null, feedback, null);
    }
}
