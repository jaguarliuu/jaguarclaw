package com.jaguarliu.ai.gateway.rpc.handler.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.schedule.ScheduledTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleUpdateHandler implements RpcHandler {

    private final ScheduledTaskService scheduledTaskService;
    private final ObjectMapper objectMapper;

    @Override
    public String getMethod() {
        return "schedule.update";
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> params = objectMapper.convertValue(request.getPayload(), Map.class);

            String id = value(params.get("id"));
            String name = (String) params.get("name");
            String cronExpr = (String) params.get("cronExpr");
            String prompt = (String) params.get("prompt");
            String targetRef = value(params.get("targetRef"));
            String targetType = value(params.get("targetType"));
            String emailTo = value(params.get("emailTo"));
            String emailCc = value(params.get("emailCc"));
            Object enabledObj = params.get("enabled");

            if (id == null || id.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "id is required");
            }
            if (name == null || name.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "name is required");
            }
            if (cronExpr == null || cronExpr.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "cronExpr is required");
            }
            if (prompt == null || prompt.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "prompt is required");
            }
            if (targetRef == null || targetRef.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "targetRef is required");
            }
            if (targetType == null || targetType.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "targetType is required");
            }
            if (enabledObj == null) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "enabled is required");
            }

            boolean enabled = Boolean.TRUE.equals(enabledObj);
            var task = scheduledTaskService.update(id, name, cronExpr, prompt, targetRef, targetType, emailTo, emailCc, enabled);
            return RpcResponse.success(request.getId(), ScheduledTaskService.toDto(task));
        }).onErrorResume(e -> {
            log.error("Failed to update scheduled task: {}", e.getMessage());
            return Mono.just(RpcResponse.error(request.getId(), "UPDATE_FAILED", e.getMessage()));
        });
    }

    private String value(Object value) {
        if (value == null) {
            return null;
        }
        String str = String.valueOf(value).trim();
        return str.isEmpty() ? null : str;
    }
}
