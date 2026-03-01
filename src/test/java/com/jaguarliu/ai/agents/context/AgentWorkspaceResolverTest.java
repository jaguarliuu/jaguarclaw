package com.jaguarliu.ai.agents.context;

import com.jaguarliu.ai.agents.entity.AgentProfileEntity;
import com.jaguarliu.ai.agents.service.AgentProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("AgentWorkspaceResolver Tests")
class AgentWorkspaceResolverTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("默认 workspace 在 tools.workspace/agents/<agentId> 下")
    void resolveAgentWorkspaceShouldStayUnderAgentsRoot() {
        AgentWorkspaceResolver resolver = new AgentWorkspaceResolver(Optional.empty());
        ReflectionTestUtils.setField(resolver, "workspaceRoot", tempDir.toString());

        Path resolved = resolver.resolveAgentWorkspace("agent-a");

        assertEquals(tempDir.resolve("agents").resolve("agent-a").toAbsolutePath().normalize(), resolved);
    }

    @Test
    @DisplayName("agentId 非法路径片段会被拒绝")
    void resolveAgentWorkspaceShouldRejectTraversalAgentId() {
        AgentWorkspaceResolver resolver = new AgentWorkspaceResolver(Optional.empty());
        ReflectionTestUtils.setField(resolver, "workspaceRoot", tempDir.toString());

        assertThrows(IllegalArgumentException.class, () -> resolver.resolveAgentWorkspace("../evil"));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolveAgentWorkspace("a/b"));
    }

    @Test
    @DisplayName("resolveAgentFile 阻止目录穿越和绝对路径")
    void resolveAgentFileShouldRejectUnsafeFileName() {
        AgentWorkspaceResolver resolver = new AgentWorkspaceResolver(Optional.empty());
        ReflectionTestUtils.setField(resolver, "workspaceRoot", tempDir.toString());

        assertThrows(IllegalArgumentException.class, () -> resolver.resolveAgentFile("agent-a", "../SOUL.md"));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolveAgentFile("agent-a", "/etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolveAgentFile("agent-a", "nested/config.md"));
    }

    @Test
    @DisplayName("profile workspace 路径超出根目录时拒绝")
    void resolveProfileWorkspaceShouldRejectOutsideWorkspaceRoot() {
        AgentProfileService profileService = mock(AgentProfileService.class);
        when(profileService.get("agent-a"))
                .thenReturn(Optional.of(AgentProfileEntity.builder().id("agent-a").workspacePath("../outside").build()));

        AgentWorkspaceResolver resolver = new AgentWorkspaceResolver(Optional.of(profileService));
        ReflectionTestUtils.setField(resolver, "workspaceRoot", tempDir.toString());

        assertThrows(IllegalArgumentException.class, () -> resolver.resolveAgentWorkspace("agent-a"));
    }

    @Test
    @DisplayName("profile workspace 在根目录内时允许")
    void resolveProfileWorkspaceShouldAllowPathInsideWorkspaceRoot() {
        AgentProfileService profileService = mock(AgentProfileService.class);
        when(profileService.get("agent-a"))
                .thenReturn(Optional.of(AgentProfileEntity.builder()
                        .id("agent-a")
                        .workspacePath("agents/agent-a-custom")
                        .build()));

        AgentWorkspaceResolver resolver = new AgentWorkspaceResolver(Optional.of(profileService));
        ReflectionTestUtils.setField(resolver, "workspaceRoot", tempDir.toString());

        Path resolved = resolver.resolveAgentWorkspace("agent-a");

        assertEquals(tempDir.resolve("agents").resolve("agent-a-custom").toAbsolutePath().normalize(), resolved);
    }

    @Test
    @DisplayName("兼容历史相对路径 workspace/agents/*，避免二次拼接")
    void resolveProfileWorkspaceShouldSupportLegacyRelativeWorkspacePath() {
        AgentProfileService profileService = mock(AgentProfileService.class);
        Path cwd = Path.of(".").toAbsolutePath().normalize();
        Path workspaceRoot = cwd.resolve("workspace").toAbsolutePath().normalize();
        String legacyPath = "workspace/agents/agent-a";
        when(profileService.get("agent-a"))
                .thenReturn(Optional.of(AgentProfileEntity.builder()
                        .id("agent-a")
                        .workspacePath(legacyPath)
                        .build()));

        AgentWorkspaceResolver resolver = new AgentWorkspaceResolver(Optional.of(profileService));
        ReflectionTestUtils.setField(resolver, "workspaceRoot", workspaceRoot.toString());

        Path resolved = resolver.resolveAgentWorkspace("agent-a");

        assertEquals(cwd.resolve(legacyPath).toAbsolutePath().normalize(), resolved);
    }
}
