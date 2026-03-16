package com.jaguarliu.ai.im.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.*;
import com.jaguarliu.ai.im.dto.ImMessageDto;
import com.jaguarliu.ai.im.repository.ImConversationRepository;
import com.jaguarliu.ai.im.repository.ImMessageRepository;
import com.jaguarliu.ai.im.service.ImIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.Map;

@Component @RequiredArgsConstructor
public class ImMessagesListHandler implements RpcHandler {
    private final ImMessageRepository messageRepo;
    private final ImConversationRepository conversationRepo;
    private final ImIdentityService identityService;
    private final ObjectMapper objectMapper;

    @Override public String getMethod() { return "im.messages.list"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> p = objectMapper.convertValue(request.getPayload(), Map.class);
            String conversationId = (String) p.get("conversationId");
            if (conversationId == null) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "conversationId required");
            }
            String selfNodeId = identityService.getCached().getNodeId();
            List<ImMessageDto> msgs = messageRepo
                .findByConversationIdOrderByCreatedAtAscIdAsc(conversationId).stream()
                .map(m -> {
                    ImMessageDto.ImMessageDtoBuilder b = ImMessageDto.builder()
                        .id(m.getId())
                        .conversationId(m.getConversationId())
                        .senderNodeId(m.getSenderNodeId())
                        .isMe(m.getSenderNodeId().equals(selfNodeId))
                        .type(m.getType())
                        .content(m.getContent())
                        .createdAt(m.getCreatedAt().toString() + "Z")
                        .status(m.getStatus());

                    if (m.getLocalFilePath() != null) {
                        b.fileUrl("/api/im/files/" + m.getId());
                        try {
                            JsonNode tree = objectMapper.readTree(m.getContent());
                            b.fileName(tree.path("filename").asText(null));
                            b.mimeType(tree.path("mimeType").asText(null));
                            long sz = tree.path("size").asLong(-1);
                            if (sz >= 0) b.fileSize(sz);
                        } catch (Exception ignored) {}
                    }
                    return b.build();
                })
                .toList();

            // Mark conversation as read
            conversationRepo.findById(conversationId).ifPresent(conv -> {
                conv.setUnreadCount(0);
                conversationRepo.save(conv);
            });

            return RpcResponse.success(request.getId(), msgs);
        });
    }
}

