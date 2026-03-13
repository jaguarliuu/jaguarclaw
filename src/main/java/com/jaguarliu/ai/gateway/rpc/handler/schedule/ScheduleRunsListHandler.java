package com.jaguarliu.ai.gateway.rpc.handler.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.schedule.ScheduleRunLogEntity;
import com.jaguarliu.ai.schedule.ScheduleRunLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * schedule.runs.list — query execution history for a scheduled task
 * params: { taskId?: string, limit?: number }
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleRunsListHandler implements RpcHandler {

    private final ScheduleRunLogService runLogService;
    private final ObjectMapper objectMapper;

    @Override
    public String getMethod() {
        return "schedule.runs.list";
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> params = objectMapper.convertValue(request.getPayload(), Map.class);
            String taskId = params != null ? (String) params.get("taskId") : null;
            int limit = params != null && params.get("limit") instanceof Number n
                    ? Math.min(n.intValue(), 100) : 20;

            List<ScheduleRunLogEntity> logs = taskId != null && !taskId.isBlank()
                    ? runLogService.listByTask(taskId, limit)
                    : runLogService.listRecent(limit);

            var dtos = logs.stream().map(ScheduleRunsListHandler::toDto).toList();
            return RpcResponse.success(request.getId(), dtos);
        }).onErrorResume(e -> {
            log.error("Failed to list schedule runs: {}", e.getMessage());
            return Mono.just(RpcResponse.error(request.getId(), "LIST_FAILED", e.getMessage()));
        });
    }

    private static Map<String, Object> toDto(ScheduleRunLogEntity e) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", e.getId());
        dto.put("taskId", e.getTaskId());
        dto.put("taskName", e.getTaskName());
        dto.put("triggeredBy", e.getTriggeredBy());
        dto.put("status", e.getStatus());
        dto.put("startedAt", e.getStartedAt() != null ? e.getStartedAt().toString() : null);
        dto.put("finishedAt", e.getFinishedAt() != null ? e.getFinishedAt().toString() : null);
        dto.put("durationMs", e.getDurationMs());
        dto.put("errorMessage", e.getErrorMessage());
        dto.put("sessionId", e.getSessionId());
        dto.put("runId", e.getRunId());
        return dto;
    }
}
