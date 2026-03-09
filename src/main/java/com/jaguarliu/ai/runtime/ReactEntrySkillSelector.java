package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.llm.StructuredOutputExecutor;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.llm.model.StructuredOutputSpec;
import com.jaguarliu.ai.skills.model.SkillEntry;
import com.jaguarliu.ai.skills.model.SkillMetadata;
import com.jaguarliu.ai.skills.registry.SkillRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReactEntrySkillSelector {

    private static final String NONE = "NONE";

    private final StructuredOutputExecutor structuredOutputExecutor;
    private final SkillRegistry skillRegistry;

    public Optional<ReactEntrySkillSelection> select(String prompt,
                                                     List<LlmRequest.Message> history,
                                                     String agentId,
                                                     String modelSelection) {
        List<SkillEntry> availableSkills = skillRegistry.getAvailable(agentId);
        if (availableSkills == null || availableSkills.isEmpty()) {
            return Optional.empty();
        }

        try {
            ReactEntrySkillSelection decision = structuredOutputExecutor.execute(
                    buildRequest(prompt, history, availableSkills, modelSelection),
                    ReactEntrySkillSelection.class
            ).getValue();
            if (decision == null || !decision.hasSelectedSkill()) {
                return Optional.empty();
            }
            return Optional.of(decision);
        } catch (Exception e) {
            log.warn("React entry skill selector degraded to no-skill: agentId={}, error={}",
                    agentId, e.getMessage());
            return Optional.empty();
        }
    }

    private LlmRequest buildRequest(String prompt,
                                    List<LlmRequest.Message> history,
                                    List<SkillEntry> availableSkills,
                                    String modelSelection) {
        List<LlmRequest.Message> messages = new ArrayList<>();
        messages.add(LlmRequest.Message.system("""
                You are the REACT entry skill selector for an autonomous agent.
                Choose at most one available skill before the execution loop starts.
                Use the full recent history and the current user request.
                Prefer selecting a skill when it materially improves execution quality, tooling, or workflow.
                Return NONE when no skill is a strong match.
                Return only the structured decision.
                """.trim()));
        messages.add(LlmRequest.Message.user(buildSelectionPrompt(prompt, history, availableSkills)));

        LlmRequest.LlmRequestBuilder builder = LlmRequest.builder()
                .messages(messages)
                .temperature(0.0)
                .maxTokens(300)
                .structuredOutput(StructuredOutputSpec.builder()
                        .name("react_entry_skill_selection")
                        .jsonSchema(buildSchema(availableSkills))
                        .strict(true)
                        .fallbackToPromptJson(true)
                        .build());
        applyModelSelection(builder, modelSelection);
        return builder.build();
    }

    private String buildSelectionPrompt(String prompt,
                                        List<LlmRequest.Message> history,
                                        List<SkillEntry> availableSkills) {
        StringBuilder builder = new StringBuilder();
        builder.append("Current user request:\n")
                .append(prompt != null ? prompt : "")
                .append("\n\nRecent history:\n");
        if (history == null || history.isEmpty()) {
            builder.append("- none\n");
        } else {
            history.stream()
                    .filter(message -> message != null && message.getRole() != null && message.resolvedTextContent() != null)
                    .skip(Math.max(0, history.size() - 8L))
                    .forEach(message -> builder.append("- ")
                            .append(message.getRole())
                            .append(": ")
                            .append(message.resolvedTextContent())
                            .append("\n"));
        }

        builder.append("\nAvailable skills:\n");
        for (SkillEntry entry : availableSkills) {
            SkillMetadata metadata = entry.getMetadata();
            if (metadata == null) {
                continue;
            }
            builder.append("- ")
                    .append(metadata.getName())
                    .append(": ")
                    .append(metadata.getDescription() != null ? metadata.getDescription() : "")
                    .append("\n");
            if (metadata.getTriggers() != null && !metadata.getTriggers().isEmpty()) {
                builder.append("  triggers: ")
                        .append(String.join(", ", metadata.getTriggers()))
                        .append("\n");
            }
            if (metadata.getTags() != null && !metadata.getTags().isEmpty()) {
                builder.append("  tags: ")
                        .append(String.join(", ", metadata.getTags()))
                        .append("\n");
            }
        }
        builder.append("\nChoose one skill name or NONE.");
        return builder.toString();
    }

    private Map<String, Object> buildSchema(List<SkillEntry> availableSkills) {
        List<String> skillNames = new ArrayList<>();
        skillNames.add(NONE);
        for (SkillEntry entry : availableSkills) {
            if (entry.getMetadata() != null && entry.getMetadata().getName() != null && !entry.getMetadata().getName().isBlank()) {
                skillNames.add(entry.getMetadata().getName());
            }
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("skillName", Map.of(
                "type", "string",
                "enum", skillNames
        ));
        properties.put("reason", Map.of("type", "string"));
        properties.put("confidence", Map.of("type", "number"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", properties);
        schema.put("required", List.of("skillName"));
        return schema;
    }

    private void applyModelSelection(LlmRequest.LlmRequestBuilder builder, String modelSelection) {
        if (modelSelection == null || !modelSelection.contains(":")) {
            return;
        }
        String[] parts = modelSelection.split(":", 2);
        if (parts.length == 2) {
            builder.providerId(parts[0]);
            builder.model(parts[1]);
        }
    }
}
