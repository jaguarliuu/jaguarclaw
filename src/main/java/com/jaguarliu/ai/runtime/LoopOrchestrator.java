package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.gateway.events.EventBus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * ReAct 循环编排器
 * 负责循环控制：步数、超时、取消检测、事件发布
 */
@Component
@RequiredArgsConstructor
public class LoopOrchestrator {

    private final EventBus eventBus;

    /**
     * 执行循环状态检查。
     * @param context 运行上下文
     * @return 循环状态
     */
    public LoopState checkLoopState(RunContext context) {
        throw new UnsupportedOperationException();
    }

    /**
     * 发布步骤完成事件。
     * @param context 运行上下文
     */
    public void publishStepEvent(RunContext context) {
        throw new UnsupportedOperationException();
    }

    /**
     * 循环状态记录类型。
     */
    public record LoopState(boolean shouldContinue, String reason) {
        public static LoopState continue_() { return new LoopState(true, null); }
        public static LoopState cancelled() { return new LoopState(false, null); }
        public static LoopState timeout() { return new LoopState(false, null); }
        public static LoopState maxSteps() { return new LoopState(false, null); }
    }
}
