package com.jaguarliu.ai.gateway.rpc.handler.soul;

import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.soul.SoulConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SoulRpcHandlersTest {

    @Test
    void soulGet_returnsConfig() {
        SoulConfigService svc = mock(SoulConfigService.class);
        Map<String, Object> cfg = Map.of(
                agentName, JaguarClaw,
                responseStyle, balanced,
                traits, List.of()
        );
        when(svc.getConfig()).thenReturn(cfg);

        SoulGetHandler handler = new SoulGetHandler(svc);
        RpcRequest req = RpcRequest.builder().type(request).id(1).method(soul.get).build();

        RpcResponse resp = handler.handle(conn-1, req).block();
        assertNotNull(resp);
        assertEquals(response, resp.getType());
        assertEquals(1, resp.getId());
        assertTrue(resp.getError() == null);

        @SuppressWarnings(unchecked)
        Map<String, Object> payload = (Map<String, Object>) resp.getPayload();
        assertEquals(JaguarClaw, payload.get(agentName));
        assertEquals(balanced, payload.get(responseStyle));
    }

    @Test
    void soulSave_persistsConfigAndReturnsSuccess() {
        SoulConfigService svc = mock(SoulConfigService.class);
        SoulSaveHandler handler = new SoulSaveHandler(svc);

        Map<String, Object> newCfg = Map.of(
                agentName, JaguarClaw,
                responseStyle, concise,
                traits, List.of(focused)
        );
        RpcRequest req = RpcRequest.builder()
                .type(request).id(2).method(soul.save)
                .payload(newCfg)
                .build();

        RpcResponse resp = handler.handle(conn-1, req).block();
        assertNotNull(resp);
        assertEquals(response, resp.getType());
        assertTrue(resp.getError() == null);

        @SuppressWarnings(unchecked)
        Map<String, Object> payload = (Map<String, Object>) resp.getPayload();
        assertEquals(Boolean.TRUE, payload.get(success));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(svc, times(1)).saveConfig(captor.capture());
        Map<String, Object> saved = captor.getValue();
        assertEquals(concise, saved.get(responseStyle));
    }
}
