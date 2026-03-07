package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.agents.context.AgentWorkspaceResolver;
import com.jaguarliu.ai.gateway.events.EventBus;
import com.jaguarliu.ai.llm.model.ToolCall;
import com.jaguarliu.ai.session.SessionFileService;
import com.jaguarliu.ai.tools.ToolDispatcher;
import com.jaguarliu.ai.tools.ToolRegistry;
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
    @DisplayName("HITL rejected tool call should return explicit rejection marker and skip dispatch")
    void hitlRejectedShouldReturnMarkerAndSkipDispatch() {
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
        assertTrue(results.get(0).result().getContent().contains(ToolExecutor.HITL_REJECTED_MARKER));
        assertEquals("hitl_rejected", results.get(0).failureCategory());
        verify(toolDispatcher, never()).dispatch(anyString(), anyMap(), any());
    }

    @Test
    @DisplayName("tool errors should be tagged with machine readable failure category")
    void toolErrorsShouldBeTaggedWithFailureCategory() {
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
                .thenReturn(Mono.just(com.jaguarliu.ai.tools.ToolResult.error("wkhtmltopdf: command not found")));

        List<ToolExecutor.ToolExecutionResult> results = toolExecutor.executeToolCalls(context, List.of(call));

        assertEquals("environment_missing", results.get(0).failureCategory());
    }

    @Test
    @DisplayName("localized windows tool errors should be tagged as environment missing")
    void localizedWindowsToolErrorsShouldBeTaggedAsEnvironmentMissing() {
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
                        .arguments("{\"command\":\"wkhtmltopdf --version\"}")
                        .build())
                .build();

        when(agentWorkspaceResolver.resolveAgentWorkspace(anyString())).thenReturn(Path.of("."));
        when(toolDispatcher.requiresHitl(anyString(), any(), anyMap())).thenReturn(false);
        when(toolDispatcher.dispatch(anyString(), anyMap(), any()))
                .thenReturn(Mono.just(com.jaguarliu.ai.tools.ToolResult.error(
                        "'wkhtmltopdf' 不是内部或外部命令，也不是可运行的程序或批处理文件。"
                )));

        List<ToolExecutor.ToolExecutionResult> results = toolExecutor.executeToolCalls(context, List.of(call));

        assertEquals("environment_missing", results.get(0).failureCategory());
    }
}
