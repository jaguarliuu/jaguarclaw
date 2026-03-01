package com.jaguarliu.ai.gateway.rpc.handler.agent;

import com.jaguarliu.ai.agents.entity.AgentProfileEntity;
import com.jaguarliu.ai.agents.service.AgentProfileService;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AgentListHandler implements RpcHandler {

    private final AgentProfileService agentProfileService;
    private final ConnectionManager connectionManager;

    @Override
    public String getMethod() {
        return "agent.list";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            if (resolvePrincipalId(connectionId) == null) {
                return RpcResponse.error(request.getId(), "UNAUTHORIZED", "Missing authenticated principal");
            }

            List<Map<String, Object>> agents = agentProfileService.list().stream()
                    .map(this::toDto)
                    .toList();
            return RpcResponse.success(request.getId(), Map.of("agents", agents));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private String resolvePrincipalId(String connectionId) {
        var principal = connectionManager.getPrincipal(connectionId);
        return principal != null ? principal.getPrincipalId() : null;
    }

    private Map<String, Object> toDto(AgentProfileEntity entity) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", entity.getId());
        dto.put("name", entity.getName());
        dto.put("displayName", entity.getDisplayName());
        dto.put("description", entity.getDescription() != null ? entity.getDescription() : "");
        dto.put("workspacePath", entity.getWorkspacePath());
        dto.put("model", entity.getModel() != null ? entity.getModel() : "");
        dto.put("enabled", entity.getEnabled());
        dto.put("isDefault", entity.getIsDefault());
        dto.put("allowedTools", entity.getAllowedTools() != null ? entity.getAllowedTools() : "[]");
        dto.put("excludedTools", entity.getExcludedTools() != null ? entity.getExcludedTools() : "[]");
        dto.put("heartbeatInterval", entity.getHeartbeatInterval() != null ? entity.getHeartbeatInterval() : 0);
        dto.put("heartbeatActiveHours", entity.getHeartbeatActiveHours() != null ? entity.getHeartbeatActiveHours() : "");
        dto.put("dailyTokenLimit", entity.getDailyTokenLimit() != null ? entity.getDailyTokenLimit() : 0);
        dto.put("monthlyCostLimit", entity.getMonthlyCostLimit() != null ? entity.getMonthlyCostLimit() : 0.0);
        dto.put("createdAt", entity.getCreatedAt().toString());
        dto.put("updatedAt", entity.getUpdatedAt().toString());
        return dto;
    }
}
