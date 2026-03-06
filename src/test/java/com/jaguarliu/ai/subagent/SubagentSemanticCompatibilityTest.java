package com.jaguarliu.ai.subagent;

import com.jaguarliu.ai.memory.search.MemorySearchService;
import com.jaguarliu.ai.runtime.CancellationManager;
import com.jaguarliu.ai.runtime.LoopConfig;
import com.jaguarliu.ai.runtime.RunContext;
import com.jaguarliu.ai.runtime.SystemPromptBuilder;
import com.jaguarliu.ai.skills.index.SkillIndexBuilder;
import com.jaguarliu.ai.soul.SoulConfigService;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolExecutionContext;
import com.jaguarliu.ai.tools.ToolRegistry;
import com.jaguarliu.ai.tools.ToolResult;
import com.jaguarliu.ai.tools.ToolVisibilityResolver;
import com.jaguarliu.ai.tools.builtin.workflow.SessionsSpawnTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Subagent semantic compatibility tests")
class SubagentSemanticCompatibilityTest {

    @Mock
    private SubagentService subagentService;
    @Mock
    private ToolRegistry toolRegistry;
    @Mock
    private SkillIndexBuilder skillIndexBuilder;
    @Mock
    private MemorySearchService memorySearchService;
    @Mock
    private SoulConfigService soulConfigService;
    @AfterEach
    void clearToolContext() {
        ToolExecutionContext.clear();
    }

    @Test
    @DisplayName("RunContext subagent mode should be compatible with worker semantics")
    void runContextWorkerSemanticAlias() {
        LoopConfig loopConfig = LoopConfig.builder().maxSteps(3).runTimeoutSeconds(30).build();
        CancellationManager cancellationManager = new CancellationManager();
        RunContext context = RunContext.createSubagent(
                "sub-run-1",
                "conn-1",
                "sub-session-1",
                "architect",
                "parent-run-1",
                "parent-session-1",
                false,
                loopConfig,
                cancellationManager
        );

        assertTrue(context.isSubagent());
        assertTrue(context.isWorker());
        assertFalse(context.isMain());
    }

    @Test
    @DisplayName("sessions_spawn should still delegate task to specified agent worker")
    void sessionsSpawnDelegatesToSpecifiedAgent() {
        SessionsSpawnTool tool = new SessionsSpawnTool(subagentService);
        when(subagentService.spawn(any(), any(), any(), any(), any()))
                .thenReturn(com.jaguarliu.ai.subagent.model.SubagentSpawnResult.success(
                        "sub-session-1", "sub-run-1", "agent:main:subagent:sub-session-1"
                ));

        ToolExecutionContext context = ToolExecutionContext.builder()
                .runId("run-main-1")
                .sessionId("session-main-1")
                .agentId("main")
                .connectionId("conn-1")
                .runKind("main")
                .build();
        ToolExecutionContext.set(context);

        ToolResult result = tool.execute(Map.of(
                "task", "并行生成 API 设计文档",
                "agentId", "architect"
        )).block();

        assertNotNull(result);
        assertTrue(result.isSuccess());

        ArgumentCaptor<com.jaguarliu.ai.subagent.model.SubagentSpawnRequest> requestCaptor =
                ArgumentCaptor.forClass(com.jaguarliu.ai.subagent.model.SubagentSpawnRequest.class);
        verify(subagentService).spawn(
                eq("run-main-1"),
                eq("session-main-1"),
                eq("main"),
                eq("conn-1"),
                requestCaptor.capture()
        );
        assertEquals("architect", requestCaptor.getValue().getAgentId());
    }

    @Test
    @DisplayName("system prompt should describe sessions_spawn as worker mechanism")
    void systemPromptUsesWorkerSemantics() {
        ToolDefinition spawnTool = ToolDefinition.builder()
                .name("sessions_spawn")
                .description("spawn worker")
                .hitl(false)
                .build();
        when(toolRegistry.listDefinitions()).thenReturn(List.of(spawnTool));
        when(toolRegistry.listDefinitions(any(ToolVisibilityResolver.VisibilityRequest.class)))
                .thenReturn(List.of(spawnTool));
        when(skillIndexBuilder.buildIndex("main")).thenReturn("");

        SystemPromptBuilder builder = new SystemPromptBuilder(
                toolRegistry,
                skillIndexBuilder,
                memorySearchService,
                Optional.empty(),
                soulConfigService,
                Optional.empty(),
                Optional.empty()
        );
        ReflectionTestUtils.setField(builder, "workspace", "./workspace");

        String prompt = builder.build(SystemPromptBuilder.PromptMode.FULL);

        assertTrue(prompt.contains("## Worker Task Execution (sessions_spawn)"));
        assertFalse(prompt.contains("## SubAgent (sessions_spawn)"));
    }
}
