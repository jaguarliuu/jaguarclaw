package com.jaguarliu.ai.llm;

import com.jaguarliu.ai.llm.model.LlmProviderConfig;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.llm.model.StructuredOutputSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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

    @Test
    @DisplayName("should allow native structured output for supported openai model")
    void shouldAllowNativeStructuredOutputForSupportedOpenAiModel() {
        LlmProperties properties = new LlmProperties();
        properties.setDefaultModel("openai:gpt-4o");
        LlmCapabilityService service = new LlmCapabilityService(properties);

        LlmRequest request = LlmRequest.builder()
                .providerId("openai")
                .model("gpt-4o")
                .messages(List.of(LlmRequest.Message.user("judge this task")))
                .structuredOutput(StructuredOutputSpec.builder()
                        .name("routing")
                        .jsonSchema(Map.of("type", "object"))
                        .build())
                .build();

        assertTrue(service.supportsNativeStructuredOutput(request));
    }

    @Test
    @DisplayName("should disable native structured output for unsupported qwen model")
    void shouldDisableNativeStructuredOutputForUnsupportedQwenModel() {
        LlmProperties properties = new LlmProperties();
        properties.setProviders(List.of(
                LlmProviderConfig.builder()
                        .id("dashscope")
                        .models(List.of("qwen-plus"))
                        .build()
        ));
        properties.setDefaultModel("dashscope:qwen-plus");
        LlmCapabilityService service = new LlmCapabilityService(properties);

        LlmRequest request = LlmRequest.builder()
                .providerId("dashscope")
                .model("qwen-plus")
                .messages(List.of(LlmRequest.Message.user("judge this task")))
                .structuredOutput(StructuredOutputSpec.builder()
                        .name("routing")
                        .jsonSchema(Map.of("type", "object"))
                        .build())
                .build();

        assertFalse(service.supportsNativeStructuredOutput(request));
    }

    @Test
    @DisplayName("should allow native structured output when provider explicitly declares model")
    void shouldAllowNativeStructuredOutputWhenProviderExplicitlyDeclaresModel() {
        LlmProperties properties = new LlmProperties();
        properties.setProviders(List.of(
                LlmProviderConfig.builder()
                        .id("dashscope")
                        .models(List.of("qwen-plus"))
                        .structuredOutputModels(List.of("qwen-plus"))
                        .build()
        ));
        properties.setDefaultModel("dashscope:qwen-plus");
        LlmCapabilityService service = new LlmCapabilityService(properties);

        LlmRequest request = LlmRequest.builder()
                .providerId("dashscope")
                .model("qwen-plus")
                .messages(List.of(LlmRequest.Message.user("judge this task")))
                .structuredOutput(StructuredOutputSpec.builder()
                        .name("routing")
                        .jsonSchema(Map.of("type", "object"))
                        .build())
                .build();

        assertTrue(service.supportsNativeStructuredOutput(request));
    }

}
