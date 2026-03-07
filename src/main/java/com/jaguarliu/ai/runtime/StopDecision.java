package com.jaguarliu.ai.runtime;

/**
 * 外层循环的停止决策。
 */
public record StopDecision(boolean stop, RunOutcome outcome, String reason) {

    public static StopDecision continueLoop() {
        return new StopDecision(false, null, "continue");
    }

    public static StopDecision cancelled() {
        return new StopDecision(true, null, "cancelled");
    }

    public static StopDecision timeout() {
        return new StopDecision(true, null, "timeout");
    }

    public static StopDecision maxSteps() {
        return new StopDecision(true, null, "max_steps");
    }

    public static StopDecision notWorthContinuing(String detail, String reason) {
        return new StopDecision(
                true,
                new RunOutcome(RunOutcomeStatus.NOT_WORTH_CONTINUING,
                        "Task is not worth continuing",
                        detail),
                reason
        );
    }

    public boolean isCancelled() {
        return "cancelled".equals(reason);
    }

    public boolean isTimeout() {
        return "timeout".equals(reason);
    }

    public boolean isMaxSteps() {
        return "max_steps".equals(reason);
    }
}
