package com.jaguarliu.ai.gateway.rpc.handler.agent;

import com.jaguarliu.ai.agents.service.AgentProfileService;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AgentDeleteHandler implements RpcHandler {

    private final AgentProfileService agentProfileService;
    private final ConnectionManager connectionManager;

    @Override
    public String getMethod() {
        return "agent.delete";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            if (resolvePrincipalId(connectionId) == null) {
                return RpcResponse.error(request.getId(), "UNAUTHORIZED", "Missing authenticated principal");
            }

            String agentId = extractString(request.getPayload(), "agentId");
            if (agentId == null || agentId.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "Missing agentId");
            }

            try {
                agentProfileService.delete(agentId);
                return RpcResponse.success(request.getId(), Map.of("deleted", true, "agentId", agentId));
            } catch (IllegalStateException e) {
                return RpcResponse.error(request.getId(), "INVALID_STATE", e.getMessage());
            } catch (IllegalArgumentException e) {
                if (e.getMessage() != null && e.getMessage().startsWith("Agent not found")) {
                    return RpcResponse.error(request.getId(), "NOT_FOUND", e.getMessage());
                }
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", e.getMessage());
            } catch (Exception e) {
                return RpcResponse.error(request.getId(), "INTERNAL_ERROR", e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private String resolvePrincipalId(String connectionId) {
        var principal = connectionManager.getPrincipal(connectionId);
        return principal != null ? principal.getPrincipalId() : null;
    }

    private String extractString(Object payload, String key) {
        if (payload instanceof Map<?, ?> map) {
            Object value = map.get(key);
            return value != null ? value.toString() : null;
        }
        return null;
    }
}

