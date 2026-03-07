package com.jaguarliu.ai.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.llm.model.LlmResponse;
import com.jaguarliu.ai.llm.model.StructuredLlmResult;
import com.jaguarliu.ai.llm.model.StructuredOutputSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                properties, objectMapper, new StructuredOutputService());

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

    @Test
    @DisplayName("structured call should fallback to prompt json when primary call fails")
    void structuredCallShouldFallbackToPromptJsonWhenPrimaryCallFails() {
        class TestClient extends OpenAiCompatibleLlmClient {
            private final List<LlmRequest> requests = new ArrayList<>();

            TestClient() {
                super(new LlmProperties(), new ObjectMapper(), new StructuredOutputService());
            }

            @Override
            public LlmResponse chat(LlmRequest request) {
                requests.add(request);
                if (requests.size() == 1) {
                    throw new RuntimeException("provider does not support response_format");
                }
                return LlmResponse.builder().content("{\"terminal\":true,\"reason\":\"blocked\"}").build();
            }
        }

        TestClient client = new TestClient();
        LlmRequest request = LlmRequest.builder()
                .messages(List.of(LlmRequest.Message.user("judge this task")))
                .structuredOutput(StructuredOutputSpec.builder()
                        .name("verifier_decision")
                        .jsonSchema(Map.of("type", "object"))
                        .strict(true)
                        .fallbackToPromptJson(true)
                        .build())
                .build();

        StructuredLlmResult<Map> result = client.structured(request, Map.class);

        assertEquals(2, client.requests.size());
        assertTrue(client.requests.get(1).getMessages().get(0).getContent().contains("Return valid JSON only"));
        assertEquals(true, result.getValue().get("terminal"));
        assertFalse(result.getNativeStructuredOutput());
        assertTrue(result.getFallbackUsed());
    }
}
