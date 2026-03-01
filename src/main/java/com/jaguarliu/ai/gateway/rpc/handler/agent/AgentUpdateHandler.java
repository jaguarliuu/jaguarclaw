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
                                AgentDtoMapper.asString(payload, "agentId"),
                                AgentDtoMapper.asString(payload, "name"),
                                AgentDtoMapper.asString(payload, "displayName"),
                                AgentDtoMapper.asString(payload, "description"),
                                AgentDtoMapper.asString(payload, "workspacePath"),
                                AgentDtoMapper.asString(payload, "model"),
                                AgentDtoMapper.asBoolean(payload, "enabled"),
                                AgentDtoMapper.asBoolean(payload, "isDefault"),
                                AgentDtoMapper.asStringList(payload, "allowedTools"),
                                AgentDtoMapper.asStringList(payload, "excludedTools"),
                                AgentDtoMapper.asInteger(payload, "heartbeatInterval"),
                                AgentDtoMapper.asString(payload, "heartbeatActiveHours"),
                                AgentDtoMapper.asInteger(payload, "dailyTokenLimit"),
                                AgentDtoMapper.asDouble(payload, "monthlyCostLimit")
                        );

                AgentProfileEntity updated = agentProfileService.update(updateRequest);
                return RpcResponse.success(request.getId(), AgentDtoMapper.toDto(updated));
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
}
