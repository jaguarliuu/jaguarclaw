package com.jaguarliu.ai.agents.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.agents.AgentConstants;
import com.jaguarliu.ai.agents.AgentRegistry;
import com.jaguarliu.ai.agents.entity.AgentProfileEntity;
import com.jaguarliu.ai.agents.repository.AgentProfileRepository;
import com.jaguarliu.ai.mcp.persistence.McpServerRepository;
import com.jaguarliu.ai.memory.index.MemoryChunkEntity;
import com.jaguarliu.ai.memory.index.MemoryChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

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

        if (repository.existsByName(request.name())) {
            throw new IllegalArgumentException("Agent name already exists: " + request.name());
        }

        boolean shouldBeDefault = request.isDefault() != null
                ? request.isDefault()
                : repository.findByIsDefaultTrue().isEmpty();

        if (shouldBeDefault) {
            clearCurrentDefault();
        }

        String workspacePath = normalizeWorkspacePath(request.name(), request.workspacePath());
        ensureWorkspaceDirectories(workspacePath);

        AgentProfileEntity entity = AgentProfileEntity.builder()
                .name(request.name().trim())
                .displayName(request.displayName() != null && !request.displayName().isBlank()
                        ? request.displayName().trim()
                        : request.name().trim())
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
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        validateAgentName(request.name().trim());
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

    private static final Path WORKSPACE_ROOT = Path.of("workspace").toAbsolutePath().normalize();

    private String normalizeWorkspacePath(String name, String inputPath) {
        Path path = (inputPath != null && !inputPath.isBlank())
                ? Path.of(inputPath.trim())
                : Path.of("workspace", "agents", name);

        // 如果是相对路径，基于 workspace root 解析
        Path resolved = path.isAbsolute()
                ? path.toAbsolutePath().normalize()
                : WORKSPACE_ROOT.resolve(path).toAbsolutePath().normalize();

        // 安全检查：必须在 workspace root 内
        if (!resolved.startsWith(WORKSPACE_ROOT)) {
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
