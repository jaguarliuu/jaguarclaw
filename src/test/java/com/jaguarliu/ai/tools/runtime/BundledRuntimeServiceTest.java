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
