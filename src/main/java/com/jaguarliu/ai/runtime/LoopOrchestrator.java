package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.gateway.events.AgentEvent;
import com.jaguarliu.ai.gateway.events.EventBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ReAct 循环编排器
 * 负责循环控制：步数、超时、取消检测、事件发布
 *
 * 从 AgentRuntime 迁移的逻辑：
 * - 循环条件检测（步数/超时/取消）
 * - 步骤完成事件发布
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoopOrchestrator {

    private final EventBus eventBus;

    public StopDecision checkStopDecision(RunContext context) {
        if (context.isAborted()) {
            log.info("Loop aborted by cancellation: runId={}, step={}",
                    context.getRunId(), context.getCurrentStep());
            return StopDecision.cancelled();
        }

        if (context.isTimedOut()) {
            log.warn("Loop timed out: runId={}, elapsed={}s, limit={}s",
                    context.getRunId(), context.getElapsedSeconds(),
                    context.getConfig().getRunTimeoutSeconds());
            return StopDecision.timeout();
        }

        if (context.isMaxStepsReached()) {
            log.warn("Loop reached max steps: runId={}, maxSteps={}",
                    context.getRunId(), context.getConfig().getMaxSteps());
            return StopDecision.maxSteps();
        }

        if (context.isRepeatedFailureLimitReached()) {
            ProgressSnapshot snapshot = context.snapshotProgress();
            log.warn("Loop stopped by repeated failures: runId={}, category={}, count={}",
                    context.getRunId(), snapshot.lastFailureCategory(), snapshot.repeatedFailureCount());
            return StopDecision.notWorthContinuing(
                    "Repeated failure category: " + snapshot.lastFailureCategory(),
                    "repeated_failures"
            );
        }

        if (context.isLowProgressLimitReached()) {
            ProgressSnapshot snapshot = context.snapshotProgress();
            log.warn("Loop stopped by low progress: runId={}, rounds={}",
                    context.getRunId(), snapshot.lowProgressRounds());
            return StopDecision.notWorthContinuing(
                    "Low progress rounds: " + snapshot.lowProgressRounds(),
                    "low_progress"
            );
        }

        if (context.isTokenBudgetReached()) {
            log.warn("Loop stopped by token budget: runId={}, totalTokens={}, budget={}",
                    context.getRunId(), context.getTotalTokens(), context.getConfig().getMaxTokens());
            return StopDecision.notWorthContinuing(
                    "Token budget exceeded: " + context.getTotalTokens(),
                    "token_budget"
            );
        }

        return StopDecision.continueLoop();
    }

    /**
     * 执行循环状态检查
     */
    public LoopState checkLoopState(RunContext context) {
        StopDecision decision = checkStopDecision(context);
        if (!decision.stop()) {
            return LoopState.continue_();
        }
        if (decision.isCancelled()) {
            return LoopState.cancelled();
        }
        if (decision.isTimeout()) {
            return LoopState.timeout();
        }
        if (decision.isMaxSteps()) {
            return LoopState.maxSteps();
        }
        return LoopState.stopped(decision.reason());
    }

    /**
     * 发布步骤完成事件
     */
    public void publishStepEvent(RunContext context) {
        eventBus.publish(AgentEvent.stepCompleted(
                context.getConnectionId(),
                context.getRunId(),
                context.getCurrentStep(),
                context.getConfig().getMaxSteps(),
                context.getElapsedSeconds()));

        log.debug("Step completed: runId={}, step={}/{}",
                context.getRunId(), context.getCurrentStep(),
                context.getConfig().getMaxSteps());
    }

    public void incrementStepAndPublish(RunContext context) {
        context.incrementStep();
        publishStepEvent(context);
    }

    public void publishStopDecision(RunContext context, StopDecision decision) {
        if (decision == null || decision.outcome() == null) {
            return;
        }
        eventBus.publish(AgentEvent.runOutcome(
                context.getConnectionId(),
                context.getRunId(),
                decision.outcome().status().name(),
                decision.reason(),
                context.getCurrentStep(),
                context.getTotalTokens()
        ));
    }

    public LoopState checkAndIncrement(RunContext context) {
        LoopState state = checkLoopState(context);
        if (state.shouldContinue()) {
            incrementStepAndPublish(context);
        }
        return state;
    }

    public record LoopState(boolean shouldContinue, String reason) {

        private static final String CONTINUE = "continue";
        private static final String CANCELLED = "cancelled";
        private static final String TIMEOUT = "timeout";
        private static final String MAX_STEPS = "max_steps";

        public static LoopState continue_() {
            return new LoopState(true, CONTINUE);
        }

        public static LoopState cancelled() {
            return new LoopState(false, CANCELLED);
        }

        public static LoopState timeout() {
            return new LoopState(false, TIMEOUT);
        }

        public static LoopState maxSteps() {
            return new LoopState(false, MAX_STEPS);
        }

        public static LoopState stopped(String reason) {
            return new LoopState(false, reason);
        }

        public boolean isCancelled() {
            return CANCELLED.equals(reason);
        }

        public boolean isTimeout() {
            return TIMEOUT.equals(reason);
        }

        public boolean isMaxSteps() {
            return MAX_STEPS.equals(reason);
        }

        public String getDescription() {
            if (shouldContinue) {
                return "继续执行";
            }
            return switch (reason) {
                case CANCELLED -> "用户取消";
                case TIMEOUT -> "执行超时";
                case MAX_STEPS -> "达到最大步数";
                default -> "停止执行: " + reason;
            };
        }
    }
}
