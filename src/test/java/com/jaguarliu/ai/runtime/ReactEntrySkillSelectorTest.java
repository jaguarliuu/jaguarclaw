package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.llm.StructuredOutputExecutor;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.llm.model.StructuredLlmResult;
import com.jaguarliu.ai.skills.model.SkillEntry;
import com.jaguarliu.ai.skills.model.SkillMetadata;
import com.jaguarliu.ai.skills.registry.SkillRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReactEntrySkillSelector Tests")
class ReactEntrySkillSelectorTest {

    @Mock
    private StructuredOutputExecutor structuredOutputExecutor;

    @Mock
    private SkillRegistry skillRegistry;

    @InjectMocks
    private ReactEntrySkillSelector selector;

    @Test
    @DisplayName("should select available skill from structured decision")
    void shouldSelectAvailableSkillFromStructuredDecision() {
        when(skillRegistry.getAvailable("coder")).thenReturn(List.of(
                SkillEntry.builder()
                        .available(true)
                        .metadata(SkillMetadata.builder()
                                .name("agent-browser")
                                .description("Automates browser interaction")
                                .tags(List.of("browser", "automation"))
                                .triggers(List.of("打开浏览器", "访问网页"))
                                .build())
                        .build()
        ));
        when(structuredOutputExecutor.execute(any(), eq(ReactEntrySkillSelection.class)))
                .thenReturn(StructuredLlmResult.<ReactEntrySkillSelection>builder()
                        .value(ReactEntrySkillSelection.builder()
                                .skillName("agent-browser")
                                .reason("browser task")
                                .confidence(0.97)
                                .build())
                        .build());

        Optional<ReactEntrySkillSelection> result = selector.select(
                "打开浏览器访问知乎",
                List.of(LlmRequest.Message.user("打开浏览器访问知乎")),
                "coder",
                null
        );

        assertTrue(result.isPresent());
        assertEquals("agent-browser", result.get().getSkillName());
        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(structuredOutputExecutor).execute(captor.capture(), eq(ReactEntrySkillSelection.class));
        assertTrue(captor.getValue().getMessages().get(1).getContent().contains("agent-browser"));
    }

    @Test
    @DisplayName("should return empty when no skills are available")
    void shouldReturnEmptyWhenNoSkillsAreAvailable() {
        when(skillRegistry.getAvailable("coder")).thenReturn(List.of());
        Optional<ReactEntrySkillSelection> result = selector.select("打开浏览器访问知乎", List.of(), "coder", null);
        assertTrue(result.isEmpty());
    }
}
