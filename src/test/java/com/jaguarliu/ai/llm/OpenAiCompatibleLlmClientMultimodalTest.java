package com.jaguarliu.ai.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.llm.model.LlmRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
    }
}
