package com.jaguarliu.ai.agents.service;

import com.jaguarliu.ai.heartbeat.HeartbeatConfigService;
import com.jaguarliu.ai.soul.SoulConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AgentProfileBootstrapInitializer Tests")
class AgentProfileBootstrapInitializerTest {

    @Test
    @DisplayName("startup should ensure default main agent exists and init all agent defaults")
    void runShouldEnsureDefaultMainAgentExists() throws Exception {
        AgentProfileService service = mock(AgentProfileService.class);
        SoulConfigService soulConfigService = mock(SoulConfigService.class);
        HeartbeatConfigService heartbeatConfigService = mock(HeartbeatConfigService.class);
        when(service.list()).thenReturn(Collections.emptyList());

        AgentProfileBootstrapInitializer initializer = new AgentProfileBootstrapInitializer(
                service, soulConfigService, heartbeatConfigService);

        initializer.run(null);

        verify(service, times(1)).ensureDefaultMainAgentExists();
        verify(service, times(1)).list();
    }
}
