package com.jaguarliu.ai.gateway.ws;

import com.jaguarliu.ai.gateway.rpc.RpcRouter;
import com.jaguarliu.ai.gateway.security.rate.ConnectionRateLimiter;
import com.jaguarliu.ai.gateway.security.rate.MessageRateLimiter;
import com.jaguarliu.ai.nodeconsole.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * WebSocket 处理器
 * 处理 WebSocket 连接的建立、消息收发、断开
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayWebSocketHandler implements WebSocketHandler {

    private final ConnectionManager connectionManager;
    private final RpcRouter rpcRouter;
    private final ConnectionRateLimiter connectionRateLimiter;
    private final MessageRateLimiter messageRateLimiter;
    private final AuditLogService auditLogService;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String clientIp = extractClientIp(session);
        if (!connectionRateLimiter.allow(clientIp)) {
            log.warn("WebSocket connection rejected by rate limit: clientIp={}", clientIp);
            auditLogService.logSecurityEvent(
                    "ws.connection.rate_limited",
                    null,
                    "ws.connect",
                    "rejected",
                    "clientIp=%s".formatted(clientIp),
                    null,
                    null
            );
            return session.close(CloseStatus.POLICY_VIOLATION);
        }

        String connectionId = generateConnectionId();

        // 注册连接
        connectionManager.register(connectionId, session);
        auditLogService.logSecurityEvent(
                "ws.connection.accepted",
                null,
                "ws.connect",
                "success",
                "clientIp=%s".formatted(clientIp),
                connectionId,
                null
        );

        // 处理接收到的消息并发送响应
        // agent.run 等需要排队的请求会立即返回 runId，结果通过事件推送
        Flux<WebSocketMessage> output = session.receive()
                .filter(message -> message.getType() == WebSocketMessage.Type.TEXT)
                .flatMap(message -> handleMessage(connectionId, session, message))
                .doOnError(error -> {
                    log.error("WebSocket error: connectionId={}", connectionId, error);
                    auditLogService.logSecurityEvent(
                            "ws.connection.error",
                            null,
                            "ws.receive",
                            "error",
                            error.getMessage(),
                            connectionId,
                            null
                    );
                })
                .doFinally(signalType -> {
                    connectionManager.remove(connectionId);
                    messageRateLimiter.clear(connectionId);
                    auditLogService.logSecurityEvent(
                            "ws.connection.closed",
                            null,
                            "ws.disconnect",
                            "success",
                            "signal=%s".formatted(signalType),
                            connectionId,
                            null
                    );
                });

        return session.send(output);
    }

    /**
     * 处理接收到的消息并返回响应
     */
    private Mono<WebSocketMessage> handleMessage(String connectionId, WebSocketSession session, WebSocketMessage message) {
        String payload = message.getPayloadAsText();
        log.debug("Received message: connectionId={}, bytes={}", connectionId, payload != null ? payload.length() : 0);

        return rpcRouter.route(connectionId, payload)
                .map(session::textMessage);
    }

    /**
     * 生成连接 ID
     */
    private String generateConnectionId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String extractClientIp(WebSocketSession session) {
        if (session.getHandshakeInfo() == null || session.getHandshakeInfo().getRemoteAddress() == null) {
            return "unknown";
        }
        return session.getHandshakeInfo().getRemoteAddress().toString();
    }
}
