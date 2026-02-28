package com.jaguarliu.ai.gateway.rpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.gateway.security.ConnectionPrincipal;
import com.jaguarliu.ai.gateway.security.ReplayGuard;
import com.jaguarliu.ai.gateway.security.RpcAuthorizationService;
import com.jaguarliu.ai.gateway.security.RpcPermission;
import com.jaguarliu.ai.gateway.security.SecurityProperties;
import com.jaguarliu.ai.gateway.security.rate.MessageRateLimiter;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import com.jaguarliu.ai.nodeconsole.AuditLogService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RPC 路由器
 * 根据 method 字段分发请求到对应的 handler
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RpcRouter {

    private final ObjectMapper objectMapper;
    private final List<RpcHandler> handlers;
    private final ConnectionManager connectionManager;
    private final SecurityProperties securityProperties;
    private final RpcAuthorizationService rpcAuthorizationService;
    private final MessageRateLimiter messageRateLimiter;
    private final ReplayGuard replayGuard;
    private final AuditLogService auditLogService;
    private final Map<String, RpcHandler> handlerMap = new HashMap<>();

    @PostConstruct
    public void init() {
        for (RpcHandler handler : handlers) {
            handlerMap.put(handler.getMethod(), handler);
            log.info("Registered RPC handler: {}", handler.getMethod());
        }
    }

    /**
     * 解析并路由请求
     * @param connectionId 连接 ID
     * @param message 原始 JSON 消息
     * @return 响应 JSON
     */
    public Mono<String> route(String connectionId, String message) {
        try {
            RpcRequest request = objectMapper.readValue(message, RpcRequest.class);

            if (!"request".equals(request.getType())) {
                return Mono.just(toJson(RpcResponse.error(
                        request.getId(), "INVALID_TYPE", "Expected type 'request'")));
            }

            String method = request.getMethod();
            if (method == null || method.isBlank()) {
                return Mono.just(toJson(RpcResponse.error(
                        request.getId(), "INVALID_METHOD", "Missing method")));
            }

            if (!isMethodAllowedForConnection(connectionId, method)) {
                auditDeny("ws.rpc.unauthorized", connectionId, method, "Authentication required");
                return Mono.just(toJson(RpcResponse.error(
                        request.getId(), "UNAUTHORIZED", "Authentication required for method: " + method)));
            }

            if (!messageRateLimiter.allow(connectionId)) {
                auditDeny("ws.rpc.rate_limited", connectionId, method, "RPC rate limit exceeded");
                return Mono.just(toJson(RpcResponse.error(
                        request.getId(), "RATE_LIMITED", "Too many RPC requests, please retry later")));
            }

            ConnectionPrincipal principal = connectionManager.getPrincipal(connectionId);
            if (!rpcAuthorizationService.isAuthorized(principal, method)) {
                auditDeny("ws.rpc.permission_denied", connectionId, method, "Permission denied");
                return Mono.just(toJson(RpcResponse.error(
                        request.getId(), "PERMISSION_DENIED", "Permission denied for method: " + method)));
            }

            RpcPermission permission = rpcAuthorizationService.resolveRequiredPermission(method);
            boolean strictReplay = permission == RpcPermission.WRITE
                    || permission == RpcPermission.CONFIG
                    || permission == RpcPermission.DANGEROUS
                    || permission == RpcPermission.ADMIN;
            ReplayGuard.ValidationResult replayCheck = replayGuard.validate(request, strictReplay);
            if (!replayCheck.allowed()) {
                auditDeny("ws.rpc.replay_blocked", connectionId, method, replayCheck.message());
                return Mono.just(toJson(RpcResponse.error(
                        request.getId(), replayCheck.code(), replayCheck.message())));
            }

            RpcHandler handler = handlerMap.get(method);

            if (handler == null) {
                log.warn("Unknown method: {}", method);
                return Mono.just(toJson(RpcResponse.error(
                        request.getId(), "METHOD_NOT_FOUND", "Unknown method: " + method)));
            }

            log.debug("Routing request: method={}, id={}", method, request.getId());
            return handler.handle(connectionId, request)
                    .map(this::toJson)
                    .onErrorResume(e -> {
                        log.error("Handler error: method={}", method, e);
                        return Mono.just(toJson(RpcResponse.error(
                                request.getId(), "INTERNAL_ERROR", e.getMessage())));
                    });

        } catch (JsonProcessingException e) {
            log.error("Failed to parse request: {}", message, e);
            auditDeny("ws.rpc.parse_error", connectionId, null, "Invalid JSON");
            return Mono.just(toJson(RpcResponse.error(null, "PARSE_ERROR", "Invalid JSON")));
        }
    }

    private boolean isMethodAllowedForConnection(String connectionId, String method) {
        if (connectionManager.isAuthenticated(connectionId)) {
            return true;
        }
        return securityProperties.getWs().getAllowAnonymousMethods().contains(method);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize response", e);
            return "{\"type\":\"response\",\"error\":{\"code\":\"SERIALIZE_ERROR\",\"message\":\"Failed to serialize response\"}}";
        }
    }

    private void auditDeny(String eventType, String connectionId, String method, String reason) {
        auditLogService.logSecurityEvent(
                eventType,
                null,
                method,
                "rejected",
                "connectionId=%s, reason=%s".formatted(connectionId, reason)
        );
    }
}
