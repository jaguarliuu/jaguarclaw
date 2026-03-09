package com.jaguarliu.ai.runtime;

/**
 * 统一运行时决策。
 */
public record Decision(
        DecisionAction action,
        RunOutcome outcome,
        String failureCategory,
        String feedback,
        String reason,
        String targetItemId
) {

    public static Decision continueSilently() {
        return new Decision(DecisionAction.CONTINUE_ITEM, null, null, null, null, null);
    }

    public static Decision continueWithFeedback(String feedback, String failureCategory, String reason) {
        return new Decision(DecisionAction.CONTINUE_ITEM, null, failureCategory, feedback, reason, null);
    }

    public static Decision itemDone(String reason, String targetItemId) {
        return new Decision(DecisionAction.ITEM_DONE, null, null, null, reason, targetItemId);
    }

    public static Decision askUser(String feedback, String failureCategory, String reason, String targetItemId) {
        return new Decision(DecisionAction.ASK_USER, null, failureCategory, feedback, reason, targetItemId);
    }

    public static Decision blockItem(String feedback, String failureCategory, String reason, String targetItemId) {
        return new Decision(DecisionAction.BLOCK_ITEM, null, failureCategory, feedback, reason, targetItemId);
    }

    public static Decision delegateItem(String feedback, String reason, String targetItemId) {
        return new Decision(DecisionAction.DELEGATE_ITEM, null, null, feedback, reason, targetItemId);
    }

    public static Decision taskDone(RunOutcome outcome, String reason, String targetItemId) {
        return new Decision(DecisionAction.TASK_DONE, outcome, null, null, reason, targetItemId);
    }

    public static Decision terminal(RunOutcome outcome, String failureCategory, String reason) {
        DecisionAction action = outcome != null && outcome.status() == RunOutcomeStatus.COMPLETED
                ? DecisionAction.TASK_DONE
                : DecisionAction.STOP;
        return new Decision(action, outcome, failureCategory, null, reason, null);
    }

    public boolean terminal() {
        return action == DecisionAction.TASK_DONE || action == DecisionAction.STOP;
    }

    public boolean continueLoop() {
        return action == DecisionAction.CONTINUE_ITEM
                || action == DecisionAction.ITEM_DONE
                || action == DecisionAction.DELEGATE_ITEM;
    }
}
