package com.jaguarliu.ai.gateway.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.model.RpcEvent;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;


/**
 * 事件总线
 * 负责 Agent 事件的发布和订阅
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventBus {

    private final ConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    /**
     * 全局事件流
     */
    private final Sinks.Many<AgentEvent> sink = Sinks.many().multicast().onBackpressureBuffer();

    /**
     * sink 发射锁，确保对 Reactor sink 的 signal 串行化
     */
    private final Object sinkEmitLock = new Object();

    /**
     * 发布事件
     */
    public void publish(AgentEvent event) {
        if (shouldLogEvent(event)) {
            log.info("Publishing event: type={}, runId={}, connectionId={}",
                    event.getType().getValue(), event.getRunId(), event.getConnectionId());
        }

        // 直接推送到对应的 WebSocket 连接
        pushToConnection(event);

        // 同时发布到全局流（供其他订阅者使用）
        emitToGlobalSink(event);
    }

    private void emitToGlobalSink(AgentEvent event) {
        synchronized (sinkEmitLock) {
            Sinks.EmitResult result = sink.tryEmitNext(event);
            if (result == Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER
                    || result == Sinks.EmitResult.FAIL_CANCELLED) {
                return;
            }
            if (result.isFailure()) {
                log.warn("Failed to emit event to global sink: type={}, runId={}, result={}",
                        event.getType() != null ? event.getType().getValue() : null,
                        event.getRunId(),
                        result);
            }
        }
    }

    boolean shouldLogEvent(AgentEvent event) {
        if (event == null || event.getType() == null) {
            return false;
        }
        return switch (event.getType()) {
            case LIFECYCLE_START,
                 LIFECYCLE_END,
                 LIFECYCLE_ERROR,
                 STEP_COMPLETED,
                 TOOL_CALL,
                 TOOL_RESULT,
                 TOOL_CONFIRM_REQUEST,
                 SKILL_ACTIVATED,
                 SUBAGENT_SPAWNED,
                 SUBAGENT_STARTED,
                 SUBAGENT_ANNOUNCED,
                 SUBAGENT_FAILED,
                 FILE_CREATED,
                 RUN_OUTCOME -> true;
            case ASSISTANT_DELTA,
                 SESSION_RENAMED,
                 ARTIFACT_OPEN,
                 ARTIFACT_DELTA,
                 HEARTBEAT_NOTIFY,
                 TOKEN_USAGE,
                 CONTEXT_COMPACTED -> false;
        };
    }

    /**
     * 订阅指定 runId 的事件
     */
    public Flux<AgentEvent> subscribe(String runId) {
        return sink.asFlux()
                .filter(event -> runId.equals(event.getRunId()));
    }

    /**
     * 订阅所有事件
     */
    public Flux<AgentEvent> subscribeAll() {
        return sink.asFlux();
    }

    /**
     * 推送事件到 WebSocket 连接
     */
    private void pushToConnection(AgentEvent event) {
        String connectionId = event.getConnectionId();
        if (connectionId == null) {
            log.warn("Event has no connectionId, cannot push: runId={}", event.getRunId());
            return;
        }

        // 定时任务使用占位 connectionId，无需推送到 WebSocket
        if ("__scheduled__".equals(connectionId)) {
            return;
        }

        if (connectionManager.get(connectionId) == null) {
            log.warn("Connection not found: connectionId={}", connectionId);
            return;
        }

        try {
            RpcEvent rpcEvent = RpcEvent.of(
                    event.getType().getValue(),
                    event.getRunId(),
                    event.getData()
            );
            String json = objectMapper.writeValueAsString(rpcEvent);
            if (!connectionManager.emit(connectionId, json)) {
                log.warn("Failed to enqueue event: connectionId={}, runId={}, type={}",
                        connectionId, event.getRunId(), event.getType().getValue());
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event", e);
        }
    }
}
