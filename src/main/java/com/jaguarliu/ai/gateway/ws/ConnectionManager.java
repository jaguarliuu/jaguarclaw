package com.jaguarliu.ai.gateway.ws;

import com.jaguarliu.ai.gateway.security.ConnectionContext;
import com.jaguarliu.ai.gateway.security.ConnectionPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 连接管理器
 * 维护 connectionId → ConnectionContext 映射
 */
@Slf4j
@Component
public class ConnectionManager {

    private final Map<String, ConnectionContext> connections = new ConcurrentHashMap<>();

    /**
     * 注册连接
     */
    public void register(String connectionId, WebSocketSession session) {
        ConnectionContext context = ConnectionContext.builder()
                .connectionId(connectionId)
                .session(session)
                .clientIp(extractClientIp(session))
                .connectedAt(LocalDateTime.now())
                .authenticated(false)
                .build();
        connections.put(connectionId, context);
        log.info("WebSocket connected: connectionId={}, clientIp={}, total={}",
                connectionId, context.getClientIp(), connections.size());
    }

    /**
     * 注册连接上下文（用于后续扩展）
     */
    public void register(ConnectionContext context) {
        connections.put(context.getConnectionId(), context);
        log.info("WebSocket connected: connectionId={}, clientIp={}, total={}",
                context.getConnectionId(), context.getClientIp(), connections.size());
    }

    /**
     * 移除连接
     */
    public void remove(String connectionId) {
        ConnectionContext context = connections.remove(connectionId);
        if (context != null && context.getOutboundSink() != null) {
            context.getOutboundSink().tryEmitComplete();
        }
        log.info("WebSocket disconnected: connectionId={}, total={}", connectionId, connections.size());
    }

    /**
     * 获取连接
     */
    public WebSocketSession get(String connectionId) {
        ConnectionContext context = connections.get(connectionId);
        return context != null ? context.getSession() : null;
    }

    /**
     * 获取连接上下文
     */
    public ConnectionContext getContext(String connectionId) {
        return connections.get(connectionId);
    }


    public Flux<String> outbound(String connectionId) {
        ConnectionContext context = connections.get(connectionId);
        if (context == null || context.getOutboundSink() == null) {
            return Flux.empty();
        }
        return context.getOutboundSink().asFlux();
    }

    public boolean emit(String connectionId, String payload) {
        ConnectionContext context = connections.get(connectionId);
        if (context == null || context.getOutboundSink() == null) {
            return false;
        }
        Sinks.EmitResult result = context.getOutboundSink().tryEmitNext(payload);
        return result.isSuccess();
    }

    /**
     * 获取连接主体
     */
    public ConnectionPrincipal getPrincipal(String connectionId) {
        ConnectionContext context = connections.get(connectionId);
        return context != null ? context.getPrincipal() : null;
    }

    /**
     * 判断连接是否已认证
     */
    public boolean isAuthenticated(String connectionId) {
        ConnectionContext context = connections.get(connectionId);
        return context != null && context.isAuthenticated();
    }

    /**
     * 绑定认证主体
     */
    public void bindPrincipal(String connectionId, ConnectionPrincipal principal) {
        ConnectionContext context = connections.get(connectionId);
        if (context == null) {
            log.warn("Cannot bind principal, connection not found: connectionId={}", connectionId);
            return;
        }
        context.setPrincipal(principal);
        context.setAuthenticated(true);
        log.info("Principal bound: connectionId={}, principalId={}",
                connectionId, principal != null ? principal.getPrincipalId() : null);
    }

    /**
     * 获取当前连接数
     */
    public int getConnectionCount() {
        return connections.size();
    }

    /**
     * 获取所有连接 ID
     */
    public Set<String> getAllConnectionIds() {
        return connections.keySet();
    }

    private String extractClientIp(WebSocketSession session) {
        return Optional.ofNullable(session.getHandshakeInfo())
                .map(info -> info.getRemoteAddress())
                .map(Object::toString)
                .orElse("unknown");
    }
}
