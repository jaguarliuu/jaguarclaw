package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.llm.StructuredOutputExecutor;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.llm.model.StructuredLlmResult;
import com.jaguarliu.ai.llm.model.StructuredOutputSpec;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlanEngine {

    private static final Map<String, Object> PLAN_SCHEMA = buildSchema();

    private final StructuredOutputExecutor structuredOutputExecutor;

    public ExecutionPlan createInitialPlan(RunContext context, List<LlmRequest.Message> history) {
        try {
            StructuredLlmResult<PlanDraft> result = structuredOutputExecutor.execute(
                    buildPlanRequest(context, history),
                    PlanDraft.class
            );
            PlanDraft draft = result != null ? result.getValue() : null;
            if (draft == null || draft.getItems() == null || draft.getItems().isEmpty()) {
                return fallbackPlan(context != null ? context.getOriginalInput() : null);
            }
            List<PlanItem> items = new ArrayList<>();
            IntStream.range(0, draft.getItems().size()).forEach(index -> {
                PlanDraftItem item = draft.getItems().get(index);
                items.add(PlanItem.builder()
                        .id("item-" + (index + 1))
                        .title(item.getTitle())
                        .status(index == 0 ? PlanItemStatus.IN_PROGRESS : PlanItemStatus.PENDING)
                        .executionMode(Boolean.TRUE.equals(item.getUseSubagent()) ? PlanExecutionMode.SUBAGENT : PlanExecutionMode.MAIN_AGENT)
                        .notes(item.getNotes())
                        .build());
            });
            return ExecutionPlan.builder()
                    .goal(context != null ? context.getOriginalInput() : null)
                    .status(ExecutionPlanStatus.ACTIVE)
                    .currentItemId(items.get(0).getId())
                    .items(items)
                    .revision(1)
                    .build();
        } catch (Exception e) {
            log.warn("Plan engine degraded to fallback plan: runId={}, error={}",
                    context != null ? context.getRunId() : "unknown", e.getMessage());
            return fallbackPlan(context != null ? context.getOriginalInput() : null);
        }
    }

    public ExecutionPlan markInProgress(ExecutionPlan plan, String itemId) {
        if (plan == null || itemId == null) {
            return plan;
        }
        for (PlanItem item : plan.getItems()) {
            if (itemId.equals(item.getId()) && item.getStatus() == PlanItemStatus.PENDING) {
                item.setStatus(PlanItemStatus.IN_PROGRESS);
            }
        }
        plan.setCurrentItemId(itemId);
        plan.setStatus(ExecutionPlanStatus.ACTIVE);
        return plan;
    }

    public ExecutionPlan markDone(ExecutionPlan plan, String itemId, String note) {
        PlanItem item = findItem(plan, itemId);
        if (item != null) {
            item.setStatus(PlanItemStatus.DONE);
            if (note != null && !note.isBlank()) {
                item.setNotes(note);
            }
        }
        if (plan != null && plan.allItemsDone()) {
            plan.setStatus(ExecutionPlanStatus.COMPLETED);
            plan.setCurrentItemId(null);
        }
        return plan;
    }

    public ExecutionPlan markBlocked(ExecutionPlan plan, String itemId, String reason) {
        PlanItem item = findItem(plan, itemId);
        if (item != null) {
            item.setStatus(PlanItemStatus.BLOCKED);
            item.setNotes(reason);
        }
        if (plan != null) {
            plan.setStatus(ExecutionPlanStatus.BLOCKED);
        }
        return plan;
    }

    public ExecutionPlan bindSkill(ExecutionPlan plan, String itemId, String skillName) {
        PlanItem item = findItem(plan, itemId);
        if (item != null) {
            item.setSkillName(skillName);
        }
        return plan;
    }

    public ExecutionPlan markDelegated(ExecutionPlan plan, String itemId, String subRunId) {
        PlanItem item = findItem(plan, itemId);
        if (item != null) {
            item.setExecutionMode(PlanExecutionMode.SUBAGENT);
            item.setSubRunId(subRunId);
            item.setStatus(PlanItemStatus.IN_PROGRESS);
        }
        return plan;
    }

    public ExecutionPlan advance(ExecutionPlan plan) {
        if (plan == null || plan.getItems() == null) {
            return plan;
        }
        PlanItem next = plan.getItems().stream()
                .filter(item -> item.getStatus() == PlanItemStatus.PENDING)
                .findFirst()
                .orElse(null);
        if (next == null) {
            if (plan.allItemsDone()) {
                plan.setStatus(ExecutionPlanStatus.COMPLETED);
                plan.setCurrentItemId(null);
            }
            return plan;
        }
        next.setStatus(PlanItemStatus.IN_PROGRESS);
        plan.setCurrentItemId(next.getId());
        plan.setStatus(ExecutionPlanStatus.ACTIVE);
        return plan;
    }

    private PlanItem findItem(ExecutionPlan plan, String itemId) {
        if (plan == null || itemId == null || plan.getItems() == null) {
            return null;
        }
        return plan.getItems().stream().filter(item -> itemId.equals(item.getId())).findFirst().orElse(null);
    }

    private ExecutionPlan fallbackPlan(String goal) {
        PlanItem item = PlanItem.builder()
                .id("item-1")
                .title(goal != null && !goal.isBlank() ? goal : "complete the requested task")
                .status(PlanItemStatus.IN_PROGRESS)
                .executionMode(PlanExecutionMode.MAIN_AGENT)
                .build();
        return ExecutionPlan.builder()
                .goal(goal)
                .status(ExecutionPlanStatus.ACTIVE)
                .currentItemId(item.getId())
                .items(new ArrayList<>(List.of(item)))
                .revision(1)
                .build();
    }

    private LlmRequest buildPlanRequest(RunContext context, List<LlmRequest.Message> history) {
        List<LlmRequest.Message> messages = new ArrayList<>();
        messages.add(LlmRequest.Message.system("""
                You are a planning assistant for an autonomous coding/runtime agent.
                Break the task into 2-6 actionable todo items.
                Use MAIN_AGENT by default.
                Use SUBAGENT only for isolated, long-running, or parallelizable work.
                Keep items concrete, short, and execution-oriented.
                Return only structured data.
                """.trim()));
        messages.add(LlmRequest.Message.user(buildPlanPrompt(context, history)));

        LlmRequest.LlmRequestBuilder builder = LlmRequest.builder()
                .messages(messages)
                .temperature(0.0)
                .maxTokens(500)
                .structuredOutput(StructuredOutputSpec.builder()
                        .name("execution_plan")
                        .jsonSchema(PLAN_SCHEMA)
                        .strict(true)
                        .fallbackToPromptJson(true)
                        .build());

        if (context != null && context.getModelSelection() != null && context.getModelSelection().contains(":")) {
            String[] parts = context.getModelSelection().split(":", 2);
            if (parts.length == 2) {
                builder.providerId(parts[0]);
                builder.model(parts[1]);
            }
        }
        return builder.build();
    }

    private String buildPlanPrompt(RunContext context, List<LlmRequest.Message> history) {
        StringBuilder builder = new StringBuilder();
        builder.append("Goal:\n")
                .append(context != null && context.getOriginalInput() != null ? context.getOriginalInput() : "")
                .append("\n\nRecent history:\n");
        if (history == null || history.isEmpty()) {
            builder.append("- none\n");
        } else {
            history.stream()
                    .filter(message -> message != null && message.resolvedTextContent() != null)
                    .skip(Math.max(0, history.size() - 6L))
                    .forEach(message -> builder.append("- ")
                            .append(message.getRole())
                            .append(": ")
                            .append(message.resolvedTextContent())
                            .append("\n"));
        }
        return builder.toString();
    }

    private static Map<String, Object> buildSchema() {
        Map<String, Object> itemProperties = new LinkedHashMap<>();
        itemProperties.put("title", Map.of("type", "string"));
        itemProperties.put("notes", Map.of("type", "string"));
        itemProperties.put("useSubagent", Map.of("type", "boolean"));

        Map<String, Object> itemSchema = new LinkedHashMap<>();
        itemSchema.put("type", "object");
        itemSchema.put("additionalProperties", false);
        itemSchema.put("properties", itemProperties);
        itemSchema.put("required", List.of("title", "useSubagent"));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("items", Map.of(
                "type", "array",
                "minItems", 1,
                "maxItems", 6,
                "items", itemSchema
        ));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", properties);
        schema.put("required", List.of("items"));
        return schema;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlanDraft {
        private List<PlanDraftItem> items;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlanDraftItem {
        private String title;
        private String notes;
        private Boolean useSubagent;
    }
}
