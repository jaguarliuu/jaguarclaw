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
    }

    public boolean hasBundledBinary(String binName) {
        if (!isEnabled() || binName == null || binName.isBlank()) {
            return false;
        }
        for (Path binPath : resolveBinPaths()) {
            for (String candidateName : resolveBinaryCandidates(binName)) {
                Path candidate = binPath.resolve(candidateName).normalize();
                if (Files.isRegularFile(candidate)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> defaultBinEntries() {
        if (IS_WINDOWS) {
            return List.of("node", "python", "python/Scripts");
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
        if (!IS_WINDOWS || binName.contains(".")) {
            return List.of(binName);
        }
        return List.of(binName + ".exe", binName + ".cmd", binName + ".bat", binName);
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
