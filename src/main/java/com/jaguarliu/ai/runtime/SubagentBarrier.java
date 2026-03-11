
package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.subagent.SubagentCompletionTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * SubAgent 屏障：等待所有已 spawn 的子代理完成，并返回格式化摘要。
 *
 * 逻辑迁移自 AgentRuntime.waitForPendingSubagents(...)，保持原有
 * 超时处理与结果格式化行为，以便在主循环退出前收敛所有子代理结果。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubagentBarrier {

    private final SubagentCompletionTracker subagentCompletionTracker;
    private final CancellationManager cancellationManager;

    /**
     * 等待所有已 spawn 的子代理完成，并构建结果摘要。
     *
     * @param subRunIds 子运行 ID 列表
     * @param context   当前运行上下文（用于剩余时间计算与取消检查）
     * @return 供 LLM 消化的聚合结果摘要文本
     */
    public String waitForCompletion(List<String> subRunIds, RunContext context) {
        long remainingSeconds = context.getConfig().getRunTimeoutSeconds() - context.getElapsedSeconds();
        // 每个子代理最多等待 5 分钟，或剩余时间（取较小值），同时至少给 30 秒下限
        long perSubagentTimeoutSeconds = Math.max(30, Math.min(remainingSeconds, 300));

        List<SubagentCompletionTracker.SubagentResult> results = new ArrayList<>();
        long totalWaitStart = System.currentTimeMillis();

        for (int i = 0; i < subRunIds.size(); i++) {
            String subRunId = subRunIds.get(i);

            // 父运行取消：向所有剩余子代理传播取消信号，并立即退出等待
            if (context.isAborted()) {
                log.info("Subagent barrier aborted by cancellation: runId={}", context.getRunId());
                for (int j = i; j < subRunIds.size(); j++) {
                    String pendingSubRunId = subRunIds.get(j);
                    cancellationManager.requestCancel(pendingSubRunId);
                    subagentCompletionTracker.cleanup(pendingSubRunId);
                    log.info("Propagated cancellation to subagent: subRunId={}", pendingSubRunId);
                    results.add(new SubagentCompletionTracker.SubagentResult(
                            pendingSubRunId, "unknown", "cancelled", null, "Parent run cancelled", 0));
                }
                break;
            }

            // 总等待超时：最多等待 min(remainingSeconds, 600s) 的总时间
            long elapsedTotal = (System.currentTimeMillis() - totalWaitStart) / 1000;
            if (elapsedTotal > Math.min(remainingSeconds, 600)) {
                log.warn("Subagent barrier total timeout reached: {}s, runId={}", elapsedTotal, context.getRunId());
                for (int j = i; j < subRunIds.size(); j++) {
                    results.add(new SubagentCompletionTracker.SubagentResult(
                            subRunIds.get(j), "unknown", "timeout", null, "Barrier total timeout", 0));
                }
                break;
            }

            CompletableFuture<SubagentCompletionTracker.SubagentResult> future =
                    subagentCompletionTracker.getFuture(subRunId);

            if (future == null) {
                log.warn("No completion future for subRunId={}, skipping", subRunId);
                results.add(new SubagentCompletionTracker.SubagentResult(
                        subRunId, "unknown", "unknown", null, "Completion tracking lost", 0));
                continue;
            }

            try {
                SubagentCompletionTracker.SubagentResult result =
                        future.get(perSubagentTimeoutSeconds, TimeUnit.SECONDS);
                subagentCompletionTracker.cleanup(subRunId);
                results.add(result);
                log.info("Subagent completed: subRunId={}, status={}", subRunId, result.status());
            } catch (TimeoutException e) {
                subagentCompletionTracker.cleanup(subRunId);
                log.warn("Timed out waiting for subagent: subRunId={}", subRunId);
                results.add(new SubagentCompletionTracker.SubagentResult(
                        subRunId, "unknown", "timeout", null, "Timed out waiting for result", 0));
            } catch (Exception e) {
                subagentCompletionTracker.cleanup(subRunId);
                log.error("Error waiting for subagent: subRunId={}", subRunId, e);
                results.add(new SubagentCompletionTracker.SubagentResult(
                        subRunId, "unknown", "error", null, "Error: " + e.getMessage(), 0));
            }
        }

        return formatSubagentResults(results);
    }

    /**
     * 将子代理结果格式化为 LLM 可读的消息（与 AgentRuntime 中逻辑保持一致）。
     */
    private String formatSubagentResults(List<SubagentCompletionTracker.SubagentResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("[All spawned SubAgents have completed. Here are their results:]\n\n");

        for (int i = 0; i < results.size(); i++) {
            SubagentCompletionTracker.SubagentResult r = results.get(i);
            sb.append("--- SubAgent ").append(i + 1).append(" ---\n");
            sb.append("Task: ").append(r.task() != null ? r.task() : "unknown").append("\n");
            sb.append("Status: ").append(r.status()).append("\n");

            if (r.isSuccess() && r.result() != null) {
                String resultText = r.result().length() > 2000
                        ? r.result().substring(0, 1997) + "..."
                        : r.result();
                sb.append("Result:\n").append(resultText).append("\n");
            }

            if (!r.isSuccess() && r.error() != null) {
                sb.append("Error: ").append(r.error()).append("\n");
            }

            sb.append("\n");
        }

        sb.append("Please summarize the above subagent results for the user. ");
        sb.append("If any subagent failed, explain what happened. ");
        sb.append("Provide a clear, consolidated response.");

        return sb.toString();
    }
}
