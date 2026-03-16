package com.jaguarliu.ai.im.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.*;
import com.jaguarliu.ai.im.entity.ImMessageEntity;
import com.jaguarliu.ai.im.repository.ImConversationRepository;
import com.jaguarliu.ai.im.repository.ImMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImConversationClearHandler implements RpcHandler {

    private final ImMessageRepository messageRepo;
    private final ImConversationRepository conversationRepo;
    private final ObjectMapper objectMapper;

    @Override
    public String getMethod() { return "im.conversation.clear"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> p = objectMapper.convertValue(request.getPayload(), Map.class);
            String conversationId = (String) p.get("conversationId");
            if (conversationId == null) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "conversationId required");
            }

            List<ImMessageEntity> messages =
                messageRepo.findByConversationIdOrderByCreatedAtAscIdAsc(conversationId);

            long deletedFiles = 0;
            long freedBytes   = 0;

            for (ImMessageEntity msg : messages) {
                if (msg.getLocalFilePath() != null) {
                    try {
                        Path filePath = Paths.get(msg.getLocalFilePath());
                        Path dir      = filePath.getParent();

                        if (Files.exists(filePath)) {
                            freedBytes += Files.size(filePath);
                            Files.delete(filePath);
                            deletedFiles++;
                        }

                        // Remove the per-message directory if now empty
                        if (dir != null && Files.exists(dir)) {
                            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
                                if (!ds.iterator().hasNext()) {
                                    Files.delete(dir);
                                }
                            }
                        }
                    } catch (IOException e) {
                        log.warn("[IM] Failed to delete file {} for message {}", msg.getLocalFilePath(), msg.getId(), e);
                    }
                }
            }

            int deletedMessages = messages.size();
            messageRepo.deleteAll(messages);

            // Reset conversation preview but keep the entry so it still appears in the list
            conversationRepo.findById(conversationId).ifPresent(conv -> {
                conv.setLastMsg(null);
                conv.setLastMsgAt(null);
                conv.setUnreadCount(0);
                conversationRepo.save(conv);
            });

            log.info("[IM] Cleared conversation {}: {} messages, {} files ({} bytes)", conversationId, deletedMessages, deletedFiles, freedBytes);

            return RpcResponse.success(request.getId(), Map.of(
                "deletedMessages", deletedMessages,
                "deletedFiles",    deletedFiles,
                "freedBytes",      freedBytes
            ));
        });
    }
}
