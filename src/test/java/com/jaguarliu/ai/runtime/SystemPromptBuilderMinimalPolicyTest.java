package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.heartbeat.HeartbeatConfigService;
import com.jaguarliu.ai.mcp.prompt.McpPromptProvider;
import com.jaguarliu.ai.memory.search.MemorySearchService;
import com.jaguarliu.ai.skills.index.SkillIndexBuilder;
import com.jaguarliu.ai.soul.SoulConfigService;
import com.jaguarliu.ai.tools.ToolRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("SystemPromptBuilder Minimal Policy Tests")
class SystemPromptBuilderMinimalPolicyTest {

    @Test
    @DisplayName("minimal prompt should not include planning protocol or skills")
    void minimalPromptShouldNotIncludePlanningProtocolOrSkills() {
        ToolRegistry toolRegistry = mock(ToolRegistry.class);
        SkillIndexBuilder skillIndexBuilder = mock(SkillIndexBuilder.class);
        MemorySearchService memorySearchService = mock(MemorySearchService.class);
        SoulConfigService soulConfigService = mock(SoulConfigService.class);

        when(skillIndexBuilder.buildIndex("main")).thenReturn("<skills/> ");
        when(skillIndexBuilder.buildCompactIndex("main")).thenReturn("<skills/> ");

        SystemPromptBuilder builder = new SystemPromptBuilder(
                toolRegistry,
                skillIndexBuilder,
                memorySearchService,
                Optional.<McpPromptProvider>empty(),
                soulConfigService,
                Optional.<HeartbeatConfigService>empty(),
                Optional.empty(),
                Optional.empty()
        );

        ReflectionTestUtils.setField(builder, "workspace", "./workspace");
        String result = builder.build(SystemPromptBuilder.PromptMode.MINIMAL);

        assertFalse(result.contains("Planning Protocol"));
        assertFalse(result.contains("## Skills"));
    }
}
