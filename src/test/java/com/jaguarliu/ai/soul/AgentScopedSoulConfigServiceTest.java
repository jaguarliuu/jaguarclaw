package com.jaguarliu.ai.soul;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.agents.context.AgentWorkspaceResolver;
import com.jaguarliu.ai.heartbeat.HeartbeatConfigService;
import com.jaguarliu.ai.gateway.rpc.handler.soul.SoulGetHandler;
import com.jaguarliu.ai.gateway.rpc.handler.soul.SoulSaveHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Agent Scoped Soul/Heartbeat Config Tests")
class AgentScopedSoulConfigServiceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("soul.get/save 按 agentId 在独立目录读写")
    void soulGetSaveShouldBeAgentScoped() {
        AgentWorkspaceResolver resolver = new AgentWorkspaceResolver(Optional.empty());
        ReflectionTestUtils.setField(resolver, "workspaceRoot", tempDir.toString());
        SoulConfigService soulConfigService = new SoulConfigService(new ObjectMapper(), resolver);

        SoulSaveHandler saveHandler = new SoulSaveHandler(soulConfigService);
        SoulGetHandler getHandler = new SoulGetHandler(soulConfigService);

        RpcRequest saveReqA = RpcRequest.builder()
                .id("1")
                .method("soul.save")
                .payload(Map.of(
                        "agentId", "agent-a",
                        "config", Map.of("agentName", "Agent A", "responseStyle", "concise", "enabled", true)
                ))
                .build();
        RpcResponse saveRespA = saveHandler.handle("conn-1", saveReqA).block();
        assertNotNull(saveRespA);
        assertEquals("response", saveRespA.getType());

        RpcRequest saveReqB = RpcRequest.builder()
                .id("2")
                .method("soul.save")
                .payload(Map.of(
                        "agentId", "agent-b",
                        "config", Map.of("agentName", "Agent B", "responseStyle", "balanced", "enabled", true)
                ))
                .build();
        RpcResponse saveRespB = saveHandler.handle("conn-1", saveReqB).block();
        assertNotNull(saveRespB);
        assertEquals("response", saveRespB.getType());

        RpcRequest getReqA = RpcRequest.builder()
                .id("3")
                .method("soul.get")
                .payload(Map.of("agentId", "agent-a"))
                .build();
        RpcResponse getRespA = getHandler.handle("conn-1", getReqA).block();
        @SuppressWarnings("unchecked")
        Map<String, Object> payloadA = (Map<String, Object>) getRespA.getPayload();
        assertEquals("Agent A", payloadA.get("agentName"));

        RpcRequest getReqB = RpcRequest.builder()
                .id("4")
                .method("soul.get")
                .payload(Map.of("agentId", "agent-b"))
                .build();
        RpcResponse getRespB = getHandler.handle("conn-1", getReqB).block();
        @SuppressWarnings("unchecked")
        Map<String, Object> payloadB = (Map<String, Object>) getRespB.getPayload();
        assertEquals("Agent B", payloadB.get("agentName"));

        Path soulMdA = tempDir.resolve("agents").resolve("agent-a").resolve("SOUL.md");
        Path soulMdB = tempDir.resolve("agents").resolve("agent-b").resolve("SOUL.md");
        assertTrue(Files.exists(soulMdA));
        assertTrue(Files.exists(soulMdB));
    }

    @Test
    @DisplayName("心跳配置按 agent 独立加载")
    void heartbeatConfigShouldBeAgentScoped() {
        AgentWorkspaceResolver resolver = new AgentWorkspaceResolver(Optional.empty());
        ReflectionTestUtils.setField(resolver, "workspaceRoot", tempDir.toString());
        HeartbeatConfigService heartbeatConfigService = new HeartbeatConfigService(new ObjectMapper(), resolver);

        heartbeatConfigService.writeHeartbeatMd("agent-a", "HEARTBEAT A");
        heartbeatConfigService.writeHeartbeatMd("agent-b", "HEARTBEAT B");

        assertEquals("HEARTBEAT A", heartbeatConfigService.readHeartbeatMd("agent-a"));
        assertEquals("HEARTBEAT B", heartbeatConfigService.readHeartbeatMd("agent-b"));

        Map<String, Object> cfgA = Map.of("enabled", false, "intervalMinutes", 10);
        Map<String, Object> cfgB = Map.of("enabled", true, "intervalMinutes", 60);
        heartbeatConfigService.saveConfig("agent-a", cfgA);
        heartbeatConfigService.saveConfig("agent-b", cfgB);

        assertEquals(false, heartbeatConfigService.getConfig("agent-a").get("enabled"));
        assertEquals(10, ((Number) heartbeatConfigService.getConfig("agent-a").get("intervalMinutes")).intValue());
        assertEquals(true, heartbeatConfigService.getConfig("agent-b").get("enabled"));
        assertEquals(60, ((Number) heartbeatConfigService.getConfig("agent-b").get("intervalMinutes")).intValue());
    }
}
