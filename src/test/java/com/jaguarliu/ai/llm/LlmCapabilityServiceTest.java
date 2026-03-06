package com.jaguarliu.ai.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LlmCapabilityService tests")
class LlmCapabilityServiceTest {

    @Test
    @DisplayName("should detect common vision models")
    void shouldDetectCommonVisionModels() {
        LlmProperties properties = new LlmProperties();
        LlmCapabilityService service = new LlmCapabilityService(properties);

        assertTrue(service.supportsVision("openai:gpt-4o"));
        assertTrue(service.supportsVision("anthropic:claude-3-7-sonnet"));
        assertTrue(service.supportsVision("google:gemini-2.0-flash"));
        assertFalse(service.supportsVision("openai:gpt-3.5-turbo"));
    }
}
