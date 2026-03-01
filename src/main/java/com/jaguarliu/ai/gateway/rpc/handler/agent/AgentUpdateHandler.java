package com.jaguarliu.ai.gateway.rpc.handler.agent;

import com.jaguarliu.ai.agents.entity.AgentProfileEntity;
import com.jaguarliu.ai.agents.service.AgentProfileService;
import com.jaguarliu.ai.feature.FeatureFlagsProperties;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentUpdateHandler implements RpcHandler {

    private final AgentProfileService agentProfileService;
    private final ConnectionManager connectionManager;
    private final FeatureFlagsProperties featureFlags;

    @Override
    public String getMethod() {
        return "agent.update";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            if (!featureFlags.isAgentControlPlane()) {
                return RpcResponse.error(request.getId(), "FEATURE_DISABLED", "Agent control plane is disabled");
            }
            if (resolvePrincipalId(connectionId) == null) {
                return RpcResponse.error(request.getId(), "UNAUTHORIZED", "Missing authenticated principal");
            }

            if (!(request.getPayload() instanceof Map<?, ?> payload)) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "Payload must be an object");
            }

            try {
                AgentProfileService.UpdateAgentProfileRequest updateRequest =
                        new AgentProfileService.UpdateAgentProfileRequest(
                                asString(payload, "agentId"),
                                asString(payload, "name"),
                                asString(payload, "displayName"),
                                asString(payload, "description"),
                                asString(payload, "workspacePath"),
                                asString(payload, "model"),
                                asBoolean(payload, "enabled"),
                                asBoolean(payload, "isDefault"),
                                asStringList(payload, "allowedTools"),
                                asStringList(payload, "excludedTools"),
                                asInteger(payload, "heartbeatInterval"),
                                asString(payload, "heartbeatActiveHours"),
                                asInteger(payload, "dailyTokenLimit"),
                                asDouble(payload, "monthlyCostLimit")
                        );

                AgentProfileEntity updated = agentProfileService.update(updateRequest);
                return RpcResponse.success(request.getId(), toDto(updated));
            } catch (IllegalStateException e) {
                return RpcResponse.error(request.getId(), "INVALID_STATE", e.getMessage());
            } catch (IllegalArgumentException e) {
                if (e.getMessage() != null && e.getMessage().startsWith("Agent not found")) {
                    return RpcResponse.error(request.getId(), "NOT_FOUND", e.getMessage());
                }
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", e.getMessage());
            } catch (Exception e) {
                log.error("agent.update failed", e);
                return RpcResponse.error(request.getId(), "INTERNAL_ERROR", "Failed to update agent");
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private String resolvePrincipalId(String connectionId) {
        var principal = connectionManager.getPrincipal(connectionId);
        return principal != null ? principal.getPrincipalId() : null;
    }

    private String asString(Map<?, ?> payload, String key) {
        Object value = payload.get(key);
        return value != null ? value.toString() : null;
    }

    private Boolean asBoolean(Map<?, ?> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private Integer asInteger(Map<?, ?> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private Double asDouble(Map<?, ?> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    private List<String> asStringList(Map<?, ?> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (value instanceof String s && !s.isBlank()) {
            return List.of(s);
        }
        return null;
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
