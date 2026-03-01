package com.jaguarliu.ai.agents.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时确保控制面存在默认 Agent。
 */
@Component
@RequiredArgsConstructor
public class AgentProfileBootstrapInitializer implements ApplicationRunner {

    private final AgentProfileService agentProfileService;

    @Override
    public void run(ApplicationArguments args) {
        agentProfileService.ensureDefaultMainAgentExists();
    }
}
