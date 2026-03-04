package com.jaguarliu.ai.tools.builtin;

import com.jaguarliu.ai.skills.model.SkillEntry;
import com.jaguarliu.ai.skills.model.SkillMetadata;
import com.jaguarliu.ai.skills.registry.SkillRegistry;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.builtin.skill.UseSkillTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("UseSkillTool Tests")
class UseSkillToolTest {

    @Test
    @DisplayName("skill_name includes enum of available skills")
    @SuppressWarnings("unchecked")
    void skillNameEnumIncludesAvailableSkills() {
        SkillRegistry registry = mock(SkillRegistry.class);
        when(registry.getAvailable("main")).thenReturn(List.of(
                entry("code-review", "Review code changes"),
                entry("git-commit", "Generate commit messages")
        ));

        UseSkillTool tool = new UseSkillTool(registry);
        ToolDefinition definition = tool.getDefinition();

        Map<String, Object> properties =
                (Map<String, Object>) definition.getParameters().get("properties");
        Map<String, Object> skillNameSchema =
                (Map<String, Object>) properties.get("skill_name");
        Object enumValue = skillNameSchema.get("enum");

        assertNotNull(enumValue);
        assertInstanceOf(List.class, enumValue);
        assertEquals(List.of("code-review", "git-commit"), enumValue);
    }

    @Test
    @DisplayName("skill_name omits enum when no skills are available")
    @SuppressWarnings("unchecked")
    void skillNameEnumAbsentWhenNoAvailableSkills() {
        SkillRegistry registry = mock(SkillRegistry.class);
        when(registry.getAvailable("main")).thenReturn(List.of());

        UseSkillTool tool = new UseSkillTool(registry);
        ToolDefinition definition = tool.getDefinition();

        Map<String, Object> properties =
                (Map<String, Object>) definition.getParameters().get("properties");
        Map<String, Object> skillNameSchema =
                (Map<String, Object>) properties.get("skill_name");

        assertFalse(skillNameSchema.containsKey("enum"));
    }

    private static SkillEntry entry(String name, String description) {
        return SkillEntry.builder()
                .metadata(SkillMetadata.builder()
                        .name(name)
                        .description(description)
                        .build())
                .available(true)
                .build();
    }
}
