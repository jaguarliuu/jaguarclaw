package com.jaguarliu.ai.agents;

import com.jaguarliu.ai.agents.entity.AgentProfileEntity;
import com.jaguarliu.ai.agents.model.AgentProfile;
import com.jaguarliu.ai.agents.repository.AgentProfileRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 注册中心
 * DB-first + YAML fallback
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRegistry {

    private final AgentsProperties agentsProperties;
    private final AgentProfileRepository agentProfileRepository;

    private final Map<String, AgentProfile> registry = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refresh();
    }

    /**
     * 从 DB 重新加载所有 Agent Profile。
     * CRUD 操作后调用此方法刷新内存缓存。
     */
    public synchronized void refresh() {
        registry.clear();

        List<AgentProfileEntity> dbProfiles = agentProfileRepository.findByEnabledTrueOrderByCreatedAtAsc();

        if (!dbProfiles.isEmpty()) {
            for (AgentProfileEntity entity : dbProfiles) {
                AgentProfile profile = toAgentProfile(entity);
                registry.put(entity.getId(), profile);
            }
            log.info("AgentRegistry refreshed from DB: {} profiles: {}", registry.size(), registry.keySet());
        } else {
            // DB 无数据时，走 YAML fallback（保持向后兼容）
            Map<String, AgentProfile> yamlProfiles = agentsProperties.getProfiles();
            if (yamlProfiles != null && !yamlProfiles.isEmpty()) {
                for (Map.Entry<String, AgentProfile> entry : yamlProfiles.entrySet()) {
                    entry.getValue().setId(entry.getKey());
                    registry.put(entry.getKey(), entry.getValue());
                }
                log.info("AgentRegistry loaded from YAML fallback: {} profiles", registry.size());
            } else {
                AgentProfile defaultProfile = createDefaultProfile();
                registry.put(AgentConstants.DEFAULT_AGENT_ID, defaultProfile);
                log.info("AgentRegistry created default 'main' profile");
            }
        }

        // 验证默认 agent 存在
        String defaultAgent = agentsProperties.getDefaultAgent();
        if (!registry.containsKey(defaultAgent)) {
            log.warn("Default agent '{}' not found in profiles, falling back to first available", defaultAgent);
            if (!registry.isEmpty()) {
                agentsProperties.setDefaultAgent(registry.keySet().iterator().next());
            }
        }
    }

    private AgentProfile toAgentProfile(AgentProfileEntity entity) {
        AgentProfile profile = new AgentProfile();
        profile.setId(entity.getId());
        profile.setSandbox("trusted");
        profile.setWorkspace(entity.getWorkspacePath());
        profile.setCanSpawn(true);
        return profile;
    }

    public Optional<AgentProfile> get(String agentId) {
        return Optional.ofNullable(registry.get(agentId));
    }

    public AgentProfile getOrDefault(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            agentId = agentsProperties.getDefaultAgent();
        }
        return registry.getOrDefault(agentId, registry.get(agentsProperties.getDefaultAgent()));
    }

    public AgentProfile getDefault() {
        return getOrDefault(null);
    }

    public String getDefaultAgentId() {
        return agentsProperties.getDefaultAgent();
    }

    public boolean exists(String agentId) {
        return registry.containsKey(agentId);
    }

    public Set<String> listAgentIds() {
        return Collections.unmodifiableSet(registry.keySet());
    }

    public AgentsProperties.LaneConfig getLaneConfig() {
        return agentsProperties.getLane();
    }

    public boolean isValidAgentId(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return true;
        }
        return registry.containsKey(agentId);
    }

    public boolean canSpawn(String agentId) {
        AgentProfile profile = getOrDefault(agentId);
        return profile != null && profile.isCanSpawn();
    }

    private AgentProfile createDefaultProfile() {
        AgentProfile profile = new AgentProfile();
        profile.setId(AgentConstants.DEFAULT_AGENT_ID);
        profile.setSandbox("trusted");
        profile.setWorkspace("./workspace");
        profile.setAuthDir("./.jaguarclaw/auth/main");
        profile.setCanSpawn(true);
        return profile;
    }
}
