package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.skills.index.SkillIndexBuilder;
import com.jaguarliu.ai.skills.registry.SkillRegistry;
import com.jaguarliu.ai.skills.selector.SkillSelector;
import com.jaguarliu.ai.skills.selector.SkillSelection;
import com.jaguarliu.ai.skills.template.SkillTemplateEngine;
import com.jaguarliu.ai.tools.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContextBuilder Policy Tests")
class ContextBuilderPolicyTest {

    @Mock private ToolRegistry toolRegistry;
    @Mock private SkillRegistry skillRegistry;
    @Mock private SkillIndexBuilder skillIndexBuilder;
    @Mock private SkillSelector skillSelector;
    @Mock private SkillTemplateEngine templateEngine;
    @Mock private SystemPromptBuilder systemPromptBuilder;
    @Mock private ContextCompactionService compactionService;

    @InjectMocks
    private ContextBuilder contextBuilder;

    @BeforeEach
    void setUp() {
        lenient().when(systemPromptBuilder.build(eq(SystemPromptBuilder.PromptMode.MINIMAL), any(), any(), anyString()))
                .thenReturn("最小提示");
        lenient().when(systemPromptBuilder.build(eq(SystemPromptBuilder.PromptMode.FULL))).thenReturn("完整提示");
        lenient().when(systemPromptBuilder.build(eq(SystemPromptBuilder.PromptMode.FULL), any(), any(), anyString()))
                .thenReturn("完整提示");
        lenient().when(compactionService.shouldCompact(any(), any(Integer.class))).thenReturn(false);
        ReflectionTestUtils.setField(contextBuilder, "autoSelectEnabled", true);
        lenient().when(skillSelector.tryManualSelection(anyString(), anyString())).thenAnswer(inv -> SkillSelection.none(inv.getArgument(0)));
        lenient().when(skillRegistry.getAvailable(anyString())).thenReturn(List.of());
    }

    @Test
    @DisplayName("should use lightweight context for direct response")
    void shouldUseLightweightContextForDirectResponse() {
        ContextBuilder.SkillAwareRequest request = contextBuilder.buildDirectResponse(
                List.of(), "今天几号？", "main");

        assertEquals(2, request.request().getMessages().size());
        assertEquals("system", request.request().getMessages().get(0).getRole());
        assertEquals("user", request.request().getMessages().get(1).getRole());
        assertNull(request.request().getTools());
    }

    @Test
    @DisplayName("should attach tools for react entry when enabled")
    void shouldAttachToolsForReactEntryWhenEnabled() {
        when(toolRegistry.size()).thenReturn(1);
        when(toolRegistry.toOpenAiTools(any(com.jaguarliu.ai.tools.ToolVisibilityResolver.VisibilityRequest.class)))
                .thenReturn(List.of(java.util.Map.of("type", "function")));

        ContextBuilder.SkillAwareRequest request = contextBuilder.buildReactEntry(
                List.of(), "读取这个文件", true, "main");

        assertEquals("auto", request.request().getToolChoice());
        assertEquals(1, request.request().getTools().size());
    }
}
