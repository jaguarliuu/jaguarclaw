package com.jaguarliu.ai.agents.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.agents.AgentRegistry;
import com.jaguarliu.ai.agents.entity.AgentProfileEntity;
import com.jaguarliu.ai.agents.repository.AgentProfileRepository;
import com.jaguarliu.ai.heartbeat.HeartbeatConfigService;
import com.jaguarliu.ai.mcp.persistence.McpServerRepository;
import com.jaguarliu.ai.memory.index.MemoryChunkRepository;
import com.jaguarliu.ai.soul.SoulConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AgentProfileService workspace path tests")
class AgentProfileServiceWorkspacePathTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("default agent workspace should be under tools.workspace")
    void ensureDefaultMainAgentExistsShouldUseConfiguredWorkspaceRoot() {
        AgentProfileRepository repository = mock(AgentProfileRepository.class);
        when(repository.count()).thenReturn(0L);
        when(repository.save(any(AgentProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AgentProfileService service = newService(repository);
        Path workspaceRoot = tempDir.resolve("workspace-root").toAbsolutePath().normalize();
        ReflectionTestUtils.setField(service, "workspaceRoot", workspaceRoot.toString());

        service.ensureDefaultMainAgentExists();

        ArgumentCaptor<AgentProfileEntity> captor = ArgumentCaptor.forClass(AgentProfileEntity.class);
        verify(repository).save(captor.capture());
        AgentProfileEntity saved = captor.getValue();

        Path savedPath = Path.of(saved.getWorkspacePath()).toAbsolutePath().normalize();
        assertEquals(workspaceRoot.resolve("workspace-main"), savedPath);
        assertTrue(savedPath.startsWith(workspaceRoot));
    }

    @Test
    @DisplayName("legacy workspace outside tools.workspace should be migrated")
    void migrateLegacyWorkspacePathsShouldMoveOutsidePathToWorkspaceRoot() throws Exception {
        AgentProfileRepository repository = mock(AgentProfileRepository.class);
        Path outsideRoot = tempDir.resolve("legacy-install").resolve("workspace-main");
        Files.createDirectories(outsideRoot.resolve("memory"));
        Files.writeString(outsideRoot.resolve("memory").resolve("MEMORY.md"), "legacy-memory");

        AgentProfileEntity legacy = AgentProfileEntity.builder()
                .id("main")
                .name("main")
                .displayName("Main")
                .workspacePath(outsideRoot.toString())
                .enabled(true)
                .isDefault(true)
                .build();

        when(repository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(legacy));
        when(repository.save(any(AgentProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AgentProfileService service = newService(repository);
        Path workspaceRoot = tempDir.resolve("appdata").resolve("workspace").toAbsolutePath().normalize();
        ReflectionTestUtils.setField(service, "workspaceRoot", workspaceRoot.toString());

        service.migrateLegacyWorkspacePaths();

        Path expected = workspaceRoot.resolve("workspace-main");
        assertEquals(expected.toString(), legacy.getWorkspacePath());
        assertTrue(Files.exists(expected.resolve("memory").resolve("MEMORY.md")));
    }

    private AgentProfileService newService(AgentProfileRepository repository) {
        AgentRegistry agentRegistry = mock(AgentRegistry.class);
        MemoryChunkRepository memoryChunkRepository = mock(MemoryChunkRepository.class);
        McpServerRepository mcpServerRepository = mock(McpServerRepository.class);
        ObjectProvider<SoulConfigService> soulProvider = mock(ObjectProvider.class);
        ObjectProvider<HeartbeatConfigService> heartbeatProvider = mock(ObjectProvider.class);

        return new AgentProfileService(
                repository,
                agentRegistry,
                memoryChunkRepository,
                mcpServerRepository,
                new ObjectMapper(),
                soulProvider,
                heartbeatProvider
        );
    }
}
