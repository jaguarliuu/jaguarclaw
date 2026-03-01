package com.jaguarliu.ai.gateway.rpc.handler.session;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import com.jaguarliu.ai.session.SessionService;
import com.jaguarliu.ai.storage.entity.SessionEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

/**
 * session.create 处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionCreateHandler implements RpcHandler {

    private final SessionService sessionService;
    private final ConnectionManager connectionManager;

    @Override
    public String getMethod() {
        return "session.create";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            String principalId = resolvePrincipalId(connectionId);
            if (principalId == null) {
                return RpcResponse.error(request.getId(), "UNAUTHORIZED", "Missing authenticated principal");
            }

            String name = extractName(request.getPayload());
            String agentId = extractAgentId(request.getPayload());
            SessionEntity session = sessionService.create(name, agentId, principalId);
            return RpcResponse.success(request.getId(), toSessionDto(session));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private String resolvePrincipalId(String connectionId) {
        var principal = connectionManager.getPrincipal(connectionId);
        return principal != null ? principal.getPrincipalId() : null;
    }

    private String extractName(Object payload) {
        if (payload instanceof Map) {
            Object name = ((Map<?, ?>) payload).get("name");
            return name != null ? name.toString() : null;
        }
        return null;
    }

    private String extractAgentId(Object payload) {
        if (payload instanceof Map) {
            Object agentId = ((Map<?, ?>) payload).get("agentId");
            return agentId != null ? agentId.toString() : null;
        }
        return null;
    }

    private Map<String, Object> toSessionDto(SessionEntity session) {
        return Map.of(
                "id", session.getId(),
                "name", session.getName(),
                "agentId", session.getAgentId(),
                "createdAt", session.getCreatedAt().toString(),
                "updatedAt", session.getUpdatedAt().toString()
        );
    }
}
