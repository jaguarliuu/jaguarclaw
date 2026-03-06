package com.jaguarliu.ai.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.storage.entity.MessageEntity;
import com.jaguarliu.ai.storage.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageService multimodal tests")
class MessageServiceMultimodalTest {

    @Mock
    private MessageRepository messageRepository;

    private MessageService messageService;

    @BeforeEach
    void setUp() {
        messageService = new MessageService(messageRepository, new ObjectMapper());
    }

    @Test
    @DisplayName("saveUserMessage should persist payload json")
    void saveUserMessageShouldPersistPayloadJson() {
        when(messageRepository.save(any(MessageEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LlmRequest.Message message = LlmRequest.Message.userWithTextAndImages("look", List.of(
                LlmRequest.ImagePart.builder()
                        .filePath("uploads/demo.png")
                        .storagePath("/tmp/demo.png")
                        .mimeType("image/png")
                        .fileName("demo.png")
                        .build()
        ));

        MessageEntity entity = messageService.saveUserMessage("session-1", "run-1", message, MessageService.DEFAULT_PRINCIPAL_ID);

        assertNotNull(entity.getPayloadJson());
        assertEquals("look", entity.getContent());

        ArgumentCaptor<MessageEntity> captor = ArgumentCaptor.forClass(MessageEntity.class);
        verify(messageRepository).save(captor.capture());
        assertNotNull(captor.getValue().getPayloadJson());
    }

    @Test
    @DisplayName("toRequestMessages should rebuild from payload json")
    void toRequestMessagesShouldRebuildFromPayloadJson() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        LlmRequest.Message raw = LlmRequest.Message.userWithTextAndImages("look", List.of(
                LlmRequest.ImagePart.builder()
                        .filePath("uploads/demo.png")
                        .storagePath("/tmp/demo.png")
                        .mimeType("image/png")
                        .fileName("demo.png")
                        .build()
        ));

        MessageEntity entity = MessageEntity.builder()
                .id("m1")
                .sessionId("session-1")
                .runId("run-1")
                .role("user")
                .content("look")
                .payloadJson(objectMapper.writeValueAsString(raw))
                .build();

        LlmRequest.Message restored = messageService.toRequestMessages(List.of(entity)).get(0);
        assertEquals("user", restored.getRole());
        assertNotNull(restored.getParts());
        assertEquals(2, restored.getParts().size());
        assertEquals("/tmp/demo.png", restored.getParts().get(1).getImage().getStoragePath());
    }
}
