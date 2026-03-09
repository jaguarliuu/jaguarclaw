package com.jaguarliu.ai.gateway.rpc.handler.agent;

import com.jaguarliu.ai.agents.context.AgentWorkspaceResolver;
import com.jaguarliu.ai.gateway.events.AgentEvent;
import com.jaguarliu.ai.gateway.events.EventBus;
import com.jaguarliu.ai.gateway.rpc.handler.session.SessionCreateHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.gateway.security.ConnectionPrincipal;
import com.jaguarliu.ai.gateway.security.rate.TokenBudgetService;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import com.jaguarliu.ai.llm.LlmCapabilityService;
import com.jaguarliu.ai.llm.LlmClient;
import com.jaguarliu.ai.llm.LlmProperties;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.llm.model.LlmResponse;
import com.jaguarliu.ai.llm.model.LlmChunk;
import com.jaguarliu.ai.nodeconsole.AuditLogService;
import com.jaguarliu.ai.runtime.AgentRuntime;
import com.jaguarliu.ai.runtime.CancellationManager;
import com.jaguarliu.ai.runtime.ChatRouter;
import com.jaguarliu.ai.runtime.ContextBuilder;
import com.jaguarliu.ai.runtime.LoopConfig;
import com.jaguarliu.ai.runtime.RunContext;
import com.jaguarliu.ai.runtime.RunOutcome;
import com.jaguarliu.ai.runtime.RunOutcomeStatus;
import com.jaguarliu.ai.runtime.SessionLaneManager;
import com.jaguarliu.ai.runtime.TaskComplexity;
import com.jaguarliu.ai.runtime.TaskRouteMode;
import com.jaguarliu.ai.runtime.TaskRouter;
import com.jaguarliu.ai.runtime.TaskRoutingDecision;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
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
    private AgentWorkspaceResolver agentWorkspaceResolver;
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

    private LlmProperties llmProperties;
    @Mock
    private LlmCapabilityService llmCapabilityService;
    @Mock
    private ToolConfigProperties toolConfigProperties;
    @Mock
    private AgentStrategyResolver strategyResolver;
    @Mock
    private CancellationManager cancellationManager;
    @Mock
    private ChatRouter chatRouter;
    @Mock
    private TaskRouter taskRouter;
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
        llmProperties = new LlmProperties();
        llmProperties.setDefaultModel("default:default-chat");
        llmProperties.setProviders(List.of(
                com.jaguarliu.ai.llm.model.LlmProviderConfig.builder()
                        .id("default")
                        .name("Default")
                        .models(List.of("default-chat"))
                        .build(),
                com.jaguarliu.ai.llm.model.LlmProviderConfig.builder()
                        .id("dashscope")
                        .name("DashScope")
                        .models(List.of("qwen-plus"))
                        .visionModels(List.of("qwen-image-2.0-pro", "qwen3.5-plus"))
                        .build()
        ));
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
        void agentRunShouldRejectImageAttachmentsForNonVisionModel() {
            AgentRunHandler handler = newAgentRunHandler();
            mockCommonRunPrerequisites();
            when(llmCapabilityService.supportsVision(eq("openai:gpt-3.5-turbo"))).thenReturn(false);

            RpcRequest request = RpcRequest.builder()
                    .id("mm-0")
                    .method("agent.run")
                    .payload(Map.of(
                            "sessionId", "session-mm",
                            "prompt", "describe this image",
                            "model", "openai:gpt-3.5-turbo",
                            "attachments", List.of(Map.of(
                                    "type", "image",
                                    "filePath", "uploads/demo.png",
                                    "filename", "demo.png",
                                    "mimeType", "image/png"
                            ))
                    ))
                    .build();

            RpcResponse response = handler.handle(CONNECTION_ID, request).block();

            assertNotNull(response);
            assertNotNull(response.getError());
            assertEquals("MODEL_NOT_SUPPORTED", response.getError().getCode());
        }

        @Test
        void agentRunShouldBuildMultimodalUserMessageWhenImageAttachmentsPresent() throws Exception {
            AgentRunHandler handler = newAgentRunHandler();
            mockCommonRunPrerequisites();

            when(sessionService.get("session-mm", PRINCIPAL_ID))
                    .thenReturn(Optional.of(sessionEntity("session-mm", "Vision Chat", "coder")));
            when(sessionLaneManager.nextSequence("session-mm")).thenReturn(9L);
            when(runService.create("session-mm", "describe this image", "coder", PRINCIPAL_ID))
                    .thenReturn(runEntity("run-mm", "session-mm", "coder", "describe this image"));

            AgentStrategy strategy = org.mockito.Mockito.mock(AgentStrategy.class);
            when(strategyResolver.resolve(any())).thenReturn(strategy);
            when(strategy.prepare(any())).thenReturn(AgentExecutionPlan.builder()
                    .systemPrompt("system prompt")
                    .strategyName("default")
                    .build());
            when(messageService.getSessionHistory(eq("session-mm"), anyInt(), eq(PRINCIPAL_ID)))
                    .thenReturn(List.of());
            when(agentRuntime.executeLoopWithContext(any(RunContext.class), anyList()))
                    .thenReturn("answer");
            when(sessionLaneManager.submit(eq("session-mm"), eq("run-mm"), eq(9L), any(Supplier.class)))
                    .thenAnswer(invocation -> {
                        @SuppressWarnings("unchecked")
                        Supplier<Object> task = (Supplier<Object>) invocation.getArgument(3);
                        task.get();
                        return Mono.empty();
                    });

            RpcRequest request = RpcRequest.builder()
                    .id("mm-1")
                    .method("agent.run")
                    .payload(Map.of(
                            "sessionId", "session-mm",
                            "prompt", "describe this image",
                            "attachments", List.of(Map.of(
                                    "type", "image",
                                    "filePath", "uploads/demo.png",
                                    "filename", "demo.png",
                                    "mimeType", "image/png"
                            ))
                    ))
                    .build();
            RpcResponse response = handler.handle(CONNECTION_ID, request).block();

            assertNotNull(response);
            assertNull(response.getError());

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<com.jaguarliu.ai.llm.model.LlmRequest.Message>> messagesCaptor = ArgumentCaptor.forClass(List.class);
            verify(agentRuntime).executeLoopWithContext(any(RunContext.class), messagesCaptor.capture());

            List<com.jaguarliu.ai.llm.model.LlmRequest.Message> messages = messagesCaptor.getValue();
            com.jaguarliu.ai.llm.model.LlmRequest.Message userMessage = messages.get(messages.size() - 1);
            assertEquals("describe this image", userMessage.getContent());
            assertNotNull(userMessage.getParts());
            assertEquals(2, userMessage.getParts().size());
            assertEquals("image", userMessage.getParts().get(0).getType());
            assertEquals("uploads/demo.png", userMessage.getParts().get(0).getImage().getFilePath());
            assertEquals("text", userMessage.getParts().get(1).getType());
            assertEquals("describe this image", userMessage.getParts().get(1).getText());
        }

        @Test
        void generateTitleShouldFallbackToChatModelWhenVisionModelSelected() throws Exception {
            AgentRunHandler handler = newAgentRunHandler();
            when(llmClient.chat(any())).thenReturn(com.jaguarliu.ai.llm.model.LlmResponse.builder().content("猫咪识图").build());

            Method method = AgentRunHandler.class.getDeclaredMethod("generateTitle", String.class, String.class, String.class);
            method.setAccessible(true);
            String title = (String) method.invoke(handler, "帮我看图", "这是一只猫", "dashscope:qwen-image-2.0-pro");

            assertEquals("猫咪识图", title);
            ArgumentCaptor<com.jaguarliu.ai.llm.model.LlmRequest> requestCaptor = ArgumentCaptor.forClass(com.jaguarliu.ai.llm.model.LlmRequest.class);
            verify(llmClient).chat(requestCaptor.capture());
            assertEquals("dashscope", requestCaptor.getValue().getProviderId());
            assertEquals("qwen-plus", requestCaptor.getValue().getModel());
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
            when(agentRuntime.executeLoopWithContext(any(RunContext.class), anyList()))
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
            verify(agentRuntime).executeLoopWithContext(contextCaptor.capture(), anyList());
            assertEquals("coder", contextCaptor.getValue().getAgentId());
        }

        @Test
        void chatRouteShouldBypassStrategyAndRuntime() throws Exception {
            AgentRunHandler handler = newAgentRunHandler();
            mockCommonRunPrerequisites();

            when(sessionService.get("session-chat", PRINCIPAL_ID))
                    .thenReturn(Optional.of(sessionEntity("session-chat", "Chat", "coder")));
            when(sessionLaneManager.nextSequence("session-chat")).thenReturn(12L);
            when(runService.create("session-chat", "hi", "coder", PRINCIPAL_ID))
                    .thenReturn(runEntity("run-chat", "session-chat", "coder", "hi"));
            when(messageService.getSessionHistory(eq("session-chat"), anyInt(), eq(PRINCIPAL_ID)))
                    .thenReturn(List.of());
            when(taskRouter.route(eq("hi"), anyList(), eq(false), nullable(String.class)))
                    .thenReturn(TaskRoutingDecision.builder()
                            .routeMode(TaskRouteMode.CHAT)
                            .complexity(TaskComplexity.DIRECT)
                            .shouldUseTools(false)
                            .shouldUseStrategy(false)
                            .reason("greeting")
                            .build());
            when(contextBuilder.buildForPolicyDecision(anyList(), eq("hi"), eq(false), eq(TaskComplexity.DIRECT), eq("coder")))
                    .thenReturn(new ContextBuilder.SkillAwareRequest(
                            LlmRequest.builder().messages(List.of(LlmRequest.Message.system("minimal"), LlmRequest.Message.user("hi"))).build(),
                            null, null, null, null
                    ));
            when(llmClient.stream(any())).thenReturn(Flux.just(
                    LlmChunk.builder().delta("hello ").build(),
                    LlmChunk.builder().delta("there").usage(LlmResponse.Usage.builder()
                            .promptTokens(3)
                            .completionTokens(2)
                            .totalTokens(5)
                            .build()).done(true).build()
            ));
            when(sessionLaneManager.submit(eq("session-chat"), eq("run-chat"), eq(12L), any(Supplier.class)))
                    .thenAnswer(invocation -> {
                        @SuppressWarnings("unchecked")
                        Supplier<Object> task = (Supplier<Object>) invocation.getArgument(3);
                        task.get();
                        return Mono.empty();
                    });

            RpcResponse response = handler.handle(CONNECTION_ID, RpcRequest.builder()
                    .id("7")
                    .method("agent.run")
                    .payload(Map.of("sessionId", "session-chat", "prompt", "hi"))
                    .build()).block();

            assertNotNull(response);
            assertNull(response.getError());
            verify(strategyResolver, org.mockito.Mockito.never()).resolve(any());
            verify(agentRuntime, org.mockito.Mockito.never()).executeLoopWithContext(any(RunContext.class), anyList(), anyString());
            verify(llmClient).stream(any());
            verify(messageService).saveAssistantMessage("session-chat", "run-chat", "hello there", PRINCIPAL_ID);

            ArgumentCaptor<AgentEvent> eventCaptor = ArgumentCaptor.forClass(AgentEvent.class);
            verify(eventBus, org.mockito.Mockito.atLeast(4)).publish(eventCaptor.capture());
            List<AgentEvent> assistantDeltas = eventCaptor.getAllValues().stream()
                    .filter(event -> event.getType() == AgentEvent.EventType.ASSISTANT_DELTA)
                    .toList();
            assertEquals(2, assistantDeltas.size());
            assertTrue(assistantDeltas.get(0).getData() instanceof AgentEvent.DeltaData first
                    && "hello ".equals(first.getContent()));
            assertTrue(assistantDeltas.get(1).getData() instanceof AgentEvent.DeltaData second
                    && "there".equals(second.getContent()));
            assertTrue(eventCaptor.getAllValues().stream().anyMatch(event ->
                    event.getType() == AgentEvent.EventType.TOKEN_USAGE));
        }

        @Test
        void lightRouteShouldUseLightRuntimePathWithoutStrategyPlan() throws Exception {
            AgentRunHandler handler = newAgentRunHandler();
            mockCommonRunPrerequisites();

            when(sessionService.get("session-light", PRINCIPAL_ID))
                    .thenReturn(Optional.of(sessionEntity("session-light", "Light", "coder")));
            when(sessionLaneManager.nextSequence("session-light")).thenReturn(13L);
            when(runService.create("session-light", "read this file", "coder", PRINCIPAL_ID))
                    .thenReturn(runEntity("run-light", "session-light", "coder", "read this file"));
            when(messageService.getSessionHistory(eq("session-light"), anyInt(), eq(PRINCIPAL_ID)))
                    .thenReturn(List.of());
            when(taskRouter.route(eq("read this file"), anyList(), eq(false), nullable(String.class)))
                    .thenReturn(TaskRoutingDecision.builder()
                            .routeMode(TaskRouteMode.LIGHT)
                            .complexity(TaskComplexity.LIGHT)
                            .shouldUseTools(true)
                            .shouldUseStrategy(false)
                            .reason("small task")
                            .build());
            when(contextBuilder.buildForPolicyDecision(anyList(), eq("read this file"), eq(true), eq(TaskComplexity.LIGHT), eq("coder")))
                    .thenReturn(new ContextBuilder.SkillAwareRequest(
                            LlmRequest.builder().messages(List.of(LlmRequest.Message.system("minimal"), LlmRequest.Message.user("read this file"))).build(),
                            null, null, null, null
                    ));
            when(agentRuntime.executeLoopWithContext(any(RunContext.class), anyList()))
                    .thenReturn("light result");
            when(sessionLaneManager.submit(eq("session-light"), eq("run-light"), eq(13L), any(Supplier.class)))
                    .thenAnswer(invocation -> {
                        @SuppressWarnings("unchecked")
                        Supplier<Object> task = (Supplier<Object>) invocation.getArgument(3);
                        task.get();
                        return Mono.empty();
                    });

            RpcResponse response = handler.handle(CONNECTION_ID, RpcRequest.builder()
                    .id("8")
                    .method("agent.run")
                    .payload(Map.of("sessionId", "session-light", "prompt", "read this file"))
                    .build()).block();

            assertNotNull(response);
            assertNull(response.getError());
            verify(strategyResolver, org.mockito.Mockito.never()).resolve(any());
            verify(agentRuntime).executeLoopWithContext(any(RunContext.class), anyList());
        }

        @Test
        void heavyRouteShouldPersistLatestDraftInsteadOfOutcomeSummary() throws Exception {
            AgentRunHandler handler = newAgentRunHandler();
            mockCommonRunPrerequisites();

            when(sessionService.get("session-heavy", PRINCIPAL_ID))
                    .thenReturn(Optional.of(sessionEntity("session-heavy", "Heavy Chat", "coder")));
            when(sessionLaneManager.nextSequence("session-heavy")).thenReturn(14L);
            when(runService.create("session-heavy", "summarize today's AI news", "coder", PRINCIPAL_ID))
                    .thenReturn(runEntity("run-heavy", "session-heavy", "coder", "summarize today's AI news"));

            AgentStrategy strategy = org.mockito.Mockito.mock(AgentStrategy.class);
            when(strategyResolver.resolve(any())).thenReturn(strategy);
            when(strategy.prepare(any())).thenReturn(AgentExecutionPlan.builder()
                    .systemPrompt("system prompt")
                    .strategyName("default")
                    .build());
            when(messageService.getSessionHistory(eq("session-heavy"), anyInt(), eq(PRINCIPAL_ID)))
                    .thenReturn(List.of());
            when(agentRuntime.executeLoopWithContext(any(RunContext.class), anyList()))
                    .thenAnswer(invocation -> {
                        RunContext context = invocation.getArgument(0);
                        context.setLatestAssistantDraft("Full streamed answer about today's AI news");
                        context.setOutcome(new com.jaguarliu.ai.runtime.RunOutcome(
                                com.jaguarliu.ai.runtime.RunOutcomeStatus.COMPLETED,
                                "Assistant provided a comprehensive summary of AI-related news as requested, fulfilling the original task.",
                                null
                        ));
                        return "Assistant provided a comprehensive summary of AI-related news as requested, fulfilling the original task.";
                    });
            when(sessionLaneManager.submit(eq("session-heavy"), eq("run-heavy"), eq(14L), any(Supplier.class)))
                    .thenAnswer(invocation -> {
                        @SuppressWarnings("unchecked")
                        Supplier<Object> task = (Supplier<Object>) invocation.getArgument(3);
                        task.get();
                        return Mono.empty();
                    });

            RpcResponse response = handler.handle(CONNECTION_ID, RpcRequest.builder()
                    .id("9")
                    .method("agent.run")
                    .payload(Map.of("sessionId", "session-heavy", "prompt", "summarize today's AI news"))
                    .build()).block();

            assertNotNull(response);
            assertNull(response.getError());
            verify(messageService).saveAssistantMessage(
                    "session-heavy",
                    "run-heavy",
                    "Full streamed answer about today's AI news",
                    PRINCIPAL_ID
            );
        }

        @Test
        void heavyRouteShouldPublishTerminalResponseWhenNoDraftWasStreamed() throws Exception {
            AgentRunHandler handler = newAgentRunHandler();
            mockCommonRunPrerequisites();

            when(sessionService.get("session-env", PRINCIPAL_ID))
                    .thenReturn(Optional.of(sessionEntity("session-env", "Env Chat", "coder")));
            when(sessionLaneManager.nextSequence("session-env")).thenReturn(15L);
            when(runService.create("session-env", "open website", "coder", PRINCIPAL_ID))
                    .thenReturn(runEntity("run-env", "session-env", "coder", "open website"));

            AgentStrategy strategy = org.mockito.Mockito.mock(AgentStrategy.class);
            when(strategyResolver.resolve(any())).thenReturn(strategy);
            when(strategy.prepare(any())).thenReturn(AgentExecutionPlan.builder()
                    .systemPrompt("system prompt")
                    .strategyName("default")
                    .build());
            when(messageService.getSessionHistory(eq("session-env"), anyInt(), eq(PRINCIPAL_ID)))
                    .thenReturn(List.of());

            String terminalResponse = "'.\\agent-browser.cmd' 不是内部或外部命令，也不是可运行的程序或批处理文件。";
            when(agentRuntime.executeLoopWithContext(any(RunContext.class), anyList()))
                    .thenAnswer(invocation -> {
                        RunContext context = invocation.getArgument(0);
                        context.setOutcome(new RunOutcome(
                                RunOutcomeStatus.BLOCKED_BY_ENVIRONMENT,
                                "Task blocked by environment",
                                terminalResponse
                        ));
                        return terminalResponse;
                    });
            when(sessionLaneManager.submit(eq("session-env"), eq("run-env"), eq(15L), any(Supplier.class)))
                    .thenAnswer(invocation -> {
                        @SuppressWarnings("unchecked")
                        Supplier<Object> task = (Supplier<Object>) invocation.getArgument(3);
                        task.get();
                        return Mono.empty();
                    });

            RpcResponse response = handler.handle(CONNECTION_ID, RpcRequest.builder()
                    .id("10")
                    .method("agent.run")
                    .payload(Map.of("sessionId", "session-env", "prompt", "open website"))
                    .build()).block();

            assertNotNull(response);
            assertNull(response.getError());
            verify(messageService).saveAssistantMessage("session-env", "run-env", terminalResponse, PRINCIPAL_ID);

            ArgumentCaptor<AgentEvent> eventCaptor = ArgumentCaptor.forClass(AgentEvent.class);
            verify(eventBus, org.mockito.Mockito.atLeastOnce()).publish(eventCaptor.capture());
            List<AgentEvent> assistantDeltas = eventCaptor.getAllValues().stream()
                    .filter(event -> event.getType() == AgentEvent.EventType.ASSISTANT_DELTA)
                    .toList();

            assertEquals(1, assistantDeltas.size());
            assertTrue(assistantDeltas.get(0).getData() instanceof AgentEvent.DeltaData delta
                    && terminalResponse.equals(delta.getContent()));
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
            when(agentRuntime.executeLoopWithContext(any(RunContext.class), anyList()))
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
                agentWorkspaceResolver,
                runService,
                messageService,
                sessionLaneManager,
                eventBus,
                contextBuilder,
                agentRuntime,
                llmClient,
                llmProperties,
                llmCapabilityService,
                toolConfigProperties,
                strategyResolver,
                loopConfig,
                cancellationManager,
                chatRouter,
                taskRouter,
                connectionManager,
                tokenBudgetService,
                auditLogService
        );
    }

    private void mockCommonRunPrerequisites() {
        when(connectionManager.getPrincipal(CONNECTION_ID)).thenReturn(principal);
        lenient().when(agentWorkspaceResolver.resolveAgentWorkspace(anyString()))
                .thenAnswer(invocation -> java.nio.file.Path.of("/tmp", "workspace-" + invocation.getArgument(0).toString()));
        lenient().when(tokenBudgetService.estimateTokens(anyString())).thenReturn(10);
        lenient().when(llmCapabilityService.supportsVision(any())).thenReturn(true);
        lenient().when(tokenBudgetService.tryConsume(eq(PRINCIPAL_ID), anyInt())).thenReturn(true);
        lenient().when(chatRouter.route(anyString(), nullable(String.class), nullable(String.class)))
                .thenAnswer(invocation -> {
                    String prompt = invocation.getArgument(0);
                    String requestedAgentId = invocation.getArgument(1);
                    String sessionAgentId = invocation.getArgument(2);
                    String resolvedAgentId = requestedAgentId != null && !requestedAgentId.isBlank()
                            ? requestedAgentId
                            : sessionAgentId != null && !sessionAgentId.isBlank() ? sessionAgentId : "main";
                    return new ChatRouter.RouteDecision(resolvedAgentId, prompt, null, false);
                });
        lenient().when(taskRouter.route(anyString(), anyList(), anyBoolean(), nullable(String.class)))
                .thenReturn(TaskRoutingDecision.builder()
                        .routeMode(TaskRouteMode.HEAVY)
                        .complexity(TaskComplexity.HEAVY)
                        .shouldUseTools(true)
                        .shouldUseStrategy(true)
                        .reason("default")
                        .build());
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
