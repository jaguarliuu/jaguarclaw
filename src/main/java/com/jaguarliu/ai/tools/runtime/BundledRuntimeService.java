package com.jaguarliu.ai.tools.runtime;

import com.jaguarliu.ai.tools.ToolsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 内置 runtime 路径解析与环境注入服务。
 */
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

        String pathKey = resolvePathKey(env);
        String existing = env.getOrDefault(pathKey, "");

        String prefix = bins.stream()
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
}
