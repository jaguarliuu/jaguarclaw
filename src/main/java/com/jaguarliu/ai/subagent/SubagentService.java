package com.jaguarliu.ai.subagent;

import com.jaguarliu.ai.agents.AgentRegistry;
import com.jaguarliu.ai.agents.model.AgentProfile;
import com.jaguarliu.ai.gateway.events.AgentEvent;
import com.jaguarliu.ai.gateway.events.EventBus;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.runtime.AgentRuntime;
import com.jaguarliu.ai.runtime.ContextBuilder;
import com.jaguarliu.ai.runtime.LaneAwareQueueManager;
import com.jaguarliu.ai.runtime.RunContext;
import com.jaguarliu.ai.session.MessageService;
import com.jaguarliu.ai.session.RunService;
import com.jaguarliu.ai.session.RunStatus;
import com.jaguarliu.ai.session.SessionService;
import com.jaguarliu.ai.storage.entity.RunEntity;
import com.jaguarliu.ai.storage.entity.SessionEntity;
import com.jaguarliu.ai.subagent.model.SubagentSpawnRequest;
import com.jaguarliu.ai.subagent.model.SubagentSpawnResult;
import com.jaguarliu.ai.runtime.LoopConfig;
import com.jaguarliu.ai.runtime.CancellationManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Worker 执行服务（兼容 SubAgent 命名）
 * 负责派发 worker 任务并管理其生命周期。
 *
 * 说明：
 * - 领域语义已解耦为「worker execution」；
 * - 为保持兼容，事件名/数据结构仍沿用 subagent.*。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubagentService {

    private final SessionService sessionService;
    private final RunService runService;
    private final MessageService messageService;
    private final AgentRegistry agentRegistry;
    private final LaneAwareQueueManager queueManager;
    private final AgentRuntime agentRuntime;
    private final ContextBuilder contextBuilder;
    private final EventBus eventBus;
    private final LoopConfig loopConfig;
    private final CancellationManager cancellationManager;
    private final SubagentAnnounceService announceService;
    private final SubagentCompletionTracker completionTracker;

    /**
     * 派发 worker 任务（兼容旧 subagent 调用）
     *
     * @param parentRunId        父运行 ID
     * @param parentSessionId    父会话 ID
     * @param parentAgentId      父 Agent ID
     * @param connectionId       连接 ID（用于事件推送）
     * @param request            spawn 请求参数
     * @return spawn 结果
     */
    public SubagentSpawnResult spawn(String parentRunId,
                                      String parentSessionId,
                                      String parentAgentId,
                                      String connectionId,
                                      SubagentSpawnRequest request) {
        // 1. 参数校验
        if (request.getTask() == null || request.getTask().isBlank()) {
            return SubagentSpawnResult.failure("Task is required");
        }

        // 2. 解析目标 agentId（默认继承父）
        String targetAgentId = request.getAgentId();
        if (targetAgentId == null || targetAgentId.isBlank()) {
            targetAgentId = parentAgentId;
        }

        // 3. 验证 agentId 有效性
        if (!agentRegistry.isValidAgentId(targetAgentId)) {
            return SubagentSpawnResult.failure("Invalid agentId: " + targetAgentId);
        }

        // 4. 检查目标 agent 是否允许被 spawn（权限检查）
        AgentProfile targetProfile = agentRegistry.getOrDefault(targetAgentId);
        // 注意：这里检查的是目标 profile 是否可以作为 subagent 运行
        // canSpawn 是指该 profile 是否可以发起 spawn，不是是否可以被 spawn
        // 暂时不做额外限制

        try {
            // 5. 创建子会话
            SessionEntity subSession = sessionService.createSubagentSession(
                    parentSessionId,
                    parentRunId,
                    targetAgentId,
                    request.getTask()
            );

            // 6. 创建子运行
            RunEntity subRun = runService.createSubagentRun(
                    subSession.getId(),
                    parentRunId,
                    parentSessionId,
                    targetAgentId,
                    request.getTask(),
                    request.isDeliver()
            );

            // 7. 发布 subagent.spawned 事件（兼容保留）
            eventBus.publish(AgentEvent.subagentSpawned(
                    connectionId,
                    parentRunId,
                    subRun.getId(),
                    subSession.getId(),
                    subSession.getSessionKey(),
                    targetAgentId,
                    request.getTask(),
                    LaneAwareQueueManager.LANE_SUBAGENT
            ));

            // 8. 注册完成跟踪（在提交执行之前，确保 future 存在于 tracker 中）
            completionTracker.register(subRun.getId());

            // 9. 提交到 subagent lane 异步执行（兼容保留）
            long sequence = queueManager.nextSequence(subSession.getId());
            queueManager.submit(
                    subSession.getId(),
                    subRun.getId(),
                    sequence,
                    LaneAwareQueueManager.LANE_SUBAGENT,
                    () -> {
                        executeSubagentRun(connectionId, subRun, subSession, request);
                        return null;
                    }
            ).subscribe();

            log.info("Spawned worker task: parentRunId={}, subRunId={}, subSessionId={}, agentId={}, task={}",
                    parentRunId, subRun.getId(), subSession.getId(), targetAgentId,
                    truncateTask(request.getTask()));

            return SubagentSpawnResult.success(
                    subSession.getId(),
                    subRun.getId(),
                    subSession.getSessionKey()
            );

        } catch (Exception e) {
            log.error("Failed to spawn subagent: parentRunId={}", parentRunId, e);
            return SubagentSpawnResult.failure("Spawn failed: " + e.getMessage());
        }
    }

    /**
     * 执行子代理运行
     */
    private void executeSubagentRun(String connectionId,
                                     RunEntity subRun,
                                     SessionEntity subSession,
                                     SubagentSpawnRequest request) {
        String runId = subRun.getId();
        String sessionId = subSession.getId();
        String task = request.getTask();
        LocalDateTime startTime = LocalDateTime.now();

        try {
            // 1. lifecycle.start
            runService.updateStatus(runId, RunStatus.RUNNING);
            eventBus.publish(AgentEvent.subagentStarted(connectionId, subRun.getParentRunId(), runId));

            // 2. 保存用户消息（任务）
            messageService.saveUserMessage(sessionId, runId, task);

            // 3. 构建上下文（子代理从空历史开始）
            List<LlmRequest.Message> messages = contextBuilder.buildMessages(
                    List.of(), task, null, subRun.getAgentId()
            );

            // 4. 创建 worker RunContext（兼容 subagent 运行类型）
            RunContext context = RunContext.createWorker(
                    runId,
                    connectionId,
                    sessionId,
                    subRun.getAgentId(),
                    subRun.getParentRunId(),
                    subRun.getRequesterSessionId(),
                    request.isDeliver(),
                    loopConfig,
                    cancellationManager
            );

            // 5. 执行 ReAct 循环
            String response = agentRuntime.executeLoopWithContext(context, messages, task);

            // 6. 保存助手消息
            messageService.saveAssistantMessage(sessionId, runId, response);

            // 7. lifecycle.end
            runService.updateStatus(runId, RunStatus.DONE);

            // 8. 完成跟踪 future（先通知主循环，让主 Agent 可以立即开始汇总）
            long durationMs = Duration.between(startTime, LocalDateTime.now()).toMillis();
            completionTracker.complete(runId, new SubagentCompletionTracker.SubagentResult(
                    runId, task, "completed", response, null, durationMs
            ));

            // 9. Announce 回传（后发 WebSocket 事件，使其与主 Agent 汇总结果的时序对齐）
            if (request.isAnnounce()) {
                announceService.announce(connectionId, subRun, subSession, response, null, startTime);
            }

            log.info("Subagent run completed: runId={}, response length={}", runId, response.length());

        } catch (java.util.concurrent.CancellationException e) {
            log.info("Subagent run cancelled: runId={}", runId);
            handleSubagentFailure(connectionId, subRun, subSession, request, "Cancelled by user", startTime);
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("Subagent run timed out: runId={}", runId);
            handleSubagentFailure(connectionId, subRun, subSession, request, "Timeout: " + e.getMessage(), startTime);
        } catch (Exception e) {
            log.error("Subagent run failed: runId={}", runId, e);
            handleSubagentFailure(connectionId, subRun, subSession, request, "Error: " + e.getMessage(), startTime);
        }
    }

    /**
     * 处理子代理失败
     */
    private void handleSubagentFailure(String connectionId,
                                        RunEntity subRun,
                                        SessionEntity subSession,
                                        SubagentSpawnRequest request,
                                        String errorMessage,
                                        LocalDateTime startTime) {
        try {
            runService.updateStatus(subRun.getId(), RunStatus.ERROR);
        } catch (Exception ignored) {}

        eventBus.publish(AgentEvent.subagentFailed(
                connectionId,
                subRun.getParentRunId(),
                subRun.getId(),
                subRun.getAgentId(),
                subRun.getPrompt(),
                errorMessage
        ));

        // 完成跟踪 future（先通知主循环）
        long durationMs = startTime != null
                ? Duration.between(startTime, LocalDateTime.now()).toMillis()
                : 0;
        completionTracker.complete(subRun.getId(), new SubagentCompletionTracker.SubagentResult(
                subRun.getId(), request.getTask(), "failed", null, errorMessage, durationMs
        ));

        // 即使失败也回传（包含错误信息）
        if (request.isAnnounce()) {
            announceService.announce(connectionId, subRun, subSession, null, errorMessage, startTime);
        }
    }

    /**
     * 截断任务描述（用于日志）
     */
    private String truncateTask(String task) {
        if (task == null) return "";
        return task.length() > 50 ? task.substring(0, 47) + "..." : task;
    }
}
