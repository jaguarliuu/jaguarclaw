package com.jaguarliu.ai.gateway.rpc.handler.agent;

import com.jaguarliu.ai.agents.entity.AgentProfileEntity;
import com.jaguarliu.ai.agents.service.AgentProfileService;
import com.jaguarliu.ai.feature.FeatureFlagsProperties;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.gateway.security.ConnectionPrincipal;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Agent Profile RPC Handlers Tests")
class AgentProfileHandlersTest {

    @Mock
    private AgentProfileService service;

    @Mock
    private ConnectionManager connectionManager;

    private FeatureFlagsProperties featureFlags;

    private ConnectionPrincipal principal;

    @BeforeEach
    void setUp() {
        featureFlags = new FeatureFlagsProperties(); // defaults: all enabled
        principal = ConnectionPrincipal.builder()
                .principalId("local-default")
                .roles(List.of("local_admin"))
                .build();
    }

    @Nested
    @DisplayName("agent.list")
    class AgentListTests {
        @Test
        void listShouldReturnAgents() {
            AgentListHandler handler = new AgentListHandler(service, connectionManager, featureFlags);
            when(connectionManager.getPrincipal("conn-1")).thenReturn(principal);
            when(service.list()).thenReturn(List.of(sampleEntity("agent-1", "coder", false)));

            RpcRequest req = RpcRequest.builder().id("1").method("agent.list").build();
            RpcResponse resp = handler.handle("conn-1", req).block();

            assertNotNull(resp);
            assertNull(resp.getError());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> agents = (List<Map<String, Object>>) ((Map<?, ?>) resp.getPayload()).get("agents");
            assertEquals(1, agents.size());
            assertEquals("coder", agents.get(0).get("name"));
        }

        @Test
        void listShouldRequireAuth() {
            AgentListHandler handler = new AgentListHandler(service, connectionManager, featureFlags);
            when(connectionManager.getPrincipal("conn-1")).thenReturn(null);

            RpcResponse resp = handler.handle("conn-1", RpcRequest.builder().id("1").method("agent.list").build()).block();
            assertNotNull(resp);
            assertNotNull(resp.getError());
            assertEquals("UNAUTHORIZED", resp.getError().getCode());
        }
    }

    @Nested
    @DisplayName("agent.get")
    class AgentGetTests {
        @Test
        void getShouldReturnAgent() {
            AgentGetHandler handler = new AgentGetHandler(service, connectionManager, featureFlags);
            when(connectionManager.getPrincipal("conn-1")).thenReturn(principal);
            when(service.get("agent-1")).thenReturn(Optional.of(sampleEntity("agent-1", "coder", false)));

            RpcRequest req = RpcRequest.builder()
                    .id("2")
                    .method("agent.get")
                    .payload(Map.of("agentId", "agent-1"))
                    .build();
            RpcResponse resp = handler.handle("conn-1", req).block();

            assertNotNull(resp);
            assertNull(resp.getError());
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) resp.getPayload();
            assertEquals("agent-1", payload.get("id"));
        }

        @Test
        void getShouldReturnNotFound() {
            AgentGetHandler handler = new AgentGetHandler(service, connectionManager, featureFlags);
            when(connectionManager.getPrincipal("conn-1")).thenReturn(principal);
            when(service.get("missing")).thenReturn(Optional.empty());

            RpcRequest req = RpcRequest.builder()
                    .id("3")
                    .method("agent.get")
                    .payload(Map.of("agentId", "missing"))
                    .build();
            RpcResponse resp = handler.handle("conn-1", req).block();

            assertNotNull(resp);
            assertNotNull(resp.getError());
            assertEquals("NOT_FOUND", resp.getError().getCode());
        }
    }

    @Nested
    @DisplayName("agent.create")
    class AgentCreateTests {
        @Test
        void createShouldSucceed() {
            AgentCreateHandler handler = new AgentCreateHandler(service, connectionManager, featureFlags);
            when(connectionManager.getPrincipal("conn-1")).thenReturn(principal);
            when(service.create(any())).thenReturn(sampleEntity("agent-2", "reviewer", false));

            RpcRequest req = RpcRequest.builder()
                    .id("4")
                    .method("agent.create")
                    .payload(Map.of("name", "reviewer", "displayName", "代码审查员"))
                    .build();
            RpcResponse resp = handler.handle("conn-1", req).block();

            assertNotNull(resp);
            assertNull(resp.getError());
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) resp.getPayload();
            assertEquals("reviewer", payload.get("name"));
        }

        @Test
        void createShouldReturnInvalidParams() {
            AgentCreateHandler handler = new AgentCreateHandler(service, connectionManager, featureFlags);
            when(connectionManager.getPrincipal("conn-1")).thenReturn(principal);
            when(service.create(any())).thenThrow(new IllegalArgumentException("name is required"));

            RpcRequest req = RpcRequest.builder()
                    .id("5")
                    .method("agent.create")
                    .payload(Map.of("displayName", "No Name"))
                    .build();
            RpcResponse resp = handler.handle("conn-1", req).block();

            assertNotNull(resp);
            assertNotNull(resp.getError());
            assertEquals("INVALID_PARAMS", resp.getError().getCode());
        }
    }

    @Nested
    @DisplayName("agent.update")
    class AgentUpdateTests {
        @Test
        void updateShouldSucceed() {
            AgentUpdateHandler handler = new AgentUpdateHandler(service, connectionManager, featureFlags);
            when(connectionManager.getPrincipal("conn-1")).thenReturn(principal);
            when(service.update(any())).thenReturn(sampleEntity("agent-1", "coder", true));

            RpcRequest req = RpcRequest.builder()
                    .id("6")
                    .method("agent.update")
                    .payload(Map.of("agentId", "agent-1", "isDefault", true))
                    .build();
            RpcResponse resp = handler.handle("conn-1", req).block();

            assertNotNull(resp);
            assertNull(resp.getError());
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) resp.getPayload();
            assertEquals(true, payload.get("isDefault"));
        }

        @Test
        void updateShouldReturnInvalidStateForDefaultConstraint() {
            AgentUpdateHandler handler = new AgentUpdateHandler(service, connectionManager, featureFlags);
            when(connectionManager.getPrincipal("conn-1")).thenReturn(principal);
            when(service.update(any())).thenThrow(new IllegalStateException(
                    "Cannot unset default agent directly. Set another default agent first."));

            RpcRequest req = RpcRequest.builder()
                    .id("7")
                    .method("agent.update")
                    .payload(Map.of("agentId", "agent-1", "isDefault", false))
                    .build();
            RpcResponse resp = handler.handle("conn-1", req).block();

            assertNotNull(resp);
            assertNotNull(resp.getError());
            assertEquals("INVALID_STATE", resp.getError().getCode());
        }
    }

    @Nested
    @DisplayName("agent.delete")
    class AgentDeleteTests {
        @Test
        void deleteShouldSucceed() {
            AgentDeleteHandler handler = new AgentDeleteHandler(service, connectionManager, featureFlags);
            when(connectionManager.getPrincipal("conn-1")).thenReturn(principal);

            RpcRequest req = RpcRequest.builder()
                    .id("8")
                    .method("agent.delete")
                    .payload(Map.of("agentId", "agent-2"))
                    .build();
            RpcResponse resp = handler.handle("conn-1", req).block();

            assertNotNull(resp);
            assertNull(resp.getError());
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) resp.getPayload();
            assertEquals(true, payload.get("deleted"));
        }

        @Test
        void deleteDefaultAgentShouldReturnInvalidState() {
            AgentDeleteHandler handler = new AgentDeleteHandler(service, connectionManager, featureFlags);
            when(connectionManager.getPrincipal("conn-1")).thenReturn(principal);
            doThrow(new IllegalStateException("Cannot delete default agent")).when(service).delete("main");

            RpcRequest req = RpcRequest.builder()
                    .id("9")
                    .method("agent.delete")
                    .payload(Map.of("agentId", "main"))
                    .build();
            RpcResponse resp = handler.handle("conn-1", req).block();

            assertNotNull(resp);
            assertNotNull(resp.getError());
            assertEquals("INVALID_STATE", resp.getError().getCode());
        }
    }

    private AgentProfileEntity sampleEntity(String id, String name, boolean isDefault) {
        LocalDateTime now = LocalDateTime.now();
        return AgentProfileEntity.builder()
                .id(id)
                .name(name)
                .displayName(name)
                .description("")
                .workspacePath("workspace/workspace-" + name)
                .model("deepseek-chat")
                .enabled(true)
                .isDefault(isDefault)
                .allowedTools("[]")
                .excludedTools("[]")
                .heartbeatInterval(30)
                .heartbeatActiveHours("09:00-22:00")
                .dailyTokenLimit(0)
                .monthlyCostLimit(0.0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
