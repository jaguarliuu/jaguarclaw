package com.jaguarliu.ai.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.storage.entity.MessageEntity;
import com.jaguarliu.ai.storage.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Message 服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    public static final String DEFAULT_PRINCIPAL_ID = "local-default";

    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    /**
     * 保存用户消息
     */
    @Transactional
    public MessageEntity saveUserMessage(String sessionId, String runId, String content) {
        return saveUserMessage(sessionId, runId, content, DEFAULT_PRINCIPAL_ID);
    }

    /**
     * 保存用户消息（指定 ownerPrincipalId）
     */
    @Transactional
    public MessageEntity saveUserMessage(String sessionId, String runId, String content, String ownerPrincipalId) {
        return saveUserMessage(sessionId, runId, LlmRequest.Message.user(content), ownerPrincipalId);
    }

    /**
     * 保存结构化用户消息（指定 ownerPrincipalId）
     */
    @Transactional
    public MessageEntity saveUserMessage(String sessionId, String runId, LlmRequest.Message message, String ownerPrincipalId) {
        return saveStructuredMessage(sessionId, runId, "user", message, ownerPrincipalId);
    }

    /**
     * 保存助手消息
     */
    @Transactional
    public MessageEntity saveAssistantMessage(String sessionId, String runId, String content) {
        return saveAssistantMessage(sessionId, runId, content, DEFAULT_PRINCIPAL_ID);
    }

    /**
     * 保存助手消息（指定 ownerPrincipalId）
     */
    @Transactional
    public MessageEntity saveAssistantMessage(String sessionId, String runId, String content, String ownerPrincipalId) {
        return saveMessage(sessionId, runId, "assistant", content, ownerPrincipalId, null);
    }

    /**
     * 保存子代理 announce 消息到父会话
     */
    @Transactional
    public MessageEntity saveSubagentAnnounce(String parentSessionId,
                                               String parentRunId,
                                               String subRunId,
                                               String subSessionId,
                                               String content) {
        return saveSubagentAnnounce(parentSessionId, parentRunId, subRunId, subSessionId, content, DEFAULT_PRINCIPAL_ID);
    }

    /**
     * 保存子代理 announce 消息（指定 ownerPrincipalId）
     */
    @Transactional
    public MessageEntity saveSubagentAnnounce(String parentSessionId,
                                               String parentRunId,
                                               String subRunId,
                                               String subSessionId,
                                               String content,
                                               String ownerPrincipalId) {
        MessageEntity message = MessageEntity.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(parentSessionId)
                .runId(parentRunId)
                .role("assistant")
                .content(content)
                .ownerPrincipalId(ownerPrincipalId)
                .build();

        message = messageRepository.save(message);
        log.info("Saved subagent announce: parentSessionId={}, subRunId={}, subSessionId={}",
                parentSessionId, subRunId, subSessionId);
        return message;
    }

    private MessageEntity saveStructuredMessage(String sessionId, String runId, String role,
                                                LlmRequest.Message message, String ownerPrincipalId) {
        String content = message != null ? message.resolvedTextContent() : null;
        if (content == null) {
            content = "";
        }
        return saveMessage(sessionId, runId, role, content, ownerPrincipalId, serializePayload(message));
    }

    private MessageEntity saveMessage(String sessionId, String runId, String role, String content,
                                      String ownerPrincipalId, String payloadJson) {
        MessageEntity message = MessageEntity.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .runId(runId)
                .role(role)
                .content(content)
                .ownerPrincipalId(ownerPrincipalId)
                .payloadJson(payloadJson)
                .build();

        message = messageRepository.save(message);
        log.debug("Saved message: sessionId={}, role={}, length={}, structured={}",
                sessionId, role, content.length(), payloadJson != null);
        return message;
    }

    private String serializePayload(LlmRequest.Message message) {
        if (message == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize message payload", e);
        }
    }

    public List<MessageEntity> getSessionHistory(String sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    public List<MessageEntity> getSessionHistory(String sessionId, String ownerPrincipalId) {
        return messageRepository.findBySessionIdAndOwnerPrincipalIdOrderByCreatedAtAsc(sessionId, ownerPrincipalId);
    }

    public List<MessageEntity> getSessionHistory(String sessionId, int limit) {
        List<MessageEntity> all = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        if (all.size() <= limit) {
            return all;
        }
        return all.subList(all.size() - limit, all.size());
    }

    public List<MessageEntity> getSessionHistory(String sessionId, int limit, String ownerPrincipalId) {
        List<MessageEntity> all = messageRepository.findBySessionIdAndOwnerPrincipalIdOrderByCreatedAtAsc(sessionId, ownerPrincipalId);
        if (all.size() <= limit) {
            return all;
        }
        return all.subList(all.size() - limit, all.size());
    }

    public List<LlmRequest.Message> toRequestMessages(List<MessageEntity> messages) {
        return messages.stream()
                .map(this::toRequestMessage)
                .toList();
    }

    private LlmRequest.Message toRequestMessage(MessageEntity entity) {
        if (entity.getPayloadJson() != null && !entity.getPayloadJson().isBlank()) {
            try {
                LlmRequest.Message message = objectMapper.readValue(entity.getPayloadJson(), LlmRequest.Message.class);
                if (message.getRole() == null || message.getRole().isBlank()) {
                    message.setRole(entity.getRole());
                }
                if (message.getContent() == null) {
                    message.setContent(entity.getContent());
                }
                return message;
            } catch (Exception e) {
                log.warn("Failed to deserialize message payload: id={}, error={}", entity.getId(), e.getMessage());
            }
        }
        return LlmRequest.Message.builder().role(entity.getRole()).content(entity.getContent()).build();
    }
}
