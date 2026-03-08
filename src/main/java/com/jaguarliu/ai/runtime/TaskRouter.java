package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.llm.StructuredOutputExecutor;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.llm.model.StructuredOutputSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ralph loop 顶层语义路由器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskRouter {

    private static final Map<String, Object> ROUTER_SCHEMA = buildSchema();

    private final StructuredOutputExecutor structuredOutputExecutor;
    private final PolicySupervisor policySupervisor;

    public TaskRoutingDecision route(String prompt,
                                     List<LlmRequest.Message> history,
                                     boolean hasImageAttachments,
                                     String modelSelection) {
        if (hasImageAttachments) {
            return TaskRoutingDecision.builder()
                    .routeMode(TaskRouteMode.HEAVY)
                    .complexity(TaskComplexity.HEAVY)
                    .shouldUseTools(true)
                    .shouldUseStrategy(true)
                    .reason("image attachments require full execution path")
                    .confidence(1.0)
                    .build();
        }

        try {
            return structuredOutputExecutor.execute(
                    buildRoutingRequest(prompt, history, modelSelection),
                    TaskRoutingDecision.class
            ).getValue();
        } catch (Exception e) {
            log.warn("Task router degraded to fallback policy: error={}", e.getMessage());
            return fallbackDecision(prompt, List.of());
        }
    }

    private TaskRoutingDecision fallbackDecision(String prompt, List<String> observations) {
        PolicyDecision fallback = policySupervisor.evaluate(prompt, observations);
        if (fallback.outcome() != null) {
            return TaskRoutingDecision.builder()
                    .routeMode(TaskRouteMode.BLOCKED)
                    .complexity(fallback.complexity())
                    .shouldUseTools(false)
                    .shouldUseStrategy(false)
                    .outcomeStatus(fallback.outcome().status())
                    .outcomeMessage(fallback.outcome().message())
                    .outcomeDetail(fallback.outcome().detail())
                    .reason("fallback_policy")
                    .confidence(0.6)
                    .build();
        }
        return TaskRoutingDecision.builder()
                .routeMode(TaskRouteMode.HEAVY)
                .complexity(TaskComplexity.HEAVY)
                .shouldUseTools(true)
                .shouldUseStrategy(true)
                .reason("fallback_policy")
                .confidence(0.5)
                .build();
    }

    private LlmRequest buildRoutingRequest(String prompt,
                                           List<LlmRequest.Message> history,
                                           String modelSelection) {
        List<LlmRequest.Message> messages = new ArrayList<>();
        messages.add(LlmRequest.Message.system("""
                You are the top-level Ralph loop router for an AI agent.
                Classify the user's request into exactly one route mode:
                - CHAT: casual conversation, greeting, acknowledgement, no tools needed.
                - DIRECT: simple answerable request, no tools, no multi-step execution.
                - LIGHT: small task or simple tool-assisted task with tight budget.
                - HEAVY: multi-step execution, uncertain workflow, or larger task.
                - BLOCKED: cannot proceed without user decision or impossible from prompt alone.
                Prefer semantic understanding over keyword matching.
                Use BLOCKED only when the user prompt itself clearly implies a required decision or impossible precondition.
                Return only the structured routing decision.
                """.trim()));
        messages.add(LlmRequest.Message.user(buildRoutingPrompt(prompt, history)));

        LlmRequest.LlmRequestBuilder builder = LlmRequest.builder()
                .messages(messages)
                .temperature(0.0)
                .maxTokens(300)
                .structuredOutput(StructuredOutputSpec.builder()
                        .name("task_routing_decision")
                        .jsonSchema(ROUTER_SCHEMA)
                        .strict(true)
                        .fallbackToPromptJson(true)
                        .build());

        applyModelSelection(builder, modelSelection);
        return builder.build();
    }

    private String buildRoutingPrompt(String prompt, List<LlmRequest.Message> history) {
        StringBuilder builder = new StringBuilder();
        builder.append("User prompt:\n")
                .append(prompt != null ? prompt : "")
                .append("\n\nRecent history:\n");

        if (history == null || history.isEmpty()) {
            builder.append("- none\n");
        } else {
            history.stream()
                    .filter(message -> message != null && message.getRole() != null && message.resolvedTextContent() != null)
                    .skip(Math.max(0, history.size() - 4L))
                    .forEach(message -> builder.append("- ")
                            .append(message.getRole())
                            .append(": ")
                            .append(message.resolvedTextContent())
                            .append("\n"));
        }

        builder.append("\nReturn a routing decision for the next execution path.");
        return builder.toString();
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

    private static Map<String, Object> buildSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("routeMode", Map.of(
                "type", "string",
                "enum", Arrays.stream(TaskRouteMode.values()).map(Enum::name).toList()
        ));
        properties.put("complexity", Map.of(
                "type", "string",
                "enum", Arrays.stream(TaskComplexity.values()).map(Enum::name).toList()
        ));
        properties.put("shouldUseTools", Map.of("type", "boolean"));
        properties.put("shouldUseStrategy", Map.of("type", "boolean"));
        properties.put("outcomeStatus", Map.of(
                "type", "string",
                "enum", Arrays.stream(RunOutcomeStatus.values()).map(Enum::name).toList()
        ));
        properties.put("outcomeMessage", Map.of("type", "string"));
        properties.put("outcomeDetail", Map.of("type", "string"));
        properties.put("reason", Map.of("type", "string"));
        properties.put("confidence", Map.of("type", "number"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", properties);
        schema.put("required", List.of("routeMode", "complexity", "shouldUseTools", "shouldUseStrategy"));
        return schema;
    }
}
