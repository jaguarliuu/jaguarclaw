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
public class ScheduleCreateHandler implements RpcHandler {

    private final ScheduledTaskService scheduledTaskService;
    private final ObjectMapper objectMapper;

    @Override
    public String getMethod() {
        return "schedule.create";
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> params = objectMapper.convertValue(request.getPayload(), Map.class);

            String name = (String) params.get("name");
            String cronExpr = (String) params.get("cronExpr");
            String prompt = (String) params.get("prompt");
            String targetRef = value(params.get("targetRef"));
            String targetType = value(params.get("targetType"));
            String emailTo = (String) params.get("emailTo");
            String emailCc = (String) params.get("emailCc");

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

            var task = scheduledTaskService.create(name, cronExpr, prompt, targetRef, targetType, emailTo, emailCc);
            return RpcResponse.success(request.getId(), ScheduledTaskService.toDto(task));
        }).onErrorResume(e -> {
            log.error("Failed to create scheduled task: {}", e.getMessage());
            return Mono.just(RpcResponse.error(request.getId(), "CREATE_FAILED", e.getMessage()));
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
