package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.subagent.SubagentCompletionTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 子代理屏障
 * 等待所有 spawn 的子代理完成
 */
@Component
@RequiredArgsConstructor
public class SubagentBarrier {

    private final SubagentCompletionTracker tracker;

    /**
     * 等待子代理完成。
     */
    public BarrierResult waitForCompletion(
            List<String> subRunIds,
            RunContext context
    ) {
        throw new UnsupportedOperationException();
    }

    /**
     * 将结果格式化为可注入到 LLM 的消息。
     */
    private String formatResults(List<SubagentCompletionTracker.SubagentResult> results) {
        throw new UnsupportedOperationException();
    }

    public record BarrierResult(List<SubagentCompletionTracker.SubagentResult> results, String formattedMessage) {}
}

