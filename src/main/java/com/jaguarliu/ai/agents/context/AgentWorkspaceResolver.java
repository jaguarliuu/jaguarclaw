package com.jaguarliu.ai.agents.context;

import com.jaguarliu.ai.agents.entity.AgentProfileEntity;
import com.jaguarliu.ai.agents.service.AgentProfileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Agent 作用域工作目录解析器
 */
@Component
public class AgentWorkspaceResolver {

    @Value("${tools.workspace:./workspace}")
    private String workspaceRoot;

    private final Optional<AgentProfileService> agentProfileService;

    public AgentWorkspaceResolver(Optional<AgentProfileService> agentProfileService) {
        this.agentProfileService = agentProfileService;
    }

    public Path resolveAgentWorkspace(String agentId) {
        String resolvedAgentId = normalizeAgentId(agentId);

        if (agentProfileService.isPresent()) {
            Optional<AgentProfileEntity> profile = agentProfileService.get().get(resolvedAgentId);
            if (profile.isPresent()
                    && profile.get().getWorkspacePath() != null
                    && !profile.get().getWorkspacePath().isBlank()) {
                return Path.of(profile.get().getWorkspacePath()).toAbsolutePath().normalize();
            }
        }

        return Path.of(workspaceRoot)
                .resolve("agents")
                .resolve(resolvedAgentId)
                .toAbsolutePath()
                .normalize();
    }

    public Path resolveAgentFile(String agentId, String fileName) {
        return resolveAgentWorkspace(agentId).resolve(fileName).toAbsolutePath().normalize();
    }

    public String normalizeAgentId(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return "main";
        }
        return agentId;
    }
}
