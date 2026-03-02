package com.jaguarliu.ai.agents.service;

import com.jaguarliu.ai.heartbeat.HeartbeatConfigService;
import com.jaguarliu.ai.soul.SoulConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时确保控制面存在默认 Agent，并为所有已存在的 Agent 初始化 soul 和 heartbeat 文件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentProfileBootstrapInitializer implements ApplicationRunner {

    private final AgentProfileService agentProfileService;
    private final SoulConfigService soulConfigService;
    private final HeartbeatConfigService heartbeatConfigService;

    @Override
    public void run(ApplicationArguments args) {
        agentProfileService.ensureDefaultMainAgentExists();

        // 确保所有已有 Agent 都有 soul 和 heartbeat 文件（兼容旧版本升级场景）
        var agents = agentProfileService.list();
        agents.forEach(agent -> {
            soulConfigService.ensureAgentDefaults(agent.getId(), agent.getDisplayName());
            heartbeatConfigService.ensureAgentDefaults(agent.getId());
        });
        log.info("Agent workspace defaults ensured for {} agent(s)", agents.size());
    }
}
