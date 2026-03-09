package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.llm.StructuredOutputExecutor;
import com.jaguarliu.ai.llm.model.LlmRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
                    .routeMode(TaskRouteMode.REACT)
                    .complexity(TaskComplexity.HEAVY)
                    .reason("image attachments require execution path")
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
            return fallbackDecision(prompt);
        }
    }

    private TaskRoutingDecision fallbackDecision(String prompt) {
        PolicyDecision fallback = policySupervisor.evaluate(prompt, List.of());
        return TaskRoutingDecision.builder()
                .routeMode(TaskRouteMode.REACT)
                .complexity(fallback.complexity())
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
                - DIRECT: pure answer-only response, no tools, no execution, no environment observation needed.
                - REACT: any request that needs tools, files, browser/web access, environment observation, skills, or multi-step execution.
                Use the full recent history to decide whether the current user message is continuing an executable task.
                Prefer REACT whenever execution may be needed.
                Return only the structured routing decision.
                """.trim()));
        messages.add(LlmRequest.Message.user(buildRoutingPrompt(prompt, history)));

        LlmRequest.LlmRequestBuilder builder = LlmRequest.builder()
                .messages(messages)
                .temperature(0.0)
                .maxTokens(250);

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
                    .skip(Math.max(0, history.size() - 6L))
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
                "enum", List.of(TaskRouteMode.DIRECT.name(), TaskRouteMode.REACT.name())
        ));
        properties.put("complexity", Map.of(
                "type", "string",
                "enum", List.of(TaskComplexity.DIRECT.name(), TaskComplexity.HEAVY.name())
        ));
        properties.put("reason", Map.of("type", "string"));
        properties.put("confidence", Map.of("type", "number"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", properties);
        schema.put("required", List.of("routeMode", "complexity"));
        return schema;
    }
}
