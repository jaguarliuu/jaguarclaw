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

    /**
     * 执行循环状态检查
     *
     * @param context 运行上下文
     * @return 循环状态（是否应该继续 + 原因）
     */
    public LoopState checkLoopState(RunContext context) {
        // 检查取消
        if (context.isAborted()) {
            log.info("Loop aborted by cancellation: runId={}, step={}",
                    context.getRunId(), context.getCurrentStep());
            return LoopState.cancelled();
        }

        // 检查超时
        if (context.isTimedOut()) {
            log.warn("Loop timed out: runId={}, elapsed={}s, limit={}s",
                    context.getRunId(), context.getElapsedSeconds(),
                    context.getConfig().getRunTimeoutSeconds());
            return LoopState.timeout();
        }

        // 检查步数
        if (context.isMaxStepsReached()) {
            log.warn("Loop reached max steps: runId={}, maxSteps={}",
                    context.getRunId(), context.getConfig().getMaxSteps());
            return LoopState.maxSteps();
        }

        return LoopState.continue_();
    }

    /**
     * 发布步骤完成事件
     *
     * @param context 运行上下文
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

    /**
     * 增加步数并发布事件
     *
     * @param context 运行上下文
     */
    public void incrementStepAndPublish(RunContext context) {
        context.incrementStep();
        publishStepEvent(context);
    }

    /**
     * 检查是否应该继续循环（带步数增加）
     *
     * @param context 运行上下文
     * @return 循环状态
     */
    public LoopState checkAndIncrement(RunContext context) {
        LoopState state = checkLoopState(context);
        if (state.shouldContinue()) {
            incrementStepAndPublish(context);
        }
        return state;
    }

    /**
     * 循环状态
     */
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

        public boolean isCancelled() {
            return CANCELLED.equals(reason);
        }

        public boolean isTimeout() {
            return TIMEOUT.equals(reason);
        }

        public boolean isMaxSteps() {
            return MAX_STEPS.equals(reason);
        }

        /**
         * 获取用户友好的状态描述
         */
        public String getDescription() {
            if (shouldContinue) {
                return "继续执行";
            }
            return switch (reason) {
                case CANCELLED -> "用户取消";
                case TIMEOUT -> "执行超时";
                case MAX_STEPS -> "达到最大步数";
                default -> "未知原因: " + reason;
            };
        }
    }
}
