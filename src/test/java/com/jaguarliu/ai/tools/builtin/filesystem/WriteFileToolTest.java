package com.jaguarliu.ai.tools.builtin.filesystem;

import com.jaguarliu.ai.runtime.RuntimeFailureCategories;
import com.jaguarliu.ai.tools.ToolExecutionContext;
import com.jaguarliu.ai.tools.ToolResult;
import com.jaguarliu.ai.tools.ToolsProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("WriteFileTool Tests")
class WriteFileToolTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("should include allowed workspace in out-of-workspace error")
    void shouldIncludeAllowedWorkspaceInOutOfWorkspaceError() {
        Path globalWorkspace = tempDir.resolve("workspace-root");
        Path agentWorkspace = globalWorkspace.resolve("workspace-agent-a").toAbsolutePath().normalize();

        ToolsProperties properties = new ToolsProperties();
        properties.setWorkspace(globalWorkspace.toString());
        WriteFileTool tool = new WriteFileTool(properties);

        ToolExecutionContext.set(ToolExecutionContext.builder()
                .agentId("agent-a")
                .sessionWorkspacePath(agentWorkspace)
                .build());

        try {
            ToolResult result = tool.execute(Map.of(
                    "path", tempDir.resolve("outside.txt").toString(),
                    "content", "hello"
            )).block();

            assertNotNull(result);
            assertFalse(result.isSuccess());
            assertEquals(RuntimeFailureCategories.HARD_ENVIRONMENT_BLOCK, result.getFailureCategory());
            assertTrue(result.getContent().contains("Allowed workspace:"), result.getContent());
            assertTrue(result.getContent().contains(agentWorkspace.toString()), result.getContent());
        } finally {
            ToolExecutionContext.clear();
        }
    }

    @Test
    @DisplayName("should include relative and resolved path in success result")
    void shouldIncludeResolvedPathInSuccessResult() throws Exception {
        Path globalWorkspace = tempDir.resolve("workspace-root");
        Path agentWorkspace = globalWorkspace.resolve("workspace-agent-a").toAbsolutePath().normalize();
        Files.createDirectories(agentWorkspace);

        ToolsProperties properties = new ToolsProperties();
        properties.setWorkspace(globalWorkspace.toString());
        WriteFileTool tool = new WriteFileTool(properties);

        ToolExecutionContext.set(ToolExecutionContext.builder()
                .agentId("agent-a")
                .sessionWorkspacePath(agentWorkspace)
                .build());

        try {
            ToolResult result = tool.execute(Map.of(
                    "path", "reports/output.txt",
                    "content", "hello"
            )).block();

            Path expectedPath = agentWorkspace.resolve("reports/output.txt").normalize();

            assertNotNull(result);
            assertTrue(result.isSuccess());
            assertTrue(result.getContent().contains("Relative path: reports/output.txt"), result.getContent());
            assertTrue(result.getContent().contains("Resolved path: " + expectedPath), result.getContent());
            assertEquals("hello", Files.readString(expectedPath));
        } finally {
            ToolExecutionContext.clear();
        }
    }
}
