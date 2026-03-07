package com.jaguarliu.ai.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("StructuredOutputService tests")
class StructuredOutputServiceTest {

    private final StructuredOutputService service = new StructuredOutputService();

    @Test
    @DisplayName("should parse plain json payload")
    void shouldParsePlainJsonPayload() {
        Decision result = service.parse("{\"terminal\":true,\"reason\":\"blocked\"}", Decision.class);

        assertEquals(true, result.terminal());
        assertEquals("blocked", result.reason());
    }

    @Test
    @DisplayName("should parse fenced json payload")
    void shouldParseFencedJsonPayload() {
        Decision result = service.parse("""
                ```json
                {"terminal":false,"reason":"continue"}
                ```
                """, Decision.class);

        assertEquals(false, result.terminal());
        assertEquals("continue", result.reason());
    }

    @Test
    @DisplayName("should fail on invalid json")
    void shouldFailOnInvalidJson() {
        assertThrows(StructuredOutputException.class,
                () -> service.parse("not-json", Decision.class));
    }

    private record Decision(boolean terminal, String reason) {
    }
}
