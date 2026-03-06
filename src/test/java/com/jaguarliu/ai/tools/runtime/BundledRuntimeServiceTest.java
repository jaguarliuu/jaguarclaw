package com.jaguarliu.ai.tools.runtime;

import com.jaguarliu.ai.tools.ToolsProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BundledRuntimeService Tests")
class BundledRuntimeServiceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("runtime disabled by default")
    void runtimeDisabledByDefault() {
        ToolsProperties properties = new ToolsProperties();
        BundledRuntimeService service = new BundledRuntimeService(properties);

        assertFalse(service.isEnabled());
        assertTrue(service.resolveBinPaths().isEmpty());
    }

    @Test
    @DisplayName("resolve configured runtime bin paths")
    void resolveConfiguredBinPaths() {
        Path runtimeHome = tempDir.resolve("runtime");

        ToolsProperties properties = new ToolsProperties();
        properties.getRuntime().setEnabled(true);
        properties.getRuntime().setHome(runtimeHome.toString());
        properties.getRuntime().setBinPaths(List.of("python", "node", "python/Scripts"));

        BundledRuntimeService service = new BundledRuntimeService(properties);
        List<Path> bins = service.resolveBinPaths();

        assertEquals(3, bins.size());
        assertTrue(bins.contains(runtimeHome.resolve("python").toAbsolutePath().normalize()));
        assertTrue(bins.contains(runtimeHome.resolve("node").toAbsolutePath().normalize()));
        assertTrue(bins.contains(runtimeHome.resolve("python/Scripts").toAbsolutePath().normalize()));
    }

    @Test
    @DisplayName("applyToEnvironment should prefix PATH")
    void applyToEnvironmentPrefixesPath() {
        Path runtimeHome = tempDir.resolve("runtime");

        ToolsProperties properties = new ToolsProperties();
        properties.getRuntime().setEnabled(true);
        properties.getRuntime().setHome(runtimeHome.toString());
        properties.getRuntime().setBinPaths(List.of("node"));

        BundledRuntimeService service = new BundledRuntimeService(properties);

        Map<String, String> env = new java.util.HashMap<>();
        env.put("PATH", "orig-path");

        service.applyToEnvironment(env);

        String path = env.get("PATH");
        assertNotNull(path);
        String[] entries = path.split(java.io.File.pathSeparator);
        assertTrue(entries.length >= 2);
        assertEquals(runtimeHome.resolve("node").toAbsolutePath().normalize().toString(), entries[0]);
        assertTrue(path.endsWith("orig-path"));
    }

    @Test
    @DisplayName("should prepend shim dir when python exists but python3 is missing")
    void shouldPrependShimDirForPython3Alias() throws Exception {
        Path runtimeHome = tempDir.resolve("runtime");
        Path bin = runtimeHome.resolve("bin");
        Files.createDirectories(bin);

        if (isWindows()) {
            Files.writeString(bin.resolve("python.cmd"), "@echo off\r\necho bundled-python\r\n");
        } else {
            Path python = bin.resolve("python");
            Files.writeString(python, "#!/usr/bin/env sh\necho bundled-python\n");
            python.toFile().setExecutable(true);
        }

        ToolsProperties properties = new ToolsProperties();
        properties.getRuntime().setEnabled(true);
        properties.getRuntime().setHome(runtimeHome.toString());
        properties.getRuntime().setBinPaths(List.of("bin"));

        BundledRuntimeService service = new BundledRuntimeService(properties);
        Map<String, String> env = new java.util.HashMap<>();
        env.put("PATH", "orig-path");

        service.applyToEnvironment(env);

        String[] entries = env.get("PATH").split(java.io.File.pathSeparator);
        Path first = Path.of(entries[0]).toAbsolutePath().normalize();
        assertEquals(runtimeHome.resolve("shims").toAbsolutePath().normalize(), first);

        Path shimPath = isWindows() ? first.resolve("python3.cmd") : first.resolve("python3");
        assertTrue(Files.exists(shimPath), () -> "Missing shim: " + shimPath);
    }

    @Test
    @DisplayName("should detect bundled binary")
    void detectBundledBinary() throws Exception {
        Path runtimeHome = tempDir.resolve("runtime");
        Path nodeBin = runtimeHome.resolve("node");
        Files.createDirectories(nodeBin);

        Path binary = nodeBin.resolve(isWindows() ? "python.exe" : "python");
        Files.writeString(binary, "echo test");
        if (!isWindows()) {
            binary.toFile().setExecutable(true);
        }

        ToolsProperties properties = new ToolsProperties();
        properties.getRuntime().setEnabled(true);
        properties.getRuntime().setHome(runtimeHome.toString());
        properties.getRuntime().setBinPaths(List.of("node"));

        BundledRuntimeService service = new BundledRuntimeService(properties);

        assertTrue(service.hasBundledBinary("python"));
        assertFalse(service.hasBundledBinary("nonexistent_bin_123"));
    }

    @Test
    @DisplayName("explicit agent-browser path should win over bin paths")
    void explicitAgentBrowserPathShouldWin() throws Exception {
        Path runtimeHome = tempDir.resolve("runtime");
        Path explicitDir = tempDir.resolve("external");
        Files.createDirectories(explicitDir);

        Path explicitBinary = explicitDir.resolve(isWindows() ? "agent-browser.cmd" : "agent-browser");
        Files.writeString(explicitBinary, "echo explicit");

        Path bundledDir = runtimeHome.resolve("bin");
        Files.createDirectories(bundledDir);
        Path bundledBinary = bundledDir.resolve(isWindows() ? "agent-browser.cmd" : "agent-browser");
        Files.writeString(bundledBinary, "echo bundled");

        ToolsProperties properties = new ToolsProperties();
        properties.getRuntime().setEnabled(true);
        properties.getRuntime().setHome(runtimeHome.toString());
        properties.getRuntime().setBinPaths(List.of("bin"));
        properties.getRuntime().setAgentBrowserExecutablePath(explicitBinary.toString());

        BundledRuntimeService service = new BundledRuntimeService(properties);
        Path resolved = service.resolveBundledBinary("agent-browser").orElseThrow();
        assertEquals(explicitBinary.toAbsolutePath().normalize(), resolved);
    }

    @Test
    @DisplayName("should resolve bundled chromium and inject related env")
    void shouldResolveBundledChromiumAndInjectEnv() throws Exception {
        Path runtimeHome = tempDir.resolve("runtime");
        Path chromiumHome = runtimeHome.resolve("browser/chromium");
        Files.createDirectories(chromiumHome);
        Path chromium = chromiumHome.resolve(isWindows() ? "chrome.exe" : "chrome");
        Files.writeString(chromium, "echo chromium");

        ToolsProperties properties = new ToolsProperties();
        properties.getRuntime().setEnabled(true);
        properties.getRuntime().setHome(runtimeHome.toString());
        properties.getRuntime().setChromiumExecutablePath("browser/chromium/" + (isWindows() ? "chrome.exe" : "chrome"));
        properties.getRuntime().setChromiumHome("browser/chromium");

        BundledRuntimeService service = new BundledRuntimeService(properties);
        assertEquals(chromium.toAbsolutePath().normalize(), service.resolveBundledChromium().orElseThrow());
        assertEquals(chromiumHome.toAbsolutePath().normalize(), service.resolveBundledChromiumHome().orElseThrow());

        Map<String, String> env = new java.util.HashMap<>();
        service.applyToEnvironment(env);
        assertEquals(chromium.toAbsolutePath().normalize().toString(), env.get("AGENT_BROWSER_CHROMIUM_PATH"));
        assertEquals(chromiumHome.toAbsolutePath().normalize().toString(), env.get("AGENT_BROWSER_KERNEL_HOME"));
        assertEquals("kernel", env.get("AGENT_BROWSER_PROVIDER"));
        assertEquals("1", env.get("AGENT_BROWSER_SKIP_INSTALL"));
    }

    @Test
    @DisplayName("empty bin paths should use os defaults")
    void emptyBinPathsUseOsDefaults() {
        Path runtimeHome = tempDir.resolve("runtime");

        ToolsProperties properties = new ToolsProperties();
        properties.getRuntime().setEnabled(true);
        properties.getRuntime().setHome(runtimeHome.toString());
        properties.getRuntime().setBinPaths(new ArrayList<>());

        BundledRuntimeService service = new BundledRuntimeService(properties);
        List<Path> bins = service.resolveBinPaths();

        assertFalse(bins.isEmpty());
    }

    @Test
    @DisplayName("blank bin paths should fallback to os defaults")
    void blankBinPathsUseOsDefaults() {
        Path runtimeHome = tempDir.resolve("runtime");

        ToolsProperties properties = new ToolsProperties();
        properties.getRuntime().setEnabled(true);
        properties.getRuntime().setHome(runtimeHome.toString());
        properties.getRuntime().setBinPaths(List.of("", "   "));

        BundledRuntimeService service = new BundledRuntimeService(properties);
        List<Path> bins = service.resolveBinPaths();

        assertFalse(bins.isEmpty());
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
