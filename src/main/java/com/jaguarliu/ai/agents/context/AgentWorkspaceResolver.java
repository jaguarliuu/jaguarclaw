package com.jaguarliu.ai.agents.context;

import com.jaguarliu.ai.agents.AgentConstants;
import com.jaguarliu.ai.agents.entity.AgentProfileEntity;
import com.jaguarliu.ai.agents.service.AgentProfileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Agent 作用域工作目录解析器
 */
@Component
public class AgentWorkspaceResolver {

    private static final String DEFAULT_AGENT_ID = AgentConstants.DEFAULT_AGENT_ID;
    private static final Pattern SAFE_AGENT_ID_PATTERN = AgentConstants.SAFE_AGENT_ID_PATTERN;

    @Value("${tools.workspace:./workspace}")
    private String workspaceRoot;

    private final Optional<AgentProfileService> agentProfileService;

    public AgentWorkspaceResolver(Optional<AgentProfileService> agentProfileService) {
        this.agentProfileService = agentProfileService;
    }

    public Path resolveAgentWorkspace(String agentId) {
        String resolvedAgentId = normalizeAgentId(agentId);
        Path workspaceRootPath = getWorkspaceRootPath();

        if (agentProfileService.isPresent()) {
            Optional<AgentProfileEntity> profile = agentProfileService.get().get(resolvedAgentId);
            if (profile.isPresent()
                    && profile.get().getWorkspacePath() != null
                    && !profile.get().getWorkspacePath().isBlank()) {
                return resolveProfileWorkspace(profile.get().getWorkspacePath(), workspaceRootPath);
            }
        }

        Path agentsRoot = workspaceRootPath.resolve("agents").toAbsolutePath().normalize();
        Path resolved = agentsRoot
                .resolve(resolvedAgentId)
                .toAbsolutePath()
                .normalize();
        assertWithinBase(resolved, agentsRoot, "agent workspace");
        return resolved;
    }

    public Path resolveAgentFile(String agentId, String fileName) {
        Path agentWorkspace = resolveAgentWorkspace(agentId);
        Path safeFileName = normalizeFileName(fileName);
        Path resolved = agentWorkspace.resolve(safeFileName).toAbsolutePath().normalize();
        assertWithinBase(resolved, agentWorkspace, "agent file");
        return resolved;
    }

    public String normalizeAgentId(String agentId) {
        String resolved = (agentId == null || agentId.isBlank()) ? DEFAULT_AGENT_ID : agentId.trim();
        if (!SAFE_AGENT_ID_PATTERN.matcher(resolved).matches()) {
            throw new IllegalArgumentException("Invalid agentId: " + resolved);
        }
        return resolved;
    }

    private Path getWorkspaceRootPath() {
        return Path.of(workspaceRoot).toAbsolutePath().normalize();
    }

    private Path resolveProfileWorkspace(String workspacePath, Path workspaceRootPath) {
        final Path candidate;
        try {
            Path configured = Path.of(workspacePath.trim());
            if (configured.isAbsolute()) {
                candidate = configured.toAbsolutePath().normalize();
            } else {
                Path legacyRelative = configured.toAbsolutePath().normalize();
                candidate = legacyRelative.startsWith(workspaceRootPath)
                        ? legacyRelative
                        : workspaceRootPath.resolve(configured).toAbsolutePath().normalize();
            }
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException("Invalid workspacePath: " + workspacePath, ex);
        }
        assertWithinBase(candidate, workspaceRootPath, "workspacePath");
        return candidate;
    }

    private Path normalizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName is required");
        }
        try {
            Path normalized = Path.of(fileName.trim()).normalize();
            String value = normalized.toString();
            if (normalized.isAbsolute() || normalized.getNameCount() != 1 || ".".equals(value) || "..".equals(value)) {
                throw new IllegalArgumentException("Invalid fileName: " + fileName);
            }
            return normalized;
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException("Invalid fileName: " + fileName, ex);
        }
    }

    private void assertWithinBase(Path target, Path base, String fieldName) {
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("Invalid " + fieldName + ": path outside workspace root");
        }
    }
}
