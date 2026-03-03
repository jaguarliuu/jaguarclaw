package com.jaguarliu.ai.agents.service;

import com.jaguarliu.ai.soul.SoulConfigService;
import com.jaguarliu.ai.heartbeat.HeartbeatConfigService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.agents.AgentConstants;
import com.jaguarliu.ai.agents.AgentRegistry;
import com.jaguarliu.ai.agents.PinyinUtils;
import com.jaguarliu.ai.agents.entity.AgentProfileEntity;
import com.jaguarliu.ai.agents.repository.AgentProfileRepository;
import com.jaguarliu.ai.mcp.persistence.McpServerRepository;
import com.jaguarliu.ai.memory.index.MemoryChunkEntity;
import com.jaguarliu.ai.memory.index.MemoryChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Agent Profile 控制面服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentProfileService {

    private static final String DEFAULT_AGENT_ID = AgentConstants.DEFAULT_AGENT_ID;
    private static final String DEFAULT_AGENT_NAME = AgentConstants.DEFAULT_AGENT_NAME;
    private static final String DEFAULT_AGENT_DISPLAY_NAME = AgentConstants.DEFAULT_AGENT_DISPLAY_NAME;

    private final AgentProfileRepository repository;
    private final AgentRegistry agentRegistry;
    private final MemoryChunkRepository memoryChunkRepository;
    private final McpServerRepository mcpServerRepository;
    private final ObjectMapper objectMapper;
    // ObjectProvider used to break circular dependency:
    // AgentProfileService → SoulConfigService → AgentWorkspaceResolver → Optional<AgentProfileService>
    private final ObjectProvider<SoulConfigService> soulConfigServiceProvider;
    private final ObjectProvider<HeartbeatConfigService> heartbeatConfigServiceProvider;

    @Value("${tools.workspace:./workspace}")
    private String workspaceRoot;

    @Transactional(readOnly = true)
    public List<AgentProfileEntity> list() {
        return repository.findAllByOrderByCreatedAtAsc();
    }

    @Transactional(readOnly = true)
    public Optional<AgentProfileEntity> get(String agentId) {
        return repository.findById(agentId);
    }

    @Transactional
    public void ensureDefaultMainAgentExists() {
        if (repository.count() > 0) {
            return;
        }

        String workspacePath = normalizeWorkspacePath(DEFAULT_AGENT_NAME, null);
        ensureWorkspaceDirectories(workspacePath);

        AgentProfileEntity defaultAgent = AgentProfileEntity.builder()
                .id(DEFAULT_AGENT_ID)
                .name(DEFAULT_AGENT_NAME)
                .displayName(DEFAULT_AGENT_DISPLAY_NAME)
                .workspacePath(workspacePath)
                .enabled(true)
                .isDefault(true)
                .allowedTools("[]")
                .excludedTools("[]")
                .build();

        try {
            repository.save(defaultAgent);
            log.info("Bootstrapped default agent profile: id={}, workspace={}", DEFAULT_AGENT_ID, workspacePath);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Default agent bootstrap skipped due to concurrent initialization", ex);
        }
    }

    @Transactional
    public AgentProfileEntity create(CreateAgentProfileRequest request) {
        validateCreateRequest(request);

        // 从 displayName 推导内部 name（拼音 slug），处理重名冲突
        String displayName = request.displayName().trim();
        String baseName = PinyinUtils.toSlug(displayName);
        String name = resolveUniqueName(baseName);

        boolean shouldBeDefault = request.isDefault() != null
                ? request.isDefault()
                : repository.findByIsDefaultTrue().isEmpty();

        if (shouldBeDefault) {
            clearCurrentDefault();
        }

        String workspacePath = normalizeWorkspacePath(name, request.workspacePath());
        ensureWorkspaceDirectories(workspacePath);

        AgentProfileEntity entity = AgentProfileEntity.builder()
                .name(name)
                .displayName(displayName)
                .description(trimToNull(request.description()))
                .workspacePath(workspacePath)
                .model(trimToNull(request.model()))
                .enabled(request.enabled() != null ? request.enabled() : true)
                .isDefault(shouldBeDefault)
                .allowedTools(toJsonArray(request.allowedTools()))
                .excludedTools(toJsonArray(request.excludedTools()))
                .heartbeatInterval(request.heartbeatInterval())
                .heartbeatActiveHours(trimToNull(request.heartbeatActiveHours()))
                .dailyTokenLimit(request.dailyTokenLimit())
                .monthlyCostLimit(request.monthlyCostLimit())
                .build();

        AgentProfileEntity saved = repository.save(entity);

        // Initialize soul and heartbeat defaults for the new agent
        soulConfigServiceProvider.getObject().ensureAgentDefaults(saved.getId(), saved.getDisplayName());
        heartbeatConfigServiceProvider.getObject().ensureAgentDefaults(saved.getId());

        agentRegistry.refresh();
        log.info("Created agent profile: id={}, name={}, default={}, enabled={}",
                saved.getId(), saved.getName(), saved.getIsDefault(), saved.getEnabled());
        return saved;
    }

    @Transactional
    public AgentProfileEntity update(UpdateAgentProfileRequest request) {
        if (request.agentId() == null || request.agentId().isBlank()) {
            throw new IllegalArgumentException("agentId is required");
        }

        AgentProfileEntity entity = repository.findById(request.agentId())
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + request.agentId()));

        if (request.name() != null && !request.name().isBlank()) {
            String normalizedName = request.name().trim();
            validateAgentName(normalizedName);
            if (repository.existsByNameAndIdNot(normalizedName, entity.getId())) {
                throw new IllegalArgumentException("Agent name already exists: " + normalizedName);
            }
            entity.setName(normalizedName);
        }

        if (request.displayName() != null) {
            entity.setDisplayName(request.displayName().isBlank()
                    ? entity.getName()
                    : request.displayName().trim());
        }
        if (request.description() != null) {
            entity.setDescription(trimToNull(request.description()));
        }
        if (request.model() != null) {
            entity.setModel(trimToNull(request.model()));
        }
        if (request.enabled() != null) {
            entity.setEnabled(request.enabled());
        }
        if (request.allowedTools() != null) {
            entity.setAllowedTools(toJsonArray(request.allowedTools()));
        }
        if (request.excludedTools() != null) {
            entity.setExcludedTools(toJsonArray(request.excludedTools()));
        }
        if (request.heartbeatInterval() != null) {
            entity.setHeartbeatInterval(request.heartbeatInterval());
        }
        if (request.heartbeatActiveHours() != null) {
            entity.setHeartbeatActiveHours(trimToNull(request.heartbeatActiveHours()));
        }
        if (request.dailyTokenLimit() != null) {
            entity.setDailyTokenLimit(request.dailyTokenLimit());
        }
        if (request.monthlyCostLimit() != null) {
            entity.setMonthlyCostLimit(request.monthlyCostLimit());
        }
        if (request.workspacePath() != null) {
            String workspacePath = normalizeWorkspacePath(entity.getName(), request.workspacePath());
            ensureWorkspaceDirectories(workspacePath);
            entity.setWorkspacePath(workspacePath);
        }

        if (request.isDefault() != null) {
            if (Boolean.TRUE.equals(request.isDefault())) {
                clearCurrentDefault();
                entity.setIsDefault(true);
            } else if (Boolean.TRUE.equals(entity.getIsDefault())) {
                throw new IllegalStateException("Cannot unset default agent directly. Set another default agent first.");
            } else {
                entity.setIsDefault(false);
            }
        }

        AgentProfileEntity saved = repository.save(entity);
        agentRegistry.refresh();
        log.info("Updated agent profile: id={}, name={}, default={}, enabled={}",
                saved.getId(), saved.getName(), saved.getIsDefault(), saved.getEnabled());
        return saved;
    }

    @Transactional
    public void delete(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId is required");
        }

        AgentProfileEntity entity = repository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            throw new IllegalStateException("Cannot delete default agent");
        }

        // cascade cleanup: memory chunks and MCP server configs
        memoryChunkRepository.deleteByScopeAndAgentId(MemoryChunkEntity.SCOPE_AGENT, agentId);
        mcpServerRepository.deleteByScopeAndAgentId("AGENT", agentId);

        repository.deleteById(agentId);
        agentRegistry.refresh();
        log.info("Deleted agent profile: id={}, name={}", entity.getId(), entity.getName());
    }

    private void validateCreateRequest(CreateAgentProfileRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (request.displayName() == null || request.displayName().isBlank()) {
            throw new IllegalArgumentException("displayName is required");
        }
    }

    private String resolveUniqueName(String baseName) {
        if (!repository.existsByName(baseName)) {
            return baseName;
        }
        int suffix = 2;
        while (true) {
            String candidate = baseName + "-" + suffix;
            if (!repository.existsByName(candidate)) {
                return candidate;
            }
            suffix++;
        }
    }

    private void validateAgentName(String name) {
        if (!AgentConstants.SAFE_AGENT_ID_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid agent name: " + name);
        }
    }

    private void clearCurrentDefault() {
        repository.findByIsDefaultTrue().ifPresent(existingDefault -> {
            existingDefault.setIsDefault(false);
            repository.save(existingDefault);
        });
    }

    private Path workspaceRootPath() {
        return Path.of(workspaceRoot).toAbsolutePath().normalize();
    }

    private String normalizeWorkspacePath(String name, String inputPath) {
        Path workspaceRootPath = workspaceRootPath();
        Path path = (inputPath != null && !inputPath.isBlank())
                ? Path.of(inputPath.trim())
                : Path.of("workspace-" + name);

        // 如果是相对路径，基于 workspace root 解析
        Path resolved = path.isAbsolute()
                ? path.toAbsolutePath().normalize()
                : workspaceRootPath.resolve(path).toAbsolutePath().normalize();

        // 安全检查：必须在 workspace root 内
        if (!resolved.startsWith(workspaceRootPath)) {
            throw new IllegalArgumentException(
                    "Invalid workspacePath: path must be within workspace root");
        }

        return resolved.toString();
    }

    private void ensureWorkspaceDirectories(String workspacePath) {
        try {
            Path base = Path.of(workspacePath);
            Files.createDirectories(base);
            Files.createDirectories(base.resolve("memory"));
            Files.createDirectories(base.resolve("skills"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create workspace directories: " + workspacePath, e);
        }
    }

    /**
     * 修复历史 workspace 路径问题：
     * 1) 旧版双层嵌套路径（workspace/workspace/workspace-{name}）
     * 2) 旧版使用错误根目录（例如安装目录下 workspace），导致超出当前 tools.workspace
     */
    @Transactional
    public void migrateLegacyWorkspacePaths() {
        List<AgentProfileEntity> agents = repository.findAllByOrderByCreatedAtAsc();
        for (AgentProfileEntity agent : agents) {
            try {
                migrateAgentWorkspacePath(agent);
            } catch (Exception e) {
                log.warn("Failed to migrate workspace path for agentId={}", agent.getId(), e);
            }
        }
    }

    private void migrateAgentWorkspacePath(AgentProfileEntity agent) throws IOException {
        if (agent.getWorkspacePath() == null || agent.getWorkspacePath().isBlank()) {
            return;
        }

        final Path storedPath;
        try {
            storedPath = Path.of(agent.getWorkspacePath()).toAbsolutePath().normalize();
        } catch (InvalidPathException ex) {
            Path fallbackPath = workspaceRootPath().resolve("workspace-" + agent.getName()).toAbsolutePath().normalize();
            ensureWorkspaceDirectories(fallbackPath.toString());
            agent.setWorkspacePath(fallbackPath.toString());
            repository.save(agent);
            log.info("Repaired invalid workspace path for agentId={}, newPath={}", agent.getId(), fallbackPath);
            return;
        }

        Path workspaceRootPath = workspaceRootPath();
        Path expectedPath = workspaceRootPath.resolve("workspace-" + agent.getName()).toAbsolutePath().normalize();

        if (storedPath.equals(expectedPath) || storedPath.startsWith(workspaceRootPath)) {
            return;
        }

        log.info("Migrating legacy workspace for agentId={}: {} -> {}",
                agent.getId(), storedPath, expectedPath);

        copyWorkspaceTree(storedPath, expectedPath);
        ensureWorkspaceDirectories(expectedPath.toString());

        agent.setWorkspacePath(expectedPath.toString());
        repository.save(agent);
        log.info("Workspace path migrated in DB for agentId={}, newPath={}", agent.getId(), expectedPath);
    }

    private void copyWorkspaceTree(Path sourceRoot, Path targetRoot) throws IOException {
        if (!Files.exists(sourceRoot)) {
            return;
        }

        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            for (Path source : (Iterable<Path>) stream::iterator) {
                Path target = targetRoot.resolve(sourceRoot.relativize(source));
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else if (!Files.exists(target)) {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private String toJsonArray(List<String> values) {
        if (values == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid tool list payload", e);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record CreateAgentProfileRequest(
            String name,
            String displayName,
            String description,
            String workspacePath,
            String model,
            Boolean enabled,
            Boolean isDefault,
            List<String> allowedTools,
            List<String> excludedTools,
            Integer heartbeatInterval,
            String heartbeatActiveHours,
            Integer dailyTokenLimit,
            Double monthlyCostLimit
    ) {
    }

    public record UpdateAgentProfileRequest(
            String agentId,
            String name,
            String displayName,
            String description,
            String workspacePath,
            String model,
            Boolean enabled,
            Boolean isDefault,
            List<String> allowedTools,
            List<String> excludedTools,
            Integer heartbeatInterval,
            String heartbeatActiveHours,
            Integer dailyTokenLimit,
            Double monthlyCostLimit
    ) {
    }
}
