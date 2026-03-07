package com.jaguarliu.ai.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.llm.model.LlmRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 构建结构化输出 fallback prompt。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StructuredOutputPromptBuilder {

    private final ObjectMapper objectMapper;

    public LlmRequest buildPromptJsonFallbackRequest(LlmRequest request) {
        List<LlmRequest.Message> fallbackMessages = new ArrayList<>();
        fallbackMessages.add(LlmRequest.Message.system(buildPromptJsonFallbackInstruction(request)));
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            fallbackMessages.addAll(request.getMessages());
        }

        return LlmRequest.builder()
                .messages(fallbackMessages)
                .model(request.getModel())
                .temperature(request.getTemperature())
                .maxTokens(request.getMaxTokens())
                .stream(request.getStream())
                .tools(request.getTools())
                .toolChoice(request.getToolChoice())
                .providerId(request.getProviderId())
                .structuredOutput(null)
                .build();
    }

    String buildPromptJsonFallbackInstruction(LlmRequest request) {
        String schema = "{}";
        if (request.getStructuredOutput() != null && request.getStructuredOutput().getJsonSchema() != null) {
            try {
                schema = objectMapper.writeValueAsString(request.getStructuredOutput().getJsonSchema());
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize structured output schema for prompt fallback", e);
            }
        }
        return "Return valid JSON only. Do not include markdown fences or explanation. JSON schema: " + schema;
    }
}
