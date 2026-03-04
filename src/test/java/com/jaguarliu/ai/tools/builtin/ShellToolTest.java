package com.jaguarliu.ai.tools.builtin;

import com.jaguarliu.ai.tools.ToolResult;
import com.jaguarliu.ai.tools.ToolsProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ShellTool Tests")
class ShellToolTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("command and script_content are mutually exclusive")
    void commandAndScriptContentMutuallyExclusive() {
        ShellTool tool = new ShellTool(propertiesWithWorkspace());

        ToolResult result = tool.execute(Map.of(
                "command", "echo hello",
                "script_content", "echo world"
        )).block();

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getContent().contains("mutually exclusive"));
    }

    @Test
    @DisplayName("should execute script_content successfully")
    void executeScriptContentSuccessfully() {
        ShellTool tool = new ShellTool(propertiesWithWorkspace());
        String script = isWindows() ? "Write-Output 'hello-script'" : "echo hello-script";

        ToolResult result = tool.execute(Map.of("script_content", script)).block();

        assertNotNull(result);
        assertTrue(result.isSuccess(), () -> "Unexpected error: " + result.getContent());
        assertTrue(result.getContent().toLowerCase().contains("hello-script"));
    }

    private ToolsProperties propertiesWithWorkspace() {
        ToolsProperties properties = new ToolsProperties();
        properties.setWorkspace(tempDir.toString());
        return properties;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}

