package com.jaguarliu.ai.gateway.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.reactive.socket.WebSocketSession;

import java.time.LocalDateTime;

/**
 * WebSocket 连接上下文
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionContext {

    /**
     * 连接 ID
     */
    private String connectionId;

    /**
     * WebSocket 会话
     */
    private WebSocketSession session;

    /**
     * 连接主体（认证后写入）
     */
    private ConnectionPrincipal principal;

    /**
     * 客户端 IP
     */
    private String clientIp;

    /**
     * 建连时间
     */
    private LocalDateTime connectedAt;

    /**
     * 是否已认证
     */
    private boolean authenticated;
}

