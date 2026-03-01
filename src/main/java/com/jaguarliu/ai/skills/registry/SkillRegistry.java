package com.jaguarliu.ai.skills.registry;

import com.jaguarliu.ai.skills.context.SkillScopeResolver;
import com.jaguarliu.ai.skills.gating.GatingResult;
import com.jaguarliu.ai.skills.gating.SkillGatingService;
import com.jaguarliu.ai.skills.model.LoadedSkill;
import com.jaguarliu.ai.skills.model.SkillEntry;
import com.jaguarliu.ai.skills.model.SkillMetadata;
import com.jaguarliu.ai.skills.parser.SkillParseResult;
import com.jaguarliu.ai.skills.parser.SkillParser;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Skill 注册表
 *
 * 职责：
 * 1. 扫描全局目录（项目级 > 用户级 > 内置）；
 * 2. 扫描 Agent 私有目录（workspace/agents/<agent>/skills）；
 * 3. 按 Agent 作用域合并可见 skill（agent 覆盖 global 同名）；
 * 4. 提供索引/激活查询。
 */
@Slf4j
@Service
public class SkillRegistry {

    private final SkillParser parser;
    private final SkillGatingService gatingService;
    private final Optional<SkillScopeResolver> scopeResolver;

    @Value("${skills.project-dir:}")
    private String configProjectDir;

    @Value("${skills.user-dir:}")
    private String configUserDir;

    @Value("${skills.builtin-dir:}")
    private String configBuiltinDir;

    // global: skill name -> SkillEntry
    private volatile Map<String, SkillEntry> registry = new ConcurrentHashMap<>();

    // global body cache: skill name -> body
    private volatile Map<String, String> bodyCache = new ConcurrentHashMap<>();

    // agent scoped registry: agentId -> (skill name -> SkillEntry)
    private volatile Map<String, Map<String, SkillEntry>> agentRegistry = new ConcurrentHashMap<>();

    // agent scoped body cache: agentId -> (skill name -> body)
    private volatile Map<String, Map<String, String>> agentBodyCache = new ConcurrentHashMap<>();

    // test override: agentId -> skills dir
    private volatile Map<String, Path> configuredAgentSkillsDirs = Map.of();

    // 快照版本号（每次刷新递增，用于前端缓存控制）
    private volatile long snapshotVersion = 0;

    // 全局扫描目录配置
    private Path projectSkillsDir;
    private Path userSkillsDir;
    private Path builtinSkillsDir;

    @Autowired
    public SkillRegistry(SkillParser parser,
                         SkillGatingService gatingService,
                         Optional<SkillScopeResolver> scopeResolver) {
        this.parser = parser;
        this.gatingService = gatingService;
        this.scopeResolver = scopeResolver;
    }

    // 兼容单元测试中的直接构造
    public SkillRegistry(SkillParser parser, SkillGatingService gatingService) {
        this(parser, gatingService, Optional.empty());
    }

    /**
     * 初始化时扫描所有 skill 目录
     */
    @PostConstruct
    public void init() {
        projectSkillsDir = configProjectDir != null && !configProjectDir.isBlank()
                ? Paths.get(configProjectDir)
                : Paths.get(System.getProperty("user.dir"), ".jaguarclaw", "skills");
        userSkillsDir = configUserDir != null && !configUserDir.isBlank()
                ? Paths.get(configUserDir)
                : Paths.get(System.getProperty("user.home"), ".jaguarclaw", "skills");
        builtinSkillsDir = configBuiltinDir != null && !configBuiltinDir.isBlank()
                ? Paths.get(configBuiltinDir)
                : Paths.get(System.getProperty("user.dir"), "skills");

        refresh();
    }

    public Path getProjectSkillsDir() {
        return projectSkillsDir;
    }

    public Path getUserSkillsDir() {
        return userSkillsDir;
    }

    public Path getBuiltinSkillsDir() {
        return builtinSkillsDir;
    }

    /**
     * 配置全局扫描目录（用于测试）
     */
    public void configure(Path projectDir, Path userDir, Path builtinDir) {
        this.projectSkillsDir = projectDir;
        this.userSkillsDir = userDir;
        this.builtinSkillsDir = builtinDir;
    }

    /**
     * 配置 agent skills 目录（用于测试）
     */
    public void configureAgentSkills(Map<String, Path> agentSkillsDirs) {
        if (agentSkillsDirs == null || agentSkillsDirs.isEmpty()) {
            this.configuredAgentSkillsDirs = Map.of();
            return;
        }
        Map<String, Path> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Path> entry : agentSkillsDirs.entrySet()) {
            String agentId = normalizeAgentId(entry.getKey());
            Path dir = entry.getValue();
            if (dir != null) {
                copy.put(agentId, dir.toAbsolutePath().normalize());
            }
        }
        this.configuredAgentSkillsDirs = Map.copyOf(copy);
    }

    /**
     * 刷新注册表（重新扫描所有目录）
     * Copy-on-write 原子切换，避免并发读取半成品。
     */
    public void refresh() {
        Map<String, SkillEntry> newRegistry = new ConcurrentHashMap<>();
        Map<String, String> newBodyCache = new ConcurrentHashMap<>();

        // 全局优先级：project(0) > user(1) > builtin(2)
        scanDirectory(builtinSkillsDir, 2, newRegistry, newBodyCache);
        scanDirectory(userSkillsDir, 1, newRegistry, newBodyCache);
        scanDirectory(projectSkillsDir, 0, newRegistry, newBodyCache);

        Map<String, Map<String, SkillEntry>> newAgentRegistry = new ConcurrentHashMap<>();
        Map<String, Map<String, String>> newAgentBodyCache = new ConcurrentHashMap<>();

        Map<String, Path> agentDirs = resolveAgentSkillsDirs();
        for (Map.Entry<String, Path> entry : agentDirs.entrySet()) {
            String agentId = normalizeAgentId(entry.getKey());
            Map<String, SkillEntry> scopedRegistry = new ConcurrentHashMap<>();
            Map<String, String> scopedBodyCache = new ConcurrentHashMap<>();
            scanDirectory(entry.getValue(), 0, scopedRegistry, scopedBodyCache);
            if (!scopedRegistry.isEmpty()) {
                newAgentRegistry.put(agentId, scopedRegistry);
                newAgentBodyCache.put(agentId, scopedBodyCache);
            }
        }

        this.registry = newRegistry;
        this.bodyCache = newBodyCache;
        this.agentRegistry = newAgentRegistry;
        this.agentBodyCache = newAgentBodyCache;
        this.snapshotVersion++;

        long globalAvailable = newRegistry.values().stream().filter(SkillEntry::isAvailable).count();
        long agentAvailable = newAgentRegistry.values().stream()
                .flatMap(m -> m.values().stream())
                .filter(SkillEntry::isAvailable)
                .count();

        log.info("Skill registry refreshed (v{}): global={} (available={}), agentScoped={} (available={})",
                snapshotVersion,
                newRegistry.size(),
                globalAvailable,
                newAgentRegistry.values().stream().mapToInt(Map::size).sum(),
                agentAvailable);
    }

    private Map<String, Path> resolveAgentSkillsDirs() {
        if (configuredAgentSkillsDirs != null && !configuredAgentSkillsDirs.isEmpty()) {
            return configuredAgentSkillsDirs;
        }
        if (scopeResolver.isPresent()) {
            return scopeResolver.get().resolveAllAgentSkillsDirs();
        }
        return Map.of();
    }

    /**
     * 扫描单个目录
     */
    private void scanDirectory(Path dir,
                               int priority,
                               Map<String, SkillEntry> targetRegistry,
                               Map<String, String> targetBodyCache) {
        if (dir == null || !Files.exists(dir) || !Files.isDirectory(dir)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(dir, 2)) {
            paths.filter(this::isSkillFile)
                    .forEach(path -> loadSkill(path, priority, targetRegistry, targetBodyCache));
        } catch (IOException e) {
            log.warn("Failed to scan skill directory: {}", dir, e);
        }
    }

    /**
     * 判断是否为 SKILL.md 文件
     */
    private boolean isSkillFile(Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        String fileName = path.getFileName().toString();
        return fileName.equals("SKILL.md") || fileName.endsWith(".SKILL.md");
    }

    /**
     * 加载单个 skill
     */
    private void loadSkill(Path path,
                           int priority,
                           Map<String, SkillEntry> targetRegistry,
                           Map<String, String> targetBodyCache) {
        try {
            String content = Files.readString(path);
            long lastModified = Files.getLastModifiedTime(path).toMillis();

            SkillParseResult result = parser.parse(content, path, priority, lastModified);
            if (!result.isValid()) {
                log.warn("Failed to parse skill {}: {}", path, result.getErrorMessage());
                return;
            }

            SkillMetadata metadata = result.getMetadata();
            String name = metadata.getName();

            SkillEntry existing = targetRegistry.get(name);
            if (existing != null && existing.getMetadata().getPriority() <= priority) {
                log.debug("Skill '{}' already registered with higher priority, skipping", name);
                return;
            }

            GatingResult gatingResult = gatingService.evaluate(metadata.getRequires());

            SkillEntry entry = SkillEntry.builder()
                    .metadata(metadata)
                    .available(gatingResult.isAvailable())
                    .unavailableReason(gatingResult.getFailureReason())
                    .lastModified(lastModified)
                    .tokenCost(SkillEntry.calculateTokenCost(metadata))
                    .build();

            targetRegistry.put(name, entry);
            targetBodyCache.put(name, result.getBody());

            log.debug("Loaded skill '{}' from {} (available={})", name, path, gatingResult.isAvailable());
        } catch (IOException e) {
            log.warn("Failed to read skill file: {}", path, e);
        }
    }

    // ==================== Query APIs ====================

    public List<SkillEntry> getAll() {
        return getAll("main");
    }

    public List<SkillEntry> getGlobalAll() {
        return new ArrayList<>(registry.values());
    }

    public List<SkillEntry> getAll(String agentId) {
        return new ArrayList<>(mergedRegistry(agentId).values());
    }

    public List<SkillEntry> getAvailable() {
        return getAvailable("main");
    }

    public List<SkillEntry> getAvailable(String agentId) {
        return mergedRegistry(agentId).values().stream()
                .filter(SkillEntry::isAvailable)
                .collect(Collectors.toList());
    }

    public List<SkillEntry> getUnavailable() {
        return getUnavailable("main");
    }

    public List<SkillEntry> getUnavailable(String agentId) {
        return mergedRegistry(agentId).values().stream()
                .filter(e -> !e.isAvailable())
                .collect(Collectors.toList());
    }

    public Optional<SkillEntry> getByName(String name) {
        return getByName(name, "main");
    }

    public Optional<SkillEntry> getByName(String name, String agentId) {
        return Optional.ofNullable(mergedRegistry(agentId).get(name));
    }

    public boolean isAvailable(String name) {
        return isAvailable(name, "main");
    }

    public boolean isAvailable(String name, String agentId) {
        SkillEntry entry = mergedRegistry(agentId).get(name);
        return entry != null && entry.isAvailable();
    }

    /**
     * 激活 skill（加载完整内容）
     */
    public Optional<LoadedSkill> activate(String name) {
        return activate(name, "main");
    }

    public Optional<LoadedSkill> activateGlobal(String name) {
        SkillEntry entry = registry.get(name);
        if (entry == null) {
            log.warn("Global skill not found: {}", name);
            return Optional.empty();
        }
        if (!entry.isAvailable()) {
            log.warn("Global skill not available: {} (reason={})", name, entry.getUnavailableReason());
            return Optional.empty();
        }

        String body = bodyCache.get(name);
        if (body == null) {
            body = reloadBody(entry.getMetadata().getSourcePath());
            if (body == null) {
                return Optional.empty();
            }
            Map<String, String> updated = new ConcurrentHashMap<>(bodyCache);
            updated.put(name, body);
            bodyCache = updated;
        }

        SkillMetadata meta = entry.getMetadata();
        Path basePath = meta.getSourcePath() != null ? meta.getSourcePath().getParent() : null;

        LoadedSkill loaded = LoadedSkill.builder()
                .name(meta.getName())
                .description(meta.getDescription())
                .body(body)
                .basePath(basePath)
                .allowedTools(meta.getAllowedTools() != null ? new HashSet<>(meta.getAllowedTools()) : null)
                .confirmBefore(meta.getConfirmBefore() != null ? new HashSet<>(meta.getConfirmBefore()) : null)
                .build();

        log.info("Activated global skill: {} (basePath={})", name, basePath);
        return Optional.of(loaded);
    }

    public Optional<LoadedSkill> activate(String name, String agentId) {
        String resolvedAgentId = normalizeAgentId(agentId);

        SkillEntry entry = mergedRegistry(resolvedAgentId).get(name);
        if (entry == null) {
            log.warn("Skill not found: {} (agentId={})", name, resolvedAgentId);
            return Optional.empty();
        }

        if (!entry.isAvailable()) {
            log.warn("Skill not available: {} (agentId={}, reason={})",
                    name, resolvedAgentId, entry.getUnavailableReason());
            return Optional.empty();
        }

        String body = resolveBody(name, resolvedAgentId);
        if (body == null) {
            body = reloadBody(entry.getMetadata().getSourcePath());
            if (body == null) {
                return Optional.empty();
            }
            cacheBody(name, resolvedAgentId, entry, body);
        }

        SkillMetadata meta = entry.getMetadata();
        Path basePath = meta.getSourcePath() != null ? meta.getSourcePath().getParent() : null;

        LoadedSkill loaded = LoadedSkill.builder()
                .name(meta.getName())
                .description(meta.getDescription())
                .body(body)
                .basePath(basePath)
                .allowedTools(meta.getAllowedTools() != null ? new HashSet<>(meta.getAllowedTools()) : null)
                .confirmBefore(meta.getConfirmBefore() != null ? new HashSet<>(meta.getConfirmBefore()) : null)
                .build();

        log.info("Activated skill: {} (agentId={}, basePath={})", name, resolvedAgentId, basePath);
        return Optional.of(loaded);
    }

    private String resolveBody(String name, String agentId) {
        Map<String, String> scoped = agentBodyCache.get(agentId);
        if (scoped != null && scoped.containsKey(name)) {
            return scoped.get(name);
        }
        return bodyCache.get(name);
    }

    private void cacheBody(String name, String agentId, SkillEntry entry, String body) {
        Map<String, SkillEntry> scopedEntries = agentRegistry.get(agentId);
        if (scopedEntries != null) {
            SkillEntry scoped = scopedEntries.get(name);
            if (scoped != null && scoped == entry) {
                Map<String, String> scopedCache = new ConcurrentHashMap<>(
                        agentBodyCache.getOrDefault(agentId, Map.of())
                );
                scopedCache.put(name, body);
                Map<String, Map<String, String>> updated = new ConcurrentHashMap<>(agentBodyCache);
                updated.put(agentId, scopedCache);
                agentBodyCache = updated;
                return;
            }
        }
        Map<String, String> updated = new ConcurrentHashMap<>(bodyCache);
        updated.put(name, body);
        bodyCache = updated;
    }

    private String reloadBody(Path path) {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        try {
            String content = Files.readString(path);
            SkillParseResult result = parser.parse(content, path, 0, System.currentTimeMillis());
            return result.isValid() ? result.getBody() : null;
        } catch (IOException e) {
            log.warn("Failed to reload skill body: {}", path, e);
            return null;
        }
    }

    private Map<String, SkillEntry> mergedRegistry(String agentId) {
        String resolvedAgentId = normalizeAgentId(agentId);

        Map<String, SkillEntry> merged = new LinkedHashMap<>(registry);
        Map<String, SkillEntry> scoped = agentRegistry.get(resolvedAgentId);
        if (scoped != null && !scoped.isEmpty()) {
            merged.putAll(scoped);
        }
        return merged;
    }

    private String normalizeAgentId(String agentId) {
        if (scopeResolver.isPresent()) {
            return scopeResolver.get().normalizeAgentId(agentId);
        }
        if (agentId == null || agentId.isBlank()) {
            return "main";
        }
        return agentId.trim();
    }

    /**
     * 检查是否有 skill 文件变化（修改/新增/删除）
     */
    public boolean hasChanges() {
        Map<Path, Long> currentSnapshot = new HashMap<>();
        collectSkillFiles(builtinSkillsDir, currentSnapshot);
        collectSkillFiles(userSkillsDir, currentSnapshot);
        collectSkillFiles(projectSkillsDir, currentSnapshot);
        resolveAgentSkillsDirs().values().forEach(dir -> collectSkillFiles(dir, currentSnapshot));

        Map<Path, Long> registeredSnapshot = new HashMap<>();
        for (SkillEntry entry : registry.values()) {
            Path path = entry.getMetadata().getSourcePath();
            if (path != null) {
                registeredSnapshot.put(path, entry.getLastModified());
            }
        }
        for (Map<String, SkillEntry> scopedRegistry : agentRegistry.values()) {
            for (SkillEntry entry : scopedRegistry.values()) {
                Path path = entry.getMetadata().getSourcePath();
                if (path != null) {
                    registeredSnapshot.put(path, entry.getLastModified());
                }
            }
        }

        for (Path path : currentSnapshot.keySet()) {
            if (!registeredSnapshot.containsKey(path)) {
                log.debug("Detected new skill file: {}", path);
                return true;
            }
        }

        for (Path path : registeredSnapshot.keySet()) {
            if (!currentSnapshot.containsKey(path)) {
                log.debug("Detected deleted skill file: {}", path);
                return true;
            }
        }

        for (Map.Entry<Path, Long> entry : currentSnapshot.entrySet()) {
            Path path = entry.getKey();
            Long currentModified = entry.getValue();
            Long registeredModified = registeredSnapshot.get(path);
            if (registeredModified != null && currentModified > registeredModified) {
                log.debug("Detected modified skill file: {}", path);
                return true;
            }
        }

        return false;
    }

    private void collectSkillFiles(Path dir, Map<Path, Long> snapshot) {
        if (dir == null || !Files.exists(dir) || !Files.isDirectory(dir)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(dir, 2)) {
            paths.filter(this::isSkillFile).forEach(path -> {
                try {
                    long lastModified = Files.getLastModifiedTime(path).toMillis();
                    snapshot.put(path, lastModified);
                } catch (IOException ignored) {
                    // ignore unreadable files
                }
            });
        } catch (IOException e) {
            log.warn("Failed to collect skill files from: {}", dir, e);
        }
    }

    public RegistryStats getStats() {
        return getStats("main");
    }

    public RegistryStats getStats(String agentId) {
        Map<String, SkillEntry> merged = mergedRegistry(agentId);
        int total = merged.size();
        int available = (int) merged.values().stream().filter(SkillEntry::isAvailable).count();
        int totalTokens = merged.values().stream()
                .filter(SkillEntry::isAvailable)
                .mapToInt(SkillEntry::getTokenCost)
                .sum();
        return new RegistryStats(total, available, total - available, totalTokens);
    }

    public record RegistryStats(
            int totalSkills,
            int availableSkills,
            int unavailableSkills,
            int totalTokenCost
    ) {
    }

    public int size() {
        return size("main");
    }

    public int size(String agentId) {
        return mergedRegistry(agentId).size();
    }

    public long getSnapshotVersion() {
        return snapshotVersion;
    }

    public void clear() {
        registry.clear();
        bodyCache.clear();
        agentRegistry.clear();
        agentBodyCache.clear();
    }
}
