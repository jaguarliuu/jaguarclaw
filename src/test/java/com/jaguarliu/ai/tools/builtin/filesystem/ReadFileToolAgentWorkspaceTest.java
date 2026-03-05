package com.jaguarliu.ai.tools.builtin.filesystem;

import com.jaguarliu.ai.tools.ToolExecutionContext;
import com.jaguarliu.ai.tools.ToolResult;
import com.jaguarliu.ai.tools.ToolsProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ReadFileTool agent workspace tests")
class ReadFileToolAgentWorkspaceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("应优先从 sessionWorkspacePath 解析相对路径")
    void shouldReadRelativeFileFromSessionWorkspacePath() throws Exception {
        Path globalWorkspace = tempDir.resolve("workspace-root");
        Path customAgentWorkspace = globalWorkspace.resolve("workspace-named-by-profile");
        Files.createDirectories(customAgentWorkspace.resolve("docs"));
        Files.writeString(customAgentWorkspace.resolve("docs").resolve("note.txt"), "hello-from-custom-workspace");

        ToolsProperties properties = new ToolsProperties();
        properties.setWorkspace(globalWorkspace.toString());
        properties.setMaxFileSize(1024 * 1024);

        ReadFileTool tool = new ReadFileTool(properties);
        ToolExecutionContext.set(ToolExecutionContext.builder()
                .agentId("66be7f68-1e87-4f2d-bcf7-61f8e0de8f2a")
                .sessionWorkspacePath(customAgentWorkspace)
                .build());

        try {
            ToolResult result = tool.execute(Map.of("path", "docs/note.txt")).block();
            assertTrue(result != null && result.isSuccess(), result == null ? "null result" : result.getContent());
            assertTrue(result.getContent().contains("hello-from-custom-workspace"));
        } finally {
            ToolExecutionContext.clear();
        }
    }
}
