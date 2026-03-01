package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.agents.entity.AgentProfileEntity;
import com.jaguarliu.ai.agents.service.AgentProfileService;
import com.jaguarliu.ai.feature.FeatureFlagsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Chat Router Mention Tests")
class ChatRouterMentionTest {

    @Mock
    private AgentProfileService agentProfileService;

    private ChatRouter chatRouter;

    @BeforeEach
    void setUp() {
        chatRouter = new ChatRouter(agentProfileService, new FeatureFlagsProperties());
    }

    @Test
    @DisplayName("@coder xxx 能正确解析并路由")
    void shouldRouteToMentionedAgentWhenMentionIsValid() {
        when(agentProfileService.get("coder"))
                .thenReturn(Optional.of(agent("coder", true, false)));

        ChatRouter.RouteDecision decision = chatRouter.route("@coder fix the failing test", null, "main");

        assertEquals("coder", decision.agentId());
        assertEquals("fix the failing test", decision.prompt());
        assertEquals("coder", decision.mentionedAgentId());
        assertTrue(decision.mentionResolved());
    }

    @Test
    @DisplayName("无效 mention 回退默认 agent")
    void shouldFallbackToDefaultAgentWhenMentionIsInvalid() {
        when(agentProfileService.get("ghost")).thenReturn(Optional.empty());
        when(agentProfileService.list()).thenReturn(List.of(
                agent("main", true, true),
                agent("coder", true, false)
        ));

        ChatRouter.RouteDecision decision = chatRouter.route("@ghost summarize this", null, "coder");

        assertEquals("main", decision.agentId());
        assertEquals("summarize this", decision.prompt());
        assertEquals("ghost", decision.mentionedAgentId());
        assertFalse(decision.mentionResolved());
    }

    @Test
    @DisplayName("无 mention 时保持原有优先级：payload > session > default")
    void shouldKeepExistingPriorityWithoutMention() {
        when(agentProfileService.list()).thenReturn(List.of(agent("main", true, true)));

        ChatRouter.RouteDecision byPayload = chatRouter.route("hello", "reviewer", "coder");
        ChatRouter.RouteDecision bySession = chatRouter.route("hello", null, "coder");
        ChatRouter.RouteDecision byDefault = chatRouter.route("hello", null, null);

        assertEquals("reviewer", byPayload.agentId());
        assertEquals("coder", bySession.agentId());
        assertEquals("main", byDefault.agentId());
    }

    private AgentProfileEntity agent(String id, boolean enabled, boolean isDefault) {
        return AgentProfileEntity.builder()
                .id(id)
                .name(id)
                .displayName(id)
                .workspacePath("workspace/agents/" + id)
                .enabled(enabled)
                .isDefault(isDefault)
                .build();
    }
}
