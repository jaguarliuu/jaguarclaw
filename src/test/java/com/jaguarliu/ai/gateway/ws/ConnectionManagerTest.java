package com.jaguarliu.ai.gateway.ws;

import com.jaguarliu.ai.gateway.security.ConnectionContext;
import com.jaguarliu.ai.gateway.security.ConnectionPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketSession;

import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ConnectionManager Tests")
class ConnectionManagerTest {

    @Test
    @DisplayName("register 后可获取 session 与 context")
    void registerStoresSessionAndContext() {
        ConnectionManager manager = new ConnectionManager();
        WebSocketSession session = mockSession("127.0.0.1", 9000);

        manager.register("conn-1", session);

        assertEquals(session, manager.get("conn-1"));
        ConnectionContext context = manager.getContext("conn-1");
        assertNotNull(context);
        assertFalse(context.isAuthenticated());
        assertNull(context.getPrincipal());
        assertEquals(1, manager.getConnectionCount());
    }

    @Test
    @DisplayName("bindPrincipal 后连接进入已认证状态")
    void bindPrincipalMarksAuthenticated() {
        ConnectionManager manager = new ConnectionManager();
        WebSocketSession session = mockSession("127.0.0.1", 9001);
        manager.register("conn-2", session);

        ConnectionPrincipal principal = ConnectionPrincipal.builder()
                .principalId("device-abc")
                .roles(List.of("local_admin"))
                .build();
        manager.bindPrincipal("conn-2", principal);

        assertTrue(manager.isAuthenticated("conn-2"));
        assertEquals("device-abc", manager.getPrincipal("conn-2").getPrincipalId());
    }

    @Test
    @DisplayName("remove 后连接被清理")
    void removeClearsConnection() {
        ConnectionManager manager = new ConnectionManager();
        WebSocketSession session = mockSession("127.0.0.1", 9002);
        manager.register("conn-3", session);

        manager.remove("conn-3");

        assertNull(manager.get("conn-3"));
        assertNull(manager.getContext("conn-3"));
        assertEquals(0, manager.getConnectionCount());
    }

    private WebSocketSession mockSession(String host, int port) {
        WebSocketSession session = mock(WebSocketSession.class);
        HandshakeInfo info = mock(HandshakeInfo.class);
        when(session.getHandshakeInfo()).thenReturn(info);
        when(info.getRemoteAddress()).thenReturn(new InetSocketAddress(host, port));
        return session;
    }
}

