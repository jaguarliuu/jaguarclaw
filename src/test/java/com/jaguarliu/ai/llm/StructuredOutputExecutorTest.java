package com.jaguarliu.ai.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.llm.model.LlmChunk;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.llm.model.LlmResponse;
import com.jaguarliu.ai.llm.model.StructuredLlmResult;
import com.jaguarliu.ai.llm.model.StructuredOutputSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("StructuredOutputExecutor Tests")
class StructuredOutputExecutorTest {

    @Test
    @DisplayName("should fallback to prompt json when primary structured call fails")
    void shouldFallbackToPromptJsonWhenPrimaryStructuredCallFails() {
        class TestClient implements LlmClient {
            private final List<LlmRequest> requests = new ArrayList<>();

            @Override
            public LlmResponse chat(LlmRequest request) {
                requests.add(request);
                if (requests.size() == 1) {
                    throw new RuntimeException("provider does not support response_format");
                }
                return LlmResponse.builder().content("{\"terminal\":true,\"reason\":\"blocked\"}").build();
            }

            @Override
            public Flux<LlmChunk> stream(LlmRequest request) {
                throw new UnsupportedOperationException();
            }
        }

        TestClient client = new TestClient();
        StructuredOutputExecutor executor = new StructuredOutputExecutor(
                client,
                new StructuredOutputService(),
                new StructuredOutputPromptBuilder(new ObjectMapper()),
                new StructuredOutputCapabilityResolver() {
                    @Override
                    public boolean shouldUseNativeStructuredOutput(LlmClient llmClient, LlmRequest request) {
                        return true;
                    }
                }
        );

        LlmRequest request = LlmRequest.builder()
                .messages(List.of(LlmRequest.Message.user("judge this task")))
                .structuredOutput(StructuredOutputSpec.builder()
                        .name("verifier_decision")
                        .jsonSchema(Map.of("type", "object"))
                        .strict(true)
                        .fallbackToPromptJson(true)
                        .build())
                .build();

        StructuredLlmResult<Map> result = executor.execute(request, Map.class);

        assertEquals(2, client.requests.size());
        assertTrue(client.requests.get(1).getMessages().get(0).getContent().contains("Return valid JSON only"));
        assertEquals(true, result.getValue().get("terminal"));
        assertFalse(result.getNativeStructuredOutput());
        assertTrue(result.getFallbackUsed());
    }

    @Test
    @DisplayName("should use native structured request when capability resolver allows it")
    void shouldUseNativeStructuredRequestWhenCapabilityResolverAllowsIt() {
        class TestClient implements LlmClient {
            private LlmRequest request;

            @Override
            public LlmResponse chat(LlmRequest request) {
                this.request = request;
                return LlmResponse.builder().content("{\"terminal\":false}").build();
            }

            @Override
            public Flux<LlmChunk> stream(LlmRequest request) {
                throw new UnsupportedOperationException();
            }
        }

        TestClient client = new TestClient();
        StructuredOutputExecutor executor = new StructuredOutputExecutor(
                client,
                new StructuredOutputService(),
                new StructuredOutputPromptBuilder(new ObjectMapper()),
                new StructuredOutputCapabilityResolver() {
                    @Override
                    public boolean shouldUseNativeStructuredOutput(LlmClient llmClient, LlmRequest request) {
                        return true;
                    }
                }
        );

        LlmRequest request = LlmRequest.builder()
                .messages(List.of(LlmRequest.Message.user("judge this task")))
                .structuredOutput(StructuredOutputSpec.builder()
                        .name("verifier_decision")
                        .jsonSchema(Map.of("type", "object"))
                        .strict(true)
                        .fallbackToPromptJson(true)
                        .build())
                .build();

        executor.execute(request, Map.class);

        assertEquals("verifier_decision", client.request.getStructuredOutput().getName());
    }
}
