package com.jaguarliu.ai.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.llm.model.StructuredOutputSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OpenAiCompatibleLlmClient structured output tests")
class OpenAiCompatibleLlmClientStructuredOutputTest {

    @Test
    @DisplayName("should add response_format schema to provider request")
    void shouldAddResponseFormatSchemaToProviderRequest() throws Exception {
        LlmProperties properties = new LlmProperties();
        properties.setEndpoint("http://localhost:11434/v1");
        ObjectMapper objectMapper = new ObjectMapper();
        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(
                properties, objectMapper);

        LlmRequest request = LlmRequest.builder()
                .messages(List.of(LlmRequest.Message.user("judge this task")))
                .structuredOutput(StructuredOutputSpec.builder()
                        .name("verifier_decision")
                        .jsonSchema(Map.of("type", "object"))
                        .strict(true)
                        .fallbackToPromptJson(true)
                        .build())
                .build();

        Method method = OpenAiCompatibleLlmClient.class.getDeclaredMethod("buildApiRequest", LlmRequest.class, boolean.class);
        method.setAccessible(true);
        Object apiRequest = method.invoke(client, request, false);

        Object responseFormat = ReflectionTestUtils.getField(apiRequest, "responseFormat");
        assertNotNull(responseFormat);
        String json = objectMapper.writeValueAsString(apiRequest);
        assertTrue(json.contains("response_format"), json);
        assertTrue(json.contains("json_schema"), json);
    }
}
