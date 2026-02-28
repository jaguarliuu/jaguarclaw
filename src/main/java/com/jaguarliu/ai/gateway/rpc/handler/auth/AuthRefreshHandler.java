package com.jaguarliu.ai.gateway.rpc.handler.auth;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.gateway.security.AuthTokenService;
import com.jaguarliu.ai.gateway.security.ConnectionPrincipal;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import com.jaguarliu.ai.nodeconsole.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Token 刷新：auth.refresh
 */
@Component
@RequiredArgsConstructor
public class AuthRefreshHandler implements RpcHandler {

    private final AuthTokenService authTokenService;
    private final ConnectionManager connectionManager;
    private final AuditLogService auditLogService;

    @Override
    public String getMethod() {
        return "auth.refresh";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        Map<String, Object> payload = extractPayload(request.getPayload());
        String refreshToken = toStringOrNull(payload.get("refreshToken"));

        if (refreshToken == null || refreshToken.isBlank()) {
            auditLogService.logSecurityEvent("ws.auth.refresh.invalid_params", null, getMethod(), "rejected", "Missing refreshToken", connectionId, request.getId());
            return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "refreshToken is required"));
        }

        return Mono.justOrEmpty(authTokenService.refresh(refreshToken))
                .map(tokens -> {
                    ConnectionPrincipal principal = ConnectionPrincipal.builder()
                            .principalId(tokens.getPrincipalId())
                            .roles(tokens.getRoles())
                            .build();
                    connectionManager.bindPrincipal(connectionId, principal);
                    auditLogService.logSecurityEvent(
                            "ws.auth.refresh.success",
                            null,
                            getMethod(),
                            "success",
                            "principalId=%s".formatted(tokens.getPrincipalId()),
                            connectionId,
                            request.getId()
                    );
                    return RpcResponse.success(request.getId(), Map.of(
                            "principalId", tokens.getPrincipalId(),
                            "roles", tokens.getRoles(),
                            "accessToken", tokens.getAccessToken(),
                            "refreshToken", tokens.getRefreshToken(),
                            "accessExpiresAt", tokens.getAccessExpiresAt().toString(),
                            "refreshExpiresAt", tokens.getRefreshExpiresAt().toString()
                    ));
                })
                .defaultIfEmpty(onInvalidRefresh(request.getId(), connectionId));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractPayload(Object payload) {
        if (payload instanceof Map) {
            return (Map<String, Object>) payload;
        }
        return Map.of();
    }

    private String toStringOrNull(Object value) {
        return value != null ? value.toString() : null;
    }

    private RpcResponse onInvalidRefresh(String requestId, String connectionId) {
        auditLogService.logSecurityEvent(
                "ws.auth.refresh.invalid_token",
                null,
                getMethod(),
                "rejected",
                "refresh token invalid or expired",
                connectionId,
                requestId
        );
        return RpcResponse.error(requestId, "INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired");
    }
}
