package com.jaguarliu.ai.gateway.rpc.handler.soul;

import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.soul.SoulConfigService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SoulRpcHandlersTest {

    @Test
    void soulGet_returnsThreeMdFiles() {
        SoulConfigService svc = mock(SoulConfigService.class);
        when(svc.readSoulMd("main")).thenReturn("# Soul\n");
        when(svc.readRuleMd("main")).thenReturn("# Rules\n");
        when(svc.readProfileMd("main")).thenReturn("# Profile\n");

        SoulGetHandler handler = new SoulGetHandler(svc);
        RpcRequest req = RpcRequest.builder().type("request").id("1").method("soul.get").build();

        RpcResponse resp = handler.handle("conn-1", req).block();
        assertNotNull(resp);
        assertEquals("response", resp.getType());
        assertEquals("1", resp.getId());
        assertNull(resp.getError());

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) resp.getPayload();
        assertEquals("# Soul\n", payload.get("soul"));
        assertEquals("# Rules\n", payload.get("rule"));
        assertEquals("# Profile\n", payload.get("profile"));
    }

    @Test
    void soulGet_withAgentId_readsAgentScopedFiles() {
        SoulConfigService svc = mock(SoulConfigService.class);
        when(svc.readSoulMd("agent-a")).thenReturn("# Agent A Soul\n");
        when(svc.readRuleMd("agent-a")).thenReturn("");
        when(svc.readProfileMd("agent-a")).thenReturn("");

        SoulGetHandler handler = new SoulGetHandler(svc);
        RpcRequest req = RpcRequest.builder()
                .type("request").id("3").method("soul.get")
                .payload(Map.of("agentId", "agent-a"))
                .build();

        RpcResponse resp = handler.handle("conn-1", req).block();
        assertNotNull(resp);
        assertNull(resp.getError());
        verify(svc, times(1)).readSoulMd("agent-a");
        verify(svc, times(1)).readRuleMd("agent-a");
        verify(svc, times(1)).readProfileMd("agent-a");
    }

    @Test
    void soulSave_soulFile_writesToSoulMd() {
        SoulConfigService svc = mock(SoulConfigService.class);
        SoulSaveHandler handler = new SoulSaveHandler(svc);

        RpcRequest req = RpcRequest.builder()
                .type("request").id("2").method("soul.save")
                .payload(Map.of("agentId", "main", "file", "soul", "content", "# New Soul\n"))
                .build();

        RpcResponse resp = handler.handle("conn-1", req).block();
        assertNotNull(resp);
        assertNull(resp.getError());

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) resp.getPayload();
        assertEquals(Boolean.TRUE, payload.get("success"));

        verify(svc, times(1)).writeSoulMd("main", "# New Soul\n");
    }

    @Test
    void soulSave_ruleFile_writesToRuleMd() {
        SoulConfigService svc = mock(SoulConfigService.class);
        SoulSaveHandler handler = new SoulSaveHandler(svc);

        RpcRequest req = RpcRequest.builder()
                .type("request").id("4").method("soul.save")
                .payload(Map.of("agentId", "agent-b", "file", "rule", "content", "# Rules\n"))
                .build();

        RpcResponse resp = handler.handle("conn-1", req).block();
        assertNotNull(resp);
        assertNull(resp.getError());
        verify(svc, times(1)).writeRuleMd("agent-b", "# Rules\n");
    }

    @Test
    void soulSave_profileFile_writesToProfileMd() {
        SoulConfigService svc = mock(SoulConfigService.class);
        SoulSaveHandler handler = new SoulSaveHandler(svc);

        RpcRequest req = RpcRequest.builder()
                .type("request").id("5").method("soul.save")
                .payload(Map.of("agentId", "agent-c", "file", "profile", "content", "# Profile\n"))
                .build();

        RpcResponse resp = handler.handle("conn-1", req).block();
        assertNotNull(resp);
        assertNull(resp.getError());
        verify(svc, times(1)).writeProfileMd("agent-c", "# Profile\n");
    }

    @Test
    void soulSave_invalidFile_returnsError() {
        SoulConfigService svc = mock(SoulConfigService.class);
        SoulSaveHandler handler = new SoulSaveHandler(svc);

        RpcRequest req = RpcRequest.builder()
                .type("request").id("6").method("soul.save")
                .payload(Map.of("agentId", "main", "file", "unknown", "content", "x"))
                .build();

        RpcResponse resp = handler.handle("conn-1", req).block();
        assertNotNull(resp);
        assertNotNull(resp.getError());
    }
}
