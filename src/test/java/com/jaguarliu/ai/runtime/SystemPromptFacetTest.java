package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.mcp.prompt.McpPromptProvider;
import com.jaguarliu.ai.memory.search.MemorySearchService;
import com.jaguarliu.ai.skills.index.SkillIndexBuilder;
import com.jaguarliu.ai.soul.SoulConfigService;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolRegistry;
import com.jaguarliu.ai.tools.ToolVisibilityResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("System Prompt Facet Tests")
class SystemPromptFacetTest {

    @Mock
    private ToolRegistry toolRegistry;
    @Mock
    private SkillIndexBuilder skillIndexBuilder;
    @Mock
    private MemorySearchService memorySearchService;
    @Mock
    private McpPromptProvider mcpPromptProvider;
    @Mock
    private SoulConfigService soulConfigService;

    private SystemPromptBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new SystemPromptBuilder(
                toolRegistry,
                skillIndexBuilder,
                memorySearchService,
                Optional.of(mcpPromptProvider),
                soulConfigService,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
        ReflectionTestUtils.setField(builder, "workspace", "./workspace");
        ReflectionTestUtils.setField(builder, "customSystemPrompt", "");

        lenient().when(skillIndexBuilder.buildIndex(anyString())).thenReturn("");
        lenient().when(mcpPromptProvider.getSystemPromptAdditions(any())).thenReturn("");
        lenient().when(soulConfigService.readSoulMd(anyString())).thenReturn("");
        lenient().when(soulConfigService.readRuleMd(anyString())).thenReturn("");
        lenient().when(soulConfigService.readProfileMd(anyString())).thenReturn("");
    }

    @Test
    @DisplayName("Kernel 模板在同一 mode 下只编译一次")
    void kernelTemplateShouldBuildOnlyOncePerMode() {
        ToolDefinition readFile = ToolDefinition.builder().name("read_file").description("read").build();
        ToolDefinition bash = ToolDefinition.builder().name("bash").description("bash").build();
        when(toolRegistry.listDefinitions(any(ToolVisibilityResolver.VisibilityRequest.class))).thenReturn(List.of(readFile, bash));
        when(toolRegistry.listDefinitions()).thenReturn(List.of(readFile, bash));

        builder.build(SystemPromptBuilder.PromptMode.FULL, Set.of("read_file"), null, "agent-a");
        builder.build(SystemPromptBuilder.PromptMode.FULL, Set.of("bash"), null, "agent-b");

        assertEquals(1, builder.kernelTemplateBuildCount(SystemPromptBuilder.PromptMode.FULL));
    }

    @Test
    @DisplayName("同一 Kernel 下不同 agent 的 soul/tool/memory facet 可替换")
    void facetsShouldBeReplaceableUnderSameKernel() {
        ToolDefinition readFile = ToolDefinition.builder().name("read_file").description("read").build();
        ToolDefinition bash = ToolDefinition.builder().name("bash").description("bash").build();
        when(toolRegistry.listDefinitions(any(ToolVisibilityResolver.VisibilityRequest.class)))
                .thenAnswer(invocation -> {
                    ToolVisibilityResolver.VisibilityRequest req = invocation.getArgument(0);
                    Set<String> allowed = req.strategyAllowedTools();
                    if (allowed == null || allowed.isEmpty()) {
                        return List.of(readFile, bash);
                    }
                    return List.of(readFile, bash).stream()
                            .filter(t -> allowed.contains(t.getName()))
                            .toList();
                });
        when(toolRegistry.listDefinitions()).thenReturn(List.of(readFile, bash));
        when(soulConfigService.readSoulMd(anyString()))
                .thenReturn("## Soul A\n\nAgent A style")
                .thenReturn("## Soul B\n\nAgent B style");
        lenient().when(soulConfigService.readRuleMd(anyString())).thenReturn("");
        lenient().when(soulConfigService.readProfileMd(anyString())).thenReturn("");

        String agentAPrompt = builder.build(
                SystemPromptBuilder.PromptMode.FULL, Set.of("read_file"), null, "agent-a");
        String agentBPrompt = builder.build(
                SystemPromptBuilder.PromptMode.FULL, Set.of("bash"), null, "agent-b");

        assertTrue(agentAPrompt.contains("## Safety Guidelines"));
        assertTrue(agentBPrompt.contains("## Safety Guidelines"));
        assertTrue(agentAPrompt.contains("## Workspace"));
        assertTrue(agentBPrompt.contains("## Workspace"));

        assertTrue(agentAPrompt.contains("## Soul A"));
        assertTrue(agentBPrompt.contains("## Soul B"));

        assertTrue(agentAPrompt.contains("**read_file**"));
        assertFalse(agentAPrompt.contains("**bash**"));
        assertTrue(agentBPrompt.contains("**bash**"));
        assertFalse(agentBPrompt.contains("**read_file**"));

        assertTrue(agentAPrompt.contains("agent scope: `agent-a`"));
        assertTrue(agentBPrompt.contains("agent scope: `agent-b`"));
    }
}
