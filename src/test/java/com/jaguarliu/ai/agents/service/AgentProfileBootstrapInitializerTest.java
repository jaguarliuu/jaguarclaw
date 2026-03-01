package com.jaguarliu.ai.agents.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("AgentProfileBootstrapInitializer Tests")
class AgentProfileBootstrapInitializerTest {

    @Test
    @DisplayName("startup should ensure default main agent exists")
    void runShouldEnsureDefaultMainAgentExists() throws Exception {
        AgentProfileService service = mock(AgentProfileService.class);
        AgentProfileBootstrapInitializer initializer = new AgentProfileBootstrapInitializer(service);

        initializer.run(null);

        verify(service, times(1)).ensureDefaultMainAgentExists();
    }
}
