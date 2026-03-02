package com.jaguarliu.ai.gateway.rpc.handler.agent;

import com.jaguarliu.ai.agents.entity.AgentProfileEntity;
import com.jaguarliu.ai.agents.service.AgentProfileService;
import com.jaguarliu.ai.feature.FeatureFlagsProperties;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AgentGetHandler implements RpcHandler {

    private final AgentProfileService agentProfileService;
    private final ConnectionManager connectionManager;
    private final FeatureFlagsProperties featureFlags;

    @Override
    public String getMethod() {
        return "agent.get";
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

            String agentId = AgentDtoMapper.extractString(request.getPayload(), "agentId");
            if (agentId == null || agentId.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "Missing agentId");
            }

            Optional<AgentProfileEntity> entity = agentProfileService.get(agentId);
            if (entity.isEmpty()) {
                return RpcResponse.error(request.getId(), "NOT_FOUND", "Agent not found: " + agentId);
            }
            return RpcResponse.success(request.getId(), AgentDtoMapper.toDto(entity.get()));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private String resolvePrincipalId(String connectionId) {
        var principal = connectionManager.getPrincipal(connectionId);
        return principal != null ? principal.getPrincipalId() : null;
    }
}
