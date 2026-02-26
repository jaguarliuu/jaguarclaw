package com.jaguarliu.ai.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ToolDefinition 渐进式加载测试
 */
class ToolDefinitionTest {

    private ToolDefinition definition;

    @BeforeEach
    void setUp() {
        definition = ToolDefinition.builder()
                .name("test_tool")
                .description("A test tool for unit testing")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "input", Map.of("type", "string", "description", "Input value")
                        ),
                        "required", List.of("input")
                ))
                .tags(List.of("test", "unit"))
                .riskLevel("low")
                .parameterSummary("input (required): The input value to process")
                .example("test_tool({ input: 'hello' })")
                .build();
    }

    @Test
    @DisplayName("L0 format should only contain name and description")
    void testL0Format() {
        Map<String, Object> l0 = definition.toOpenAiFormatL0();

        assertEquals("function", l0.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> function = (Map<String, Object>) l0.get("function");

        assertEquals("test_tool", function.get("name"));
        assertTrue(((String) function.get("description")).contains("A test tool"));
        // L0 不应该包含 parameters
        assertNull(function.get("parameters"));
    }

    @Test
    @DisplayName("L1 format should contain parameter summary")
    void testL1Format() {
        Map<String, Object> l1 = definition.toOpenAiFormatL1();

        assertEquals("function", l1.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> function = (Map<String, Object>) l1.get("function");

        assertEquals("test_tool", function.get("name"));
        String desc = (String) function.get("description");
        assertTrue(desc.contains("test tool"));
        assertTrue(desc.contains("input (required)"));
    }

    @Test
    @DisplayName("L2 format should contain full parameters schema")
    void testL2Format() {
        Map<String, Object> l2 = definition.toOpenAiFormat();

        assertEquals("function", l2.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> function = (Map<String, Object>) l2.get("function");

        assertEquals("test_tool", function.get("name"));
        assertNotNull(function.get("parameters"));

        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) function.get("parameters");

        assertEquals("object", params.get("type"));
        assertNotNull(params.get("properties"));
    }

    @Test
    @DisplayName("L0 tokens should be less than L1 tokens")
    void testTokenEstimation() {
        int l0Tokens = definition.estimateL0Tokens();
        int l1Tokens = definition.estimateL1Tokens();

        assertTrue(l0Tokens > 0, "L0 tokens should be positive");
        assertTrue(l1Tokens > l0Tokens, "L1 tokens should be greater than L0 tokens");
        assertTrue(l0Tokens < 100, "L0 tokens should be less than 100");
        assertTrue(l1Tokens < 500, "L1 tokens should be less than 500");
    }

    @Test
    @DisplayName("Tags should appear in L0 description")
    void testTagsInL0() {
        Map<String, Object> l0 = definition.toOpenAiFormatL0();

        @SuppressWarnings("unchecked")
        Map<String, Object> function = (Map<String, Object>) l0.get("function");

        String desc = (String) function.get("description");
        assertTrue(desc.contains("[tags:"));
        assertTrue(desc.contains("test"));
        assertTrue(desc.contains("unit"));
    }
}
