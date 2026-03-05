package com.jaguarliu.ai.gateway.rpc.handler.agent;

import com.jaguarliu.ai.gateway.events.EventBus;
import com.jaguarliu.ai.gateway.rpc.handler.session.SessionCreateHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.gateway.security.ConnectionPrincipal;
import com.jaguarliu.ai.gateway.security.rate.TokenBudgetService;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import com.jaguarliu.ai.llm.LlmClient;
import com.jaguarliu.ai.nodeconsole.AuditLogService;
import com.jaguarliu.ai.runtime.AgentRuntime;
import com.jaguarliu.ai.runtime.CancellationManager;
import com.jaguarliu.ai.runtime.ChatRouter;
import com.jaguarliu.ai.runtime.ContextBuilder;
import com.jaguarliu.ai.runtime.LoopConfig;
import com.jaguarliu.ai.runtime.RunContext;
import com.jaguarliu.ai.runtime.SessionLaneManager;
import com.jaguarliu.ai.runtime.strategy.AgentExecutionPlan;
import com.jaguarliu.ai.runtime.strategy.AgentStrategy;
import com.jaguarliu.ai.runtime.strategy.AgentStrategyResolver;
import com.jaguarliu.ai.session.MessageService;
import com.jaguarliu.ai.session.RunService;
import com.jaguarliu.ai.session.SessionService;
import com.jaguarliu.ai.storage.entity.RunEntity;
import com.jaguarliu.ai.storage.entity.SessionEntity;
import com.jaguarliu.ai.tools.ToolConfigProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Agent Run With AgentId Tests")
class AgentRunWithAgentIdTest {

    private static final String CONNECTION_ID = "conn-1";
    private static final String PRINCIPAL_ID = "local-default";

    @Mock
    private SessionService sessionService;
    @Mock
    private RunService runService;
    @Mock
    private MessageService messageService;
    @Mock
    private SessionLaneManager sessionLaneManager;
    @Mock
    private EventBus eventBus;
    @Mock
    private ContextBuilder contextBuilder;
    @Mock
    private AgentRuntime agentRuntime;
    @Mock
    private LlmClient llmClient;
    @Mock
    private ToolConfigProperties toolConfigProperties;
    @Mock
    private AgentStrategyResolver strategyResolver;
    @Mock
    private CancellationManager cancellationManager;
    @Mock
    private ChatRouter chatRouter;
    @Mock
    private ConnectionManager connectionManager;
    @Mock
    private TokenBudgetService tokenBudgetService;
    @Mock
    private AuditLogService auditLogService;

    private ConnectionPrincipal principal;
    private LoopConfig loopConfig;

    @BeforeEach
    void setUp() {
        principal = ConnectionPrincipal.builder()
                .principalId(PRINCIPAL_ID)
                .roles(List.of("local_admin"))
                .build();
        loopConfig = new LoopConfig();
    }

    @Nested
    @DisplayName("session.create")
    class SessionCreateTests {
        @Test
        void sessionCreateShouldUsePayloadAgentId() {
            SessionCreateHandler handler = new SessionCreateHandler(sessionService, connectionManager);
            when(connectionManager.getPrincipal(CONNECTION_ID)).thenReturn(principal);
            when(sessionService.create("Architecture Chat", "reviewer", PRINCIPAL_ID))
                    .thenReturn(sessionEntity("s-1", "Architecture Chat", "reviewer"));

            RpcRequest request = RpcRequest.builder()
                    .id("1")
                    .method("session.create")
                    .payload(Map.of("name", "Architecture Chat", "agentId", "reviewer"))
                    .build();
            RpcResponse response = handler.handle(CONNECTION_ID, request).block();

            assertNotNull(response);
            assertNull(response.getError());
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) response.getPayload();
            assertEquals("reviewer", payload.get("agentId"));
            verify(sessionService).create("Architecture Chat", "reviewer", PRINCIPAL_ID);
        }
    }

    @Nested
    @DisplayName("agent.run")
    class AgentRunTests {

        @Test
        void agentRunShouldUseSessionBoundAgentId() {
            AgentRunHandler handler = newAgentRunHandler();
            mockCommonRunPrerequisites();

            when(sessionService.get("session-1", PRINCIPAL_ID))
                    .thenReturn(Optional.of(sessionEntity("session-1", "Chat", "coder")));
            when(sessionLaneManager.nextSequence("session-1")).thenReturn(1L);
            when(runService.create("session-1", "hello", "coder", PRINCIPAL_ID))
                    .thenReturn(runEntity("run-1", "session-1", "coder", "hello"));
            when(sessionLaneManager.submit(eq("session-1"), eq("run-1"), eq(1L), any(Supplier.class)))
                    .thenReturn(Mono.empty());

            RpcRequest request = RpcRequest.builder()
                    .id("2")
                    .method("agent.run")
                    .payload(Map.of("sessionId", "session-1", "prompt", "hello"))
                    .build();
            RpcResponse response = handler.handle(CONNECTION_ID, request).block();

            assertNotNull(response);
            assertNull(response.getError());
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) response.getPayload();
            assertEquals("coder", payload.get("agentId"));
            verify(runService).create("session-1", "hello", "coder", PRINCIPAL_ID);
        }

        @Test
        void agentRunPayloadAgentIdShouldOverrideSessionBinding() {
            AgentRunHandler handler = newAgentRunHandler();
            mockCommonRunPrerequisites();

            when(sessionService.get("session-2", PRINCIPAL_ID))
                    .thenReturn(Optional.of(sessionEntity("session-2", "Chat", "coder")));
            when(sessionLaneManager.nextSequence("session-2")).thenReturn(2L);
            when(runService.create("session-2", "hello", "reviewer", PRINCIPAL_ID))
                    .thenReturn(runEntity("run-2", "session-2", "reviewer", "hello"));
            when(sessionLaneManager.submit(eq("session-2"), eq("run-2"), eq(2L), any(Supplier.class)))
                    .thenReturn(Mono.empty());

            RpcRequest request = RpcRequest.builder()
                    .id("3")
                    .method("agent.run")
                    .payload(Map.of("sessionId", "session-2", "prompt", "hello", "agentId", "reviewer"))
                    .build();
            RpcResponse response = handler.handle(CONNECTION_ID, request).block();

            assertNotNull(response);
            assertNull(response.getError());
            verify(runService).create("session-2", "hello", "reviewer", PRINCIPAL_ID);
        }

        @Test
        void agentRunWithoutAgentIdShouldFallbackToMain() {
            AgentRunHandler handler = newAgentRunHandler();
            mockCommonRunPrerequisites();

            when(sessionService.create("New Session", "main", PRINCIPAL_ID))
                    .thenReturn(sessionEntity("session-new", "New Session", "main"));
            when(sessionLaneManager.nextSequence("session-new")).thenReturn(3L);
            when(runService.create("session-new", "hello", "main", PRINCIPAL_ID))
                    .thenReturn(runEntity("run-3", "session-new", "main", "hello"));
            when(sessionLaneManager.submit(eq("session-new"), eq("run-3"), eq(3L), any(Supplier.class)))
                    .thenReturn(Mono.empty());

            RpcRequest request = RpcRequest.builder()
                    .id("4")
                    .method("agent.run")
                    .payload(Map.of("prompt", "hello"))
                    .build();
            RpcResponse response = handler.handle(CONNECTION_ID, request).block();

            assertNotNull(response);
            assertNull(response.getError());
            verify(sessionService).create("New Session", "main", PRINCIPAL_ID);
            verify(runService).create("session-new", "hello", "main", PRINCIPAL_ID);
        }

        @Test
        void runContextShouldCarryResolvedAgentId() throws Exception {
            AgentRunHandler handler = newAgentRunHandler();
            mockCommonRunPrerequisites();

            when(sessionService.get("session-ctx", PRINCIPAL_ID))
                    .thenReturn(Optional.of(sessionEntity("session-ctx", "Scoped Chat", "coder")));
            when(sessionLaneManager.nextSequence("session-ctx")).thenReturn(10L);
            when(runService.create("session-ctx", "hello", "coder", PRINCIPAL_ID))
                    .thenReturn(runEntity("run-ctx", "session-ctx", "coder", "hello"));

            AgentStrategy strategy = org.mockito.Mockito.mock(AgentStrategy.class);
            when(strategyResolver.resolve(any())).thenReturn(strategy);
            when(strategy.prepare(any())).thenReturn(AgentExecutionPlan.builder()
                    .systemPrompt("system prompt")
                    .strategyName("default")
                    .build());
            when(messageService.getSessionHistory(eq("session-ctx"), anyInt(), eq(PRINCIPAL_ID)))
                    .thenReturn(List.of());
            when(agentRuntime.executeLoopWithContext(any(RunContext.class), anyList(), eq("hello")))
                    .thenReturn("answer");
            when(sessionLaneManager.submit(eq("session-ctx"), eq("run-ctx"), eq(10L), any(Supplier.class)))
                    .thenAnswer(invocation -> {
                        @SuppressWarnings("unchecked")
                        Supplier<Object> task = (Supplier<Object>) invocation.getArgument(3);
                        task.get();
                        return Mono.empty();
                    });

            RpcRequest request = RpcRequest.builder()
                    .id("5")
                    .method("agent.run")
                    .payload(Map.of("sessionId", "session-ctx", "prompt", "hello"))
                    .build();
            RpcResponse response = handler.handle(CONNECTION_ID, request).block();

            assertNotNull(response);
            assertNull(response.getError());

            ArgumentCaptor<RunContext> contextCaptor = ArgumentCaptor.forClass(RunContext.class);
            verify(agentRuntime).executeLoopWithContext(contextCaptor.capture(), anyList(), eq("hello"));
            assertEquals("coder", contextCaptor.getValue().getAgentId());
        }

        @Test
        void cancelledRunShouldPersistAssistantCancellationMessage() throws Exception {
            AgentRunHandler handler = newAgentRunHandler();
            mockCommonRunPrerequisites();

            when(sessionService.get("session-cancel", PRINCIPAL_ID))
                    .thenReturn(Optional.of(sessionEntity("session-cancel", "Cancel Chat", "coder")));
            when(sessionLaneManager.nextSequence("session-cancel")).thenReturn(11L);
            when(runService.create("session-cancel", "hello", "coder", PRINCIPAL_ID))
                    .thenReturn(runEntity("run-cancel", "session-cancel", "coder", "hello"));

            AgentStrategy strategy = org.mockito.Mockito.mock(AgentStrategy.class);
            when(strategyResolver.resolve(any())).thenReturn(strategy);
            when(strategy.prepare(any())).thenReturn(AgentExecutionPlan.builder()
                    .systemPrompt("system prompt")
                    .strategyName("default")
                    .build());
            when(messageService.getSessionHistory(eq("session-cancel"), anyInt(), eq(PRINCIPAL_ID)))
                    .thenReturn(List.of());
            when(agentRuntime.executeLoopWithContext(any(RunContext.class), anyList(), eq("hello")))
                    .thenThrow(new CancellationException("Run cancelled by user"));
            when(sessionLaneManager.submit(eq("session-cancel"), eq("run-cancel"), eq(11L), any(Supplier.class)))
                    .thenAnswer(invocation -> {
                        @SuppressWarnings("unchecked")
                        Supplier<Object> task = (Supplier<Object>) invocation.getArgument(3);
                        task.get();
                        return Mono.empty();
                    });

            RpcRequest request = RpcRequest.builder()
                    .id("6")
                    .method("agent.run")
                    .payload(Map.of("sessionId", "session-cancel", "prompt", "hello"))
                    .build();
            RpcResponse response = handler.handle(CONNECTION_ID, request).block();

            assertNotNull(response);
            assertNull(response.getError());
            verify(messageService).saveAssistantMessage(
                    eq("session-cancel"),
                    eq("run-cancel"),
                    contains("Cancelled by user"),
                    eq(PRINCIPAL_ID)
            );
        }
    }

    private AgentRunHandler newAgentRunHandler() {
        return new AgentRunHandler(
                sessionService,
                runService,
                messageService,
                sessionLaneManager,
                eventBus,
                contextBuilder,
                agentRuntime,
                llmClient,
                toolConfigProperties,
                strategyResolver,
                loopConfig,
                cancellationManager,
                chatRouter,
                connectionManager,
                tokenBudgetService,
                auditLogService
        );
    }

    private void mockCommonRunPrerequisites() {
        when(connectionManager.getPrincipal(CONNECTION_ID)).thenReturn(principal);
        when(tokenBudgetService.estimateTokens(anyString())).thenReturn(10);
        when(tokenBudgetService.tryConsume(eq(PRINCIPAL_ID), anyInt())).thenReturn(true);
        when(chatRouter.route(anyString(), nullable(String.class), nullable(String.class)))
                .thenAnswer(invocation -> {
                    String prompt = invocation.getArgument(0);
                    String requestedAgentId = invocation.getArgument(1);
                    String sessionAgentId = invocation.getArgument(2);
                    String resolvedAgentId = requestedAgentId != null && !requestedAgentId.isBlank()
                            ? requestedAgentId
                            : sessionAgentId != null && !sessionAgentId.isBlank() ? sessionAgentId : "main";
                    return new ChatRouter.RouteDecision(resolvedAgentId, prompt, null, false);
                });
    }

    private SessionEntity sessionEntity(String id, String name, String agentId) {
        LocalDateTime now = LocalDateTime.now();
        return SessionEntity.builder()
                .id(id)
                .name(name)
                .agentId(agentId)
                .sessionKind("main")
                .ownerPrincipalId(PRINCIPAL_ID)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private RunEntity runEntity(String id, String sessionId, String agentId, String prompt) {
        LocalDateTime now = LocalDateTime.now();
        return RunEntity.builder()
                .id(id)
                .sessionId(sessionId)
                .status("queued")
                .agentId(agentId)
                .runKind("main")
                .lane("main")
                .deliver(false)
                .prompt(prompt)
                .ownerPrincipalId(PRINCIPAL_ID)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
