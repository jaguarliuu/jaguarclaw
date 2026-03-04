package com.jaguarliu.ai.gateway.rpc.handler.node;

import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.nodeconsole.NodeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NodeTestHandler Tests")
class NodeTestHandlerTest {

    @Mock
    private NodeService nodeService;

    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @InjectMocks
    private NodeTestHandler handler;

    @Test
    @DisplayName("returns detailed test report payload")
    @SuppressWarnings("unchecked")
    void returnsDetailedPayload() {
        LocalDateTime testedAt = LocalDateTime.of(2026, 3, 4, 12, 0);
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(Map.of("id", "node-1"));
        when(nodeService.testConnectionDetailed("node-1")).thenReturn(
                new NodeService.ConnectionTestReport(
                        "node-1", "prod-web-1", false, "NETWORK_ERROR",
                        "SSH network connection failed", 1200L, testedAt
                )
        );

        RpcRequest request = RpcRequest.builder()
                .id("req-1")
                .method("nodes.test")
                .payload(Map.of("id", "node-1"))
                .build();

        RpcResponse response = handler.handle("conn-1", request).block();
        assertNotNull(response);
        assertNull(response.getError());

        Map<String, Object> payload = (Map<String, Object>) response.getPayload();
        assertEquals(false, payload.get("success"));
        assertEquals("node-1", payload.get("nodeId"));
        assertEquals("prod-web-1", payload.get("nodeAlias"));
        assertEquals("NETWORK_ERROR", payload.get("errorType"));
        assertEquals("SSH network connection failed", payload.get("message"));
        assertEquals(1200L, ((Number) payload.get("durationMs")).longValue());
        assertEquals("2026-03-04T12:00", payload.get("testedAt"));
    }

    @Test
    @DisplayName("returns invalid params when id is missing")
    void rejectsMissingId() {
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(Map.of());

        RpcRequest request = RpcRequest.builder()
                .id("req-2")
                .method("nodes.test")
                .payload(Map.of())
                .build();

        RpcResponse response = handler.handle("conn-1", request).block();
        assertNotNull(response);
        assertNotNull(response.getError());
        assertEquals("INVALID_PARAMS", response.getError().getCode());
    }

    @Test
    @DisplayName("returns success when optional errorType is null")
    @SuppressWarnings("unchecked")
    void returnsSuccessWhenErrorTypeIsNull() {
        LocalDateTime testedAt = LocalDateTime.of(2026, 3, 4, 16, 32, 54);
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(Map.of("id", "node-1"));
        when(nodeService.testConnectionDetailed("node-1")).thenReturn(
                new NodeService.ConnectionTestReport(
                        "node-1", "yajun", true, null,
                        "Connection successful", 123L, testedAt
                )
        );

        RpcRequest request = RpcRequest.builder()
                .id("req-3")
                .method("nodes.test")
                .payload(Map.of("id", "node-1"))
                .build();

        RpcResponse response = handler.handle("conn-1", request).block();
        assertNotNull(response);
        assertNull(response.getError());

        Map<String, Object> payload = (Map<String, Object>) response.getPayload();
        assertEquals(true, payload.get("success"));
        assertEquals("node-1", payload.get("nodeId"));
        assertEquals("yajun", payload.get("nodeAlias"));
        assertNull(payload.get("errorType"));
        assertEquals("Connection successful", payload.get("message"));
        assertEquals(123L, ((Number) payload.get("durationMs")).longValue());
        assertEquals("2026-03-04T16:32:54", payload.get("testedAt"));
    }
}
