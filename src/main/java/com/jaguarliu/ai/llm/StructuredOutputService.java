package com.jaguarliu.ai.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * 结构化输出解析服务。
 */
@Component
public class StructuredOutputService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public <T> T parse(String raw, Class<T> targetType) {
        String payload = extractJsonPayload(raw);
        try {
            return OBJECT_MAPPER.readValue(payload, targetType);
        } catch (Exception e) {
            throw new StructuredOutputException("Failed to parse structured output", e);
        }
    }

    String extractJsonPayload(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new StructuredOutputException("Structured output is empty");
        }

        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int closingFence = trimmed.lastIndexOf("```");
            if (firstNewline < 0 || closingFence <= firstNewline) {
                throw new StructuredOutputException("Invalid fenced structured output");
            }
            trimmed = trimmed.substring(firstNewline + 1, closingFence).trim();
        }

        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            throw new StructuredOutputException("Structured output is not valid JSON");
        }
        return trimmed;
    }
}
