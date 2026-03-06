package com.jaguarliu.ai.llm;

import com.jaguarliu.ai.llm.model.LlmRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("LlmRequest multimodal tests")
class LlmRequestMultimodalTest {

    @Test
    @DisplayName("legacy content should fallback to text part")
    void legacyContentShouldFallbackToTextPart() {
        LlmRequest.Message message = LlmRequest.Message.user("hello");

        List<LlmRequest.ContentPart> parts = message.resolvedParts();

        assertEquals(1, parts.size());
        assertEquals("text", parts.get(0).getType());
        assertEquals("hello", parts.get(0).getText());
        assertEquals("hello", message.resolvedTextContent());
    }

    @Test
    @DisplayName("user message should include text and images")
    void userMessageShouldIncludeTextAndImages() {
        LlmRequest.ImagePart image = LlmRequest.ImagePart.builder()
                .filePath("uploads/demo.png")
                .mimeType("image/png")
                .fileName("demo.png")
                .build();

        LlmRequest.Message message = LlmRequest.Message.userWithTextAndImages("describe this", List.of(image));

        assertEquals("describe this", message.getContent());
        assertNotNull(message.getParts());
        assertEquals(2, message.getParts().size());
        assertEquals("text", message.getParts().get(0).getType());
        assertEquals("image", message.getParts().get(1).getType());
        assertEquals("uploads/demo.png", message.getParts().get(1).getImage().getFilePath());
    }
}
