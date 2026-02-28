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

import java.util.List;
import java.util.Map;

/**
 * session.list 处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionListHandler implements RpcHandler {

    private final SessionService sessionService;
    private final ConnectionManager connectionManager;

    @Override
    public String getMethod() {
        return "session.list";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            String principalId = resolvePrincipalId(connectionId);
            if (principalId == null) {
                return RpcResponse.error(request.getId(), "UNAUTHORIZED", "Missing authenticated principal");
            }

            List<SessionEntity> sessions = sessionService.listMainSessions(principalId);
            List<Map<String, Object>> sessionDtos = sessions.stream()
                    .map(this::toSessionDto)
                    .toList();
            return RpcResponse.success(request.getId(), Map.of("sessions", sessionDtos));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private String resolvePrincipalId(String connectionId) {
        var principal = connectionManager.getPrincipal(connectionId);
        return principal != null ? principal.getPrincipalId() : null;
    }

    private Map<String, Object> toSessionDto(SessionEntity session) {
        return Map.of(
                "id", session.getId(),
                "name", session.getName(),
                "createdAt", session.getCreatedAt().toString(),
                "updatedAt", session.getUpdatedAt().toString()
        );
    }
}
