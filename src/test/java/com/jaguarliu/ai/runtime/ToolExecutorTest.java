package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.agents.context.AgentWorkspaceResolver;
import com.jaguarliu.ai.gateway.events.EventBus;
import com.jaguarliu.ai.llm.model.ToolCall;
import com.jaguarliu.ai.session.SessionFileService;
import com.jaguarliu.ai.tools.ToolDispatcher;
import com.jaguarliu.ai.tools.ToolRegistry;
import com.jaguarliu.ai.tools.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ToolExecutor Tests")
class ToolExecutorTest {

    @Mock
    private ToolRegistry toolRegistry;

    @Mock
    private ToolDispatcher toolDispatcher;

    @Mock
    private HitlManager hitlManager;

    @Mock
    private EventBus eventBus;

    @Mock
    private SessionFileService sessionFileService;

    @Mock
    private AgentWorkspaceResolver agentWorkspaceResolver;

    @InjectMocks
    private ToolExecutor toolExecutor;

    @Test
    @DisplayName("HITL rejected tool call should return structured rejection and skip dispatch")
    void hitlRejectedShouldReturnStructuredRejectionAndSkipDispatch() {
        RunContext context = RunContext.create(
                "run-1",
                "conn-1",
                "session-1",
                new LoopConfig(),
                new CancellationManager()
        );

        ToolCall call = ToolCall.builder()
                .id("call-1")
                .function(ToolCall.FunctionCall.builder()
                        .name("remote_exec")
                        .arguments("{\"alias\":\"n1\",\"command\":\"ls -la\"}")
                        .build())
                .build();

        when(toolDispatcher.requiresHitl(anyString(), any(), anyMap())).thenReturn(true);
        when(hitlManager.requestConfirmation("call-1", "remote_exec"))
                .thenReturn(Mono.just(HitlDecision.reject()));

        List<ToolExecutor.ToolExecutionResult> results = toolExecutor.executeToolCalls(context, List.of(call));

        assertFalse(results.get(0).result().isSuccess());
        assertEquals(RuntimeFailureCategories.HITL_REJECTED, results.get(0).result().getFailureCategory());
        assertEquals(RuntimeFailureCategories.HITL_REJECTED, results.get(0).failureCategory());
        verify(toolDispatcher, never()).dispatch(anyString(), anyMap(), any());
    }

    @Test
    @DisplayName("tool errors should prefer structured failure category")
    void toolErrorsShouldPreferStructuredFailureCategory() {
        RunContext context = RunContext.create(
                "run-1",
                "conn-1",
                "session-1",
                new LoopConfig(),
                new CancellationManager()
        );

        ToolCall call = ToolCall.builder()
                .id("call-2")
                .function(ToolCall.FunctionCall.builder()
                        .name("bash")
                        .arguments("{\"command\":\"wkhtmltopdf\"}")
                        .build())
                .build();

        when(agentWorkspaceResolver.resolveAgentWorkspace(anyString())).thenReturn(Path.of("."));
        when(toolDispatcher.requiresHitl(anyString(), any(), anyMap())).thenReturn(false);
        when(toolDispatcher.dispatch(anyString(), anyMap(), any()))
                .thenReturn(Mono.just(ToolResult.error(
                        "wkhtmltopdf: command not found",
                        RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT
                )));

        List<ToolExecutor.ToolExecutionResult> results = toolExecutor.executeToolCalls(context, List.of(call));

        assertEquals(RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT, results.get(0).failureCategory());
    }

    @Test
    @DisplayName("unstructured tool errors should degrade to generic tool error")
    void unstructuredToolErrorsShouldDegradeToGenericToolError() {
        RunContext context = RunContext.create(
                "run-1",
                "conn-1",
                "session-1",
                new LoopConfig(),
                new CancellationManager()
        );

        ToolCall call = ToolCall.builder()
                .id("call-3")
                .function(ToolCall.FunctionCall.builder()
                        .name("bash")
                        .arguments("{\"command\":\"unknown\"}")
                        .build())
                .build();

        when(agentWorkspaceResolver.resolveAgentWorkspace(anyString())).thenReturn(Path.of("."));
        when(toolDispatcher.requiresHitl(anyString(), any(), anyMap())).thenReturn(false);
        when(toolDispatcher.dispatch(anyString(), anyMap(), any()))
                .thenReturn(Mono.just(ToolResult.error("arbitrary failure")));

        List<ToolExecutor.ToolExecutionResult> results = toolExecutor.executeToolCalls(context, List.of(call));

        assertEquals(RuntimeFailureCategories.TOOL_ERROR, results.get(0).failureCategory());
    }
}
