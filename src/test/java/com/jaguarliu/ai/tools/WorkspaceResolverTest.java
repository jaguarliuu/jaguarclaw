package com.jaguarliu.ai.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("WorkspaceResolver Tests")
class WorkspaceResolverTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("resolveSessionWorkspace 优先使用上下文中的 sessionWorkspacePath")
    void shouldPreferSessionWorkspacePathFromContext() {
        ToolsProperties props = new ToolsProperties();
        props.setWorkspace(tempDir.resolve("global-workspace").toString());

        Path customWorkspace = tempDir.resolve("agent-custom-home").toAbsolutePath().normalize();
        ToolExecutionContext.set(ToolExecutionContext.builder()
                .agentId("agent-random-id")
                .sessionWorkspacePath(customWorkspace)
                .build());

        try {
            Path resolved = WorkspaceResolver.resolveSessionWorkspace(props);
            assertEquals(customWorkspace, resolved);
        } finally {
            ToolExecutionContext.clear();
        }
    }

    @Test
    @DisplayName("未提供 sessionWorkspacePath 时应抛错，避免旧路径回退")
    void shouldFailWhenContextMissingSessionWorkspacePath() {
        ToolsProperties props = new ToolsProperties();
        Path global = tempDir.resolve("global-workspace").toAbsolutePath().normalize();
        props.setWorkspace(global.toString());

        ToolExecutionContext.set(ToolExecutionContext.builder()
                .agentId("agent-a")
                .build());

        try {
            assertThrows(IllegalStateException.class, () -> WorkspaceResolver.resolveSessionWorkspace(props));
        } finally {
            ToolExecutionContext.clear();
        }
    }

    @Test
    @DisplayName("无上下文时返回全局 workspace")
    void shouldReturnGlobalWorkspaceWithoutContext() {
        ToolsProperties props = new ToolsProperties();
        Path global = tempDir.resolve("global-workspace").toAbsolutePath().normalize();
        props.setWorkspace(global.toString());

        Path resolved = WorkspaceResolver.resolveSessionWorkspace(props);

        assertEquals(global, resolved);
    }
}
