package com.jaguarliu.ai.tools.runtime;

import com.jaguarliu.ai.tools.ToolsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 内置 runtime 路径解析与环境注入服务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BundledRuntimeService {

    private final ToolsProperties toolsProperties;

    private static final boolean IS_WINDOWS = System.getProperty("os.name", "")
            .toLowerCase().contains("win");

    private static final String AGENT_BROWSER_BIN_ENV = "AGENT_BROWSER_EXECUTABLE_PATH";
    private static final String AGENT_BROWSER_CHROMIUM_ENV = "AGENT_BROWSER_CHROMIUM_PATH";
    private static final String AGENT_BROWSER_KERNEL_HOME_ENV = "AGENT_BROWSER_KERNEL_HOME";
    private static final String AGENT_BROWSER_HOME_ENV = "AGENT_BROWSER_HOME";

    public boolean isEnabled() {
        return toolsProperties.getRuntime() != null && toolsProperties.getRuntime().isEnabled();
    }

    public Optional<Path> resolveRuntimeHome() {
        if (!isEnabled()) {
            return Optional.empty();
        }
        String home = toolsProperties.getRuntime().getHome();
        if (home == null || home.isBlank()) {
            home = System.getenv("JAGUAR_RUNTIME_HOME");
        }
        if (home == null || home.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Path.of(home).toAbsolutePath().normalize());
    }

    public List<Path> resolveBinPaths() {
        Optional<Path> runtimeHomeOpt = resolveRuntimeHome();
        if (runtimeHomeOpt.isEmpty()) {
            return List.of();
        }
        Path runtimeHome = runtimeHomeOpt.get();

        List<String> configured = toolsProperties.getRuntime().getBinPaths();
        List<String> normalizedConfigured = configured == null
                ? List.of()
                : configured.stream()
                .filter(entry -> entry != null && !entry.isBlank())
                .toList();

        List<String> entries = normalizedConfigured.isEmpty()
                ? defaultBinEntries()
                : normalizedConfigured;

        Set<Path> paths = new LinkedHashSet<>();
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            Path candidate = Path.of(entry);
            if (!candidate.isAbsolute()) {
                candidate = runtimeHome.resolve(entry);
            }
            paths.add(candidate.toAbsolutePath().normalize());
        }
        return new ArrayList<>(paths);
    }

    public void applyToEnvironment(Map<String, String> env) {
        if (env == null || !isEnabled()) {
            return;
        }
        List<Path> bins = resolveBinPaths();
        if (bins.isEmpty()) {
            return;
        }
        List<Path> effectiveBins = new ArrayList<>();
        resolveCompatShimDir().ifPresent(effectiveBins::add);
        effectiveBins.addAll(bins);

        String pathKey = resolvePathKey(env);
        String existing = env.getOrDefault(pathKey, "");

        String prefix = effectiveBins.stream()
                .map(Path::toString)
                .filter(s -> !s.isBlank())
                .distinct()
                .reduce((a, b) -> a + java.io.File.pathSeparator + b)
                .orElse("");
        if (prefix.isBlank()) {
            return;
        }

        String merged = existing == null || existing.isBlank()
                ? prefix
                : prefix + java.io.File.pathSeparator + existing;

        env.put(pathKey, merged);
        env.put("PATH", merged);
        if (IS_WINDOWS) {
            env.put("Path", merged);
        }
        resolveRuntimeHome().ifPresent(path -> env.put("JAGUAR_RUNTIME_HOME", path.toString()));
        resolveAgentBrowserHome().ifPresent(path -> env.put(AGENT_BROWSER_HOME_ENV, path.toString()));
        resolveBundledBinary("agent-browser").ifPresent(path ->
                env.put(AGENT_BROWSER_BIN_ENV, path.toString()));
        resolveBundledChromium().ifPresent(path ->
                env.put(AGENT_BROWSER_CHROMIUM_ENV, path.toString()));
        resolveBundledChromiumHome().ifPresent(path ->
                env.put(AGENT_BROWSER_KERNEL_HOME_ENV, path.toString()));
        env.putIfAbsent("AGENT_BROWSER_PROVIDER", "kernel");
        env.put("AGENT_BROWSER_SKIP_INSTALL", "1");
    }

    public boolean hasBundledBinary(String binName) {
        return resolveBundledBinary(binName).isPresent();
    }

    public Optional<Path> resolveBundledBinary(String binName) {
        if (!isEnabled() || binName == null || binName.isBlank()) {
            return Optional.empty();
        }

        Optional<Path> explicit = resolveExplicitBinaryPath(binName);
        if (explicit.isPresent()) {
            return explicit;
        }

        for (Path binPath : resolveBinPaths()) {
            for (String candidateName : resolveBinaryCandidates(binName)) {
                Path candidate = binPath.resolve(candidateName).normalize();
                if (Files.isRegularFile(candidate)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Path> resolveBundledChromium() {
        if (!isEnabled()) {
            return Optional.empty();
        }
        Optional<Path> explicitPath = resolveConfiguredPath(
                toolsProperties.getRuntime().getChromiumExecutablePath(),
                AGENT_BROWSER_CHROMIUM_ENV
        ).filter(Files::isRegularFile);
        if (explicitPath.isPresent()) {
            return explicitPath;
        }

        return resolveRuntimeHome()
                .map(this::resolveChromiumFromRuntimeHome)
                .orElse(Optional.empty());
    }

    public Optional<Path> resolveBundledChromiumHome() {
        if (!isEnabled()) {
            return Optional.empty();
        }
        Optional<Path> explicit = resolveConfiguredPath(
                toolsProperties.getRuntime().getChromiumHome(),
                AGENT_BROWSER_KERNEL_HOME_ENV
        ).filter(Files::isDirectory);
        if (explicit.isPresent()) {
            return explicit;
        }

        return resolveRuntimeHome().flatMap(runtimeHome -> {
            Path configured = runtimeHome.resolve("browser/chromium").toAbsolutePath().normalize();
            if (Files.isDirectory(configured)) {
                return Optional.of(configured);
            }
            return resolveBundledChromium().map(chromium -> {
                Path parent = chromium.getParent();
                return parent != null ? parent.toAbsolutePath().normalize() : runtimeHome;
            });
        });
    }

    public Optional<Path> resolveAgentBrowserHome() {
        if (!isEnabled()) {
            return Optional.empty();
        }

        Optional<Path> runtimeHome = resolveRuntimeHome();
        Optional<Path> binary = resolveBundledBinary("agent-browser");
        if (binary.isEmpty()) {
            return runtimeHome;
        }

        Path parent = binary.get().getParent();
        if (parent == null) {
            return runtimeHome;
        }
        if (parent.getFileName() != null && "bin".equalsIgnoreCase(parent.getFileName().toString()) && parent.getParent() != null) {
            return Optional.of(parent.getParent().toAbsolutePath().normalize());
        }
        return Optional.of(parent.toAbsolutePath().normalize());
    }

    private List<String> defaultBinEntries() {
        if (IS_WINDOWS) {
            return List.of("bin", "node", "python", "python/Scripts");
        }
        return List.of("bin");
    }

    private String resolvePathKey(Map<String, String> env) {
        for (String key : env.keySet()) {
            if (key != null && key.equalsIgnoreCase("PATH")) {
                return key;
            }
        }
        return IS_WINDOWS ? "Path" : "PATH";
    }

    private List<String> resolveBinaryCandidates(String binName) {
        if (binName.contains(".")) {
            return List.of(binName);
        }
        if (IS_WINDOWS) {
            return List.of(binName + ".exe", binName + ".cmd", binName + ".bat", binName);
        }
        // 非 Windows 场景也兼容检查 Windows 后缀，便于跨平台构建/校验 win runtime
        return List.of(binName, binName + ".exe", binName + ".cmd", binName + ".bat");
    }

    private Optional<Path> resolveExplicitBinaryPath(String binName) {
        if (binName == null || binName.isBlank()) {
            return Optional.empty();
        }
        String normalized = binName.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("agent-browser")) {
            return resolveConfiguredPath(
                    toolsProperties.getRuntime().getAgentBrowserExecutablePath(),
                    AGENT_BROWSER_BIN_ENV
            ).filter(Files::isRegularFile);
        }
        return Optional.empty();
    }

    private Optional<Path> resolveConfiguredPath(String configuredValue, String envName) {
        String envValue = System.getenv(envName);
        String value = configuredValue;
        if (value == null || value.isBlank()) {
            value = envValue;
        }
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        Path candidate = Path.of(value);
        if (!candidate.isAbsolute()) {
            Optional<Path> runtimeHomeOpt = resolveRuntimeHome();
            if (runtimeHomeOpt.isEmpty()) {
                return Optional.empty();
            }
            candidate = runtimeHomeOpt.get().resolve(value);
        }
        return Optional.of(candidate.toAbsolutePath().normalize());
    }

    private Optional<Path> resolveChromiumFromRuntimeHome(Path runtimeHome) {
        List<Path> candidates = new ArrayList<>();
        candidates.add(runtimeHome.resolve("browser/chromium/chrome.exe"));
        candidates.add(runtimeHome.resolve("browser/chromium/chrome"));
        candidates.add(runtimeHome.resolve("browser/chromium/chrome-win64/chrome.exe"));
        candidates.add(runtimeHome.resolve("browser/chromium/chrome-linux/chrome"));
        candidates.add(runtimeHome.resolve("browser/chromium/Chromium.app/Contents/MacOS/Chromium"));
        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.isRegularFile(normalized)) {
                return Optional.of(normalized);
            }
        }
        return Optional.empty();
    }

    private Optional<Path> resolveCompatShimDir() {
        try {
            Optional<Path> runtimeHomeOpt = resolveRuntimeHome();
            if (runtimeHomeOpt.isEmpty()) {
                return Optional.empty();
            }

            boolean needsPython3Shim = hasBundledBinary("python") && !hasBundledBinary("python3");
            boolean needsPip3Shim = hasBundledBinary("pip") && !hasBundledBinary("pip3");
            if (!needsPython3Shim && !needsPip3Shim) {
                return Optional.empty();
            }

            Path shimDir = runtimeHomeOpt.get().resolve("shims").toAbsolutePath().normalize();
            Files.createDirectories(shimDir);

            if (needsPython3Shim) {
                writeShim(shimDir, "python3", "python");
            }
            if (needsPip3Shim) {
                writeShim(shimDir, "pip3", "pip");
            }
            return Optional.of(shimDir);
        } catch (Exception e) {
            log.warn("Failed to prepare runtime compatibility shims", e);
            return Optional.empty();
        }
    }

    private void writeShim(Path shimDir, String alias, String target) throws IOException {
        if (IS_WINDOWS) {
            Path script = shimDir.resolve(alias + ".cmd");
            String content = "@echo off\r\n" + target + " %*\r\n";
            Files.writeString(script, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return;
        }

        Path script = shimDir.resolve(alias);
        String content = "#!/usr/bin/env sh\nexec " + target + " \"$@\"\n";
        Files.writeString(script, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            Files.setPosixFilePermissions(script, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ,
                    PosixFilePermission.OTHERS_EXECUTE
            ));
        } catch (UnsupportedOperationException ignore) {
            // 非 POSIX 文件系统忽略
        }
    }
}
