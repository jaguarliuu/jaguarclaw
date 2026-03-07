package com.jaguarliu.ai.llm.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LlmRequest structured output tests")
class LlmRequestStructuredOutputTest {

    @Test
    @DisplayName("request should carry optional structured output spec")
    void requestShouldCarryOptionalStructuredOutputSpec() {
        StructuredOutputSpec spec = StructuredOutputSpec.builder()
                .name("verifier_decision")
                .jsonSchema(Map.of("type", "object"))
                .strict(true)
                .fallbackToPromptJson(true)
                .build();

        LlmRequest request = LlmRequest.builder()
                .model("gpt-test")
                .structuredOutput(spec)
                .build();

        assertNotNull(request.getStructuredOutput());
        assertEquals("verifier_decision", request.getStructuredOutput().getName());
        assertEquals("object", request.getStructuredOutput().getJsonSchema().get("type"));
        assertTrue(request.getStructuredOutput().getStrict());
        assertTrue(request.getStructuredOutput().getFallbackToPromptJson());
    }

    @Test
    @DisplayName("structured result should preserve raw text and fallback metadata")
    void structuredResultShouldPreserveRawTextAndFallbackMetadata() {
        StructuredLlmResult<Map> result = StructuredLlmResult.<Map>builder()
                .value(Map.of("terminal", true))
                .rawText("{\"terminal\":true}")
                .nativeStructuredOutput(false)
                .fallbackUsed(true)
                .build();

        assertEquals(true, result.getValue().get("terminal"));
        assertEquals("{\"terminal\":true}", result.getRawText());
        assertFalse(result.getNativeStructuredOutput());
        assertTrue(result.getFallbackUsed());
    }
}
