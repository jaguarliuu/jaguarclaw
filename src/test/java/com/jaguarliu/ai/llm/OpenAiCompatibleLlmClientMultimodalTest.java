package com.jaguarliu.ai.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.llm.model.LlmChunk;
import com.jaguarliu.ai.llm.model.LlmRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OpenAiCompatibleLlmClient multimodal tests")
class OpenAiCompatibleLlmClientMultimodalTest {

    @Test
    @DisplayName("should encode image parts as openai content array")
    void shouldEncodeImagePartsAsOpenAiContentArray() throws Exception {
        Path image = Files.createTempFile("jc-vision-", ".png");
        Files.write(image, new byte[] {1, 2, 3, 4});

        LlmProperties properties = new LlmProperties();
        properties.setEndpoint("http://localhost:11434/v1");
        ObjectMapper objectMapper = new ObjectMapper();
        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(properties, objectMapper);

        LlmRequest request = LlmRequest.builder()
                .messages(List.of(LlmRequest.Message.userWithTextAndImages("describe", List.of(
                        LlmRequest.ImagePart.builder()
                                .filePath("uploads/demo.png")
                                .storagePath(image.toString())
                                .mimeType("image/png")
                                .fileName("demo.png")
                                .build()
                ))))
                .build();

        Method method = OpenAiCompatibleLlmClient.class.getDeclaredMethod("buildApiRequest", LlmRequest.class, boolean.class);
        method.setAccessible(true);
        Object apiRequest = method.invoke(client, request, false);
        String json = objectMapper.writeValueAsString(apiRequest);

        assertTrue(json.contains("\"type\":\"image_url\""), json);
        assertTrue(json.contains("data:image/png;base64,"), json);
        assertTrue(json.contains("\"text\":\"describe\""), json);
        assertTrue(json.indexOf("\"type\":\"image_url\"") < json.indexOf("\"text\":\"describe\""), json);
    }

    @Test
    @DisplayName("should parse streaming array content into text delta")
    void shouldParseStreamingArrayContentIntoTextDelta() throws Exception {
        LlmProperties properties = new LlmProperties();
        properties.setEndpoint("http://localhost:11434/v1");
        ObjectMapper objectMapper = new ObjectMapper();
        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(properties, objectMapper);

        Method method = OpenAiCompatibleLlmClient.class.getDeclaredMethod("parseSseChunk", String.class, java.util.Map.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Flux<LlmChunk> flux = (Flux<LlmChunk>) method.invoke(
                client,
                "data: {\"choices\":[{\"delta\":{\"content\":[{\"type\":\"text\",\"text\":\"图片里是一只猫\"}]},\"finish_reason\":null}]}",
                new HashMap<Integer, Object>()
        );

        List<LlmChunk> chunks = flux.collectList().block();
        assertEquals(1, chunks.size());
        assertEquals("图片里是一只猫", chunks.get(0).getDelta());
    }
}
