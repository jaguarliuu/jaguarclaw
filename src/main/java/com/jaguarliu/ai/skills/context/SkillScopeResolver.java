package com.jaguarliu.ai.skills.context;

import com.jaguarliu.ai.agents.context.AgentWorkspaceResolver;
import com.jaguarliu.ai.agents.entity.AgentProfileEntity;
import com.jaguarliu.ai.agents.service.AgentProfileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Skill 作用域目录解析器
 *
 * 目录约定：
 * - 全局 skill：由 SkillRegistry 现有 project/user/builtin 目录维护
 * - agent skill：workspace/agents/{agentId}/skills（或 AgentProfile 自定义 workspace 下的 skills）
 */
@Component
public class SkillScopeResolver {

    @Value("${tools.workspace:./workspace}")
    private String workspaceRoot;

    private final Optional<AgentProfileService> agentProfileService;
    private final Optional<AgentWorkspaceResolver> workspaceResolver;

    public SkillScopeResolver(Optional<AgentProfileService> agentProfileService,
                              Optional<AgentWorkspaceResolver> workspaceResolver) {
        this.agentProfileService = agentProfileService;
        this.workspaceResolver = workspaceResolver;
    }

    public String normalizeAgentId(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return "main";
        }
        return agentId.trim();
    }

    public Path resolveAgentSkillsDir(String agentId) {
        String resolvedAgentId = normalizeAgentId(agentId);
        if (workspaceResolver.isPresent()) {
            return workspaceResolver.get()
                    .resolveAgentWorkspace(resolvedAgentId)
                    .resolve("skills")
                    .toAbsolutePath()
                    .normalize();
        }
        return resolveAgentsRoot()
                .resolve(resolvedAgentId)
                .resolve("skills")
                .toAbsolutePath()
                .normalize();
    }

    public Path resolveAgentsRoot() {
        return Path.of(workspaceRoot)
                .resolve("agents")
                .toAbsolutePath()
                .normalize();
    }

    /**
     * 解析当前系统中所有可扫描的 agent skills 目录。
     */
    public Map<String, Path> resolveAllAgentSkillsDirs() {
        Map<String, Path> result = new LinkedHashMap<>();

        if (agentProfileService.isPresent()) {
            List<AgentProfileEntity> profiles = agentProfileService.get().list();
            for (AgentProfileEntity profile : profiles) {
                String agentId = normalizeAgentId(profile.getId());
                result.put(agentId, resolveAgentSkillsDir(agentId));
            }
        }

        Path agentsRoot = resolveAgentsRoot();
        if (Files.exists(agentsRoot) && Files.isDirectory(agentsRoot)) {
            try (var stream = Files.list(agentsRoot)) {
                stream.filter(Files::isDirectory).forEach(agentDir -> {
                    String agentId = agentDir.getFileName().toString();
                    result.putIfAbsent(agentId, agentDir.resolve("skills").toAbsolutePath().normalize());
                });
            } catch (Exception ignored) {
                // best effort
            }
        }

        return result;
    }
}
