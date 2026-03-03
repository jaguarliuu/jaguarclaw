package com.jaguarliu.ai.soul;

import com.jaguarliu.ai.agents.context.AgentWorkspaceResolver;
import com.jaguarliu.ai.heartbeat.HeartbeatConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Agent Scoped Soul/Heartbeat Config Tests")
class AgentScopedSoulConfigServiceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("soul.save/get 按 agentId 在独立目录读写 3 个 MD 文件")
    void soulGetSaveShouldBeAgentScoped() {
        AgentWorkspaceResolver resolver = new AgentWorkspaceResolver(Optional.empty());
        ReflectionTestUtils.setField(resolver, "workspaceRoot", tempDir.toString());
        SoulConfigService soulConfigService = new SoulConfigService(resolver);

        SoulSaveHandler saveHandler = new SoulSaveHandler(soulConfigService);
        SoulGetHandler getHandler = new SoulGetHandler(soulConfigService);

        // Save soul for agent-a
        RpcRequest saveReqA = RpcRequest.builder()
                .id("1").method("soul.save")
                .payload(Map.of("agentId", "agent-a", "file", "soul", "content", "# Soul A\n"))
                .build();
        RpcResponse saveRespA = saveHandler.handle("conn-1", saveReqA).block();
        assertNotNull(saveRespA);
        assertEquals("response", saveRespA.getType());

        // Save soul for agent-b
        RpcRequest saveReqB = RpcRequest.builder()
                .id("2").method("soul.save")
                .payload(Map.of("agentId", "agent-b", "file", "soul", "content", "# Soul B\n"))
                .build();
        RpcResponse saveRespB = saveHandler.handle("conn-1", saveReqB).block();
        assertNotNull(saveRespB);
        assertEquals("response", saveRespB.getType());

        // Get for agent-a
        RpcRequest getReqA = RpcRequest.builder()
                .id("3").method("soul.get")
                .payload(Map.of("agentId", "agent-a"))
                .build();
        RpcResponse getRespA = getHandler.handle("conn-1", getReqA).block();
        @SuppressWarnings("unchecked")
        Map<String, Object> payloadA = (Map<String, Object>) getRespA.getPayload();
        assertEquals("# Soul A\n", payloadA.get("soul"));

        // Get for agent-b
        RpcRequest getReqB = RpcRequest.builder()
                .id("4").method("soul.get")
                .payload(Map.of("agentId", "agent-b"))
                .build();
        RpcResponse getRespB = getHandler.handle("conn-1", getReqB).block();
        @SuppressWarnings("unchecked")
        Map<String, Object> payloadB = (Map<String, Object>) getRespB.getPayload();
        assertEquals("# Soul B\n", payloadB.get("soul"));

        // Check files exist on disk
        Path soulMdA = tempDir.resolve("workspace-agent-a").resolve("SOUL.md");
        Path soulMdB = tempDir.resolve("workspace-agent-b").resolve("SOUL.md");
        assertTrue(Files.exists(soulMdA));
        assertTrue(Files.exists(soulMdB));
    }

    @Test
    @DisplayName("ensureAgentDefaults 创建 3 个 MD 文件")
    void ensureAgentDefaultsCreatesMdFiles() {
        AgentWorkspaceResolver resolver = new AgentWorkspaceResolver(Optional.empty());
        ReflectionTestUtils.setField(resolver, "workspaceRoot", tempDir.toString());
        SoulConfigService soulConfigService = new SoulConfigService(resolver);

        soulConfigService.ensureAgentDefaults("test-agent", "Test Agent");

        Path workspaceDir = tempDir.resolve("workspace-test-agent");
        assertTrue(Files.exists(workspaceDir.resolve("SOUL.md")));
        assertTrue(Files.exists(workspaceDir.resolve("RULE.md")));
        assertTrue(Files.exists(workspaceDir.resolve("PROFILE.md")));
    }

    @Test
    @DisplayName("extractAgentName 从 SOUL.md 提取名字")
    void extractAgentNameReturnsParsedName(@TempDir Path tempDir) {
        AgentWorkspaceResolver resolver = new AgentWorkspaceResolver(Optional.empty());
        ReflectionTestUtils.setField(resolver, "workspaceRoot", tempDir.toString());
        SoulConfigService svc = new SoulConfigService(resolver);

        svc.writeSoulMd("agent-x", "# Soul\n\nYour name is Alice.\n\n## Personality\nHelpful.\n");

        assertEquals("Alice", svc.extractAgentName("agent-x"));
    }

    @Test
    @DisplayName("extractAgentName 无名字时返回 null")
    void extractAgentNameReturnsNullWhenMissing(@TempDir Path tempDir) {
        AgentWorkspaceResolver resolver = new AgentWorkspaceResolver(Optional.empty());
        ReflectionTestUtils.setField(resolver, "workspaceRoot", tempDir.toString());
        SoulConfigService svc = new SoulConfigService(resolver);

        svc.writeSoulMd("agent-y", "# Soul\n\n## Personality\nHelpful.\n");

        assertNull(svc.extractAgentName("agent-y"));
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
