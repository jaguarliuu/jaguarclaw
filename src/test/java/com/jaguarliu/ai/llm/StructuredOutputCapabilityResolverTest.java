package com.jaguarliu.ai.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.llm.model.LlmProviderConfig;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.llm.model.StructuredOutputSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("StructuredOutputCapabilityResolver tests")
class StructuredOutputCapabilityResolverTest {

    @Test
    @DisplayName("should disable native structured output for unsupported qwen model")
    void shouldDisableNativeStructuredOutputForUnsupportedQwenModel() {
        LlmProperties properties = new LlmProperties();
        properties.setProviders(List.of(
                LlmProviderConfig.builder()
                        .id("dashscope")
                        .name("DashScope")
                        .models(List.of("qwen-plus"))
                        .build()
        ));
        properties.setDefaultModel("dashscope:qwen-plus");

        StructuredOutputCapabilityResolver resolver =
                new StructuredOutputCapabilityResolver(new LlmCapabilityService(properties));
        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(properties, new ObjectMapper());

        LlmRequest request = LlmRequest.builder()
                .providerId("dashscope")
                .model("qwen-plus")
                .messages(List.of(LlmRequest.Message.user("route this")))
                .structuredOutput(StructuredOutputSpec.builder()
                        .name("task_routing_decision")
                        .jsonSchema(Map.of("type", "object"))
                        .strict(true)
                        .fallbackToPromptJson(true)
                        .build())
                .build();

        assertFalse(resolver.shouldUseNativeStructuredOutput(client, request));
    }

    @Test
    @DisplayName("should allow native structured output for supported openai model")
    void shouldAllowNativeStructuredOutputForSupportedOpenAiModel() {
        LlmProperties properties = new LlmProperties();
        properties.setProviders(List.of(
                LlmProviderConfig.builder()
                        .id("openai")
                        .name("OpenAI")
                        .models(List.of("gpt-4o"))
                        .build()
        ));
        properties.setDefaultModel("openai:gpt-4o");

        StructuredOutputCapabilityResolver resolver =
                new StructuredOutputCapabilityResolver(new LlmCapabilityService(properties));
        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(properties, new ObjectMapper());

        LlmRequest request = LlmRequest.builder()
                .providerId("openai")
                .model("gpt-4o")
                .messages(List.of(LlmRequest.Message.user("route this")))
                .structuredOutput(StructuredOutputSpec.builder()
                        .name("task_routing_decision")
                        .jsonSchema(Map.of("type", "object"))
                        .strict(true)
                        .fallbackToPromptJson(true)
                        .build())
                .build();

        assertTrue(resolver.shouldUseNativeStructuredOutput(client, request));
    }
}
