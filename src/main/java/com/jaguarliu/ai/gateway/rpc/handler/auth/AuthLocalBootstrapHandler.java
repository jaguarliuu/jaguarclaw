package com.jaguarliu.ai.gateway.rpc.handler.auth;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.gateway.security.AuthTokenService;
import com.jaguarliu.ai.gateway.security.ConnectionContext;
import com.jaguarliu.ai.gateway.security.ConnectionPrincipal;
import com.jaguarliu.ai.gateway.security.SecurityProperties;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import com.jaguarliu.ai.nodeconsole.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 本地桌面首次引导认证：auth.local.bootstrap
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthLocalBootstrapHandler implements RpcHandler {

    private final ConnectionManager connectionManager;
    private final AuthTokenService authTokenService;
    private final SecurityProperties securityProperties;
    private final AuditLogService auditLogService;

    @Override
    public String getMethod() {
        return "auth.local.bootstrap";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        Map<String, Object> payload = extractPayload(request.getPayload());
        String deviceId = toStringOrNull(payload.get("deviceId"));

        if (deviceId == null || deviceId.isBlank()) {
            auditLogService.logSecurityEvent("ws.auth.bootstrap.invalid_params", null, getMethod(), "rejected", "Missing deviceId", connectionId, request.getId());
            return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "deviceId is required"));
        }

        ConnectionContext context = connectionManager.getContext(connectionId);
        if (context == null) {
            auditLogService.logSecurityEvent("ws.auth.bootstrap.connection_missing", null, getMethod(), "rejected", "Connection not found", connectionId, request.getId());
            return Mono.just(RpcResponse.error(request.getId(), "CONNECTION_NOT_FOUND", "Connection not found"));
        }

        if (securityProperties.getWs().isLocalOnlyBootstrap() && !isLoopback(context.getClientIp())) {
            log.warn("Bootstrap forbidden: connectionId={}, clientIp={}", connectionId, context.getClientIp());
            auditLogService.logSecurityEvent(
                    "ws.auth.bootstrap.forbidden",
                    null,
                    getMethod(),
                    "rejected",
                    "clientIp=%s".formatted(context.getClientIp()),
                    connectionId,
                    request.getId()
            );
            return Mono.just(RpcResponse.error(request.getId(), "BOOTSTRAP_FORBIDDEN", "Bootstrap is allowed only from localhost"));
        }

        List<String> roles = List.of("local_admin");
        ConnectionPrincipal principal = ConnectionPrincipal.builder()
                .principalId(deviceId)
                .roles(roles)
                .build();
        connectionManager.bindPrincipal(connectionId, principal);

        AuthTokenService.TokenPair tokens = authTokenService.issueTokens(deviceId, roles);
        auditLogService.logSecurityEvent(
                "ws.auth.bootstrap.success",
                null,
                getMethod(),
                "success",
                "principalId=%s".formatted(deviceId),
                connectionId,
                request.getId()
        );
        return Mono.just(RpcResponse.success(request.getId(), Map.of(
                "principalId", deviceId,
                "roles", roles,
                "accessToken", tokens.getAccessToken(),
                "refreshToken", tokens.getRefreshToken(),
                "accessExpiresAt", tokens.getAccessExpiresAt().toString(),
                "refreshExpiresAt", tokens.getRefreshExpiresAt().toString()
        )));
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

    private boolean isLoopback(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return false;
        }
        return clientIp.contains("127.0.0.1")
                || clientIp.contains("0:0:0:0:0:0:0:1")
                || clientIp.contains("::1")
                || clientIp.contains("localhost");
    }
}
