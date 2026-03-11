package com.jaguarliu.ai.subagent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * SubAgent 完成跟踪器
 *
 * 用于主循环等待子代理完成：
 * 1. spawn 时 register → 创建 CompletableFuture
 * 2. announce/failure 时 complete → 完成 Future
 * 3. AgentRuntime 在循环退出前 wait → 阻塞等待所有 Future
 */
@Slf4j
@Component
public class SubagentCompletionTracker {

    private final ConcurrentHashMap<String, CompletableFuture<SubagentResult>> pending = new ConcurrentHashMap<>();

    /**
     * 注册一个待完成的子代理
     *
     * @param subRunId 子运行 ID
     * @return CompletableFuture，在子代理完成时 resolve
     */
    public CompletableFuture<SubagentResult> register(String subRunId) {
        CompletableFuture<SubagentResult> future = new CompletableFuture<>();
        pending.put(subRunId, future);
        log.debug("Registered pending subagent: subRunId={}", subRunId);
        return future;
    }

    /**
     * 标记子代理完成
     *
     * NOTE: 故意不从 pending 移除 future，避免竞态条件：
     * 若子代理在主循环调用 getFuture() 前就已完成，remove() 会导致 getFuture()
     * 返回 null，屏障将误判为"tracking lost"。保留 future 使 getFuture() 始终
     * 能找到它，already-completed 的 future.get() 会立即返回。
     * cleanup() 由屏障在获取结果后显式调用，负责最终的 map 清理。
     *
     * @param subRunId 子运行 ID
     * @param result   完成结果
     */
    public void complete(String subRunId, SubagentResult result) {
        CompletableFuture<SubagentResult> future = pending.get(subRunId);
        if (future != null) {
            future.complete(result);
            log.debug("Completed pending subagent: subRunId={}, status={}", subRunId, result.status());
        } else {
            log.warn("No pending future for subRunId={} (not registered or already cleaned up)", subRunId);
        }
    }

    /**
     * 清理子代理的 CompletableFuture（由屏障在取得结果后调用）
     *
     * @param subRunId 子运行 ID
     */
    public void cleanup(String subRunId) {
        pending.remove(subRunId);
        log.debug("Cleaned up future for subRunId={}", subRunId);
    }

    /**
     * 获取子代理的 CompletableFuture
     *
     * @param subRunId 子运行 ID
     * @return future（可能为 null）
     */
    public CompletableFuture<SubagentResult> getFuture(String subRunId) {
        return pending.get(subRunId);
    }

    /**
     * 子代理完成结果
     */
    public record SubagentResult(
            String subRunId,
            String task,
            String status,       // "completed" or "failed"
            String result,       // 成功时的结果文本
            String error,        // 失败时的错误信息
            long durationMs
    ) {
        public boolean isSuccess() {
            return "completed".equals(status);
        }
    }
}
