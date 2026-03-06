package com.jaguarliu.ai.tools.builtin;

import com.jaguarliu.ai.tools.ToolResult;
import com.jaguarliu.ai.tools.ToolExecutionContext;
import com.jaguarliu.ai.tools.ToolsProperties;
import com.jaguarliu.ai.tools.builtin.shell.ShellTool;
import com.jaguarliu.ai.tools.runtime.BundledRuntimeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
        ShellTool tool = newTool(propertiesWithWorkspace());

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
        ShellTool tool = newTool(propertiesWithWorkspace());
        String script = isWindows() ? "Write-Output 'hello-script'" : "echo hello-script";

        ToolResult result = tool.execute(Map.of("script_content", script)).block();

        assertNotNull(result);
        assertTrue(result.isSuccess(), () -> "Unexpected error: " + result.getContent());
        assertTrue(result.getContent().toLowerCase().contains("hello-script"));
    }

    @Test
    @DisplayName("should inject bundled runtime bin path into PATH")
    void shouldInjectBundledRuntimeBinPathIntoPath() throws Exception {
        Path runtimeHome = tempDir.resolve("runtime");
        String relativeBin = isWindows() ? "node" : "bin";
        Path binPath = runtimeHome.resolve(relativeBin);
        Files.createDirectories(binPath);

        ToolsProperties properties = propertiesWithWorkspace();
        properties.getRuntime().setEnabled(true);
        properties.getRuntime().setHome(runtimeHome.toString());
        properties.getRuntime().setBinPaths(List.of(relativeBin));

        ShellTool tool = newTool(properties);
        String script = isWindows() ? "Write-Output $env:Path" : "echo $PATH";

        ToolResult result = tool.execute(Map.of("script_content", script)).block();

        assertNotNull(result);
        assertTrue(result.isSuccess(), () -> "Unexpected error: " + result.getContent());
        assertTrue(result.getContent().contains(binPath.toAbsolutePath().normalize().toString()));
    }

    @Test
    @DisplayName("should resolve python3 to bundled python via shim")
    void shouldResolvePython3ViaBundledShim() throws Exception {
        Path runtimeHome = tempDir.resolve("runtime");
        Path binPath = runtimeHome.resolve("bin");
        Files.createDirectories(binPath);

        if (isWindows()) {
            Files.writeString(binPath.resolve("python.cmd"), "@echo off\r\necho bundled-python\r\n");
        } else {
            Path python = binPath.resolve("python");
            Files.writeString(python, "#!/usr/bin/env sh\necho bundled-python\n");
            python.toFile().setExecutable(true);
        }

        ToolsProperties properties = propertiesWithWorkspace();
        properties.getRuntime().setEnabled(true);
        properties.getRuntime().setHome(runtimeHome.toString());
        properties.getRuntime().setBinPaths(List.of("bin"));

        ShellTool tool = newTool(properties);
        ToolResult result = tool.execute(Map.of("command", "python3 -c \"print('ignored')\"")).block();

        assertNotNull(result);
        assertTrue(result.isSuccess(), () -> "Unexpected error: " + result.getContent());
        assertTrue(result.getContent().toLowerCase().contains("bundled-python"), result::getContent);
    }

    @Test
    @DisplayName("should inject agent-browser session/profile and bundled chromium env")
    void shouldInjectAgentBrowserSessionProfileAndChromiumEnv() throws Exception {
        Path runtimeHome = tempDir.resolve("runtime");
        Path chromiumHome = runtimeHome.resolve("browser/chromium");
        Files.createDirectories(chromiumHome);
        Path chromiumExe = chromiumHome.resolve(isWindows() ? "chrome.exe" : "chrome");
        Files.writeString(chromiumExe, "echo chrome");

        ToolsProperties properties = propertiesWithWorkspace();
        properties.getRuntime().setEnabled(true);
        properties.getRuntime().setHome(runtimeHome.toString());
        properties.getRuntime().setChromiumExecutablePath("browser/chromium/" + (isWindows() ? "chrome.exe" : "chrome"));
        properties.getRuntime().setChromiumHome("browser/chromium");

        ShellTool tool = newTool(properties);

        ToolExecutionContext.set(ToolExecutionContext.builder()
                .agentId("agent-a")
                .sessionId("sess-001")
                .runId("run-001")
                .sessionWorkspacePath(tempDir)
                .build());
        try {
            String command = isWindows()
                    ? "echo %AGENT_BROWSER_SESSION% && echo %AGENT_BROWSER_PROFILE% && echo %AGENT_BROWSER_CHROMIUM_PATH%"
                    : "echo $AGENT_BROWSER_SESSION && echo $AGENT_BROWSER_PROFILE && echo $AGENT_BROWSER_CHROMIUM_PATH";
            ToolResult result = tool.execute(Map.of("command", command)).block();

            assertNotNull(result);
            assertTrue(result.isSuccess(), () -> "Unexpected error: " + result.getContent());
            assertTrue(result.getContent().contains("sess-001"), result::getContent);
            assertTrue(result.getContent().contains("browser-profiles"), result::getContent);
            assertTrue(result.getContent().contains("agent-a"), result::getContent);
            assertTrue(result.getContent().contains(chromiumExe.toAbsolutePath().normalize().toString()), result::getContent);
        } finally {
            ToolExecutionContext.clear();
        }
    }

    private ToolsProperties propertiesWithWorkspace() {
        ToolsProperties properties = new ToolsProperties();
        properties.setWorkspace(tempDir.toString());
        return properties;
    }

    private ShellTool newTool(ToolsProperties properties) {
        return new ShellTool(properties, new BundledRuntimeService(properties));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
