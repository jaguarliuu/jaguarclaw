package com.jaguarliu.ai.im.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.im.dto.ImNodeDto;
import com.jaguarliu.ai.im.entity.ImContactEntity;
import com.jaguarliu.ai.im.entity.ImConversationEntity;
import com.jaguarliu.ai.im.repository.ImContactRepository;
import com.jaguarliu.ai.im.repository.ImConversationRepository;
import com.jaguarliu.ai.im.service.ImRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * im.chat.start { targetNodeId }
 * Auto-creates contact + conversation from the online node registry (no pairing ceremony).
 */
@Component
@RequiredArgsConstructor
public class ImChatStartHandler implements RpcHandler {

    private final ImRegistryService registryService;
    private final ImContactRepository contactRepo;
    private final ImConversationRepository conversationRepo;
    private final ObjectMapper objectMapper;

    @Override
    public String getMethod() { return "im.chat.start"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> params = objectMapper.convertValue(request.getPayload(), Map.class);
            String targetNodeId = (String) params.get("targetNodeId");
            if (targetNodeId == null || targetNodeId.isBlank()) {
                return RpcResponse.error(request.getId(), "BAD_REQUEST", "Missing targetNodeId");
            }

            Optional<ImNodeDto> nodeOpt = registryService.getNode(targetNodeId);
            if (nodeOpt.isEmpty()) {
                return RpcResponse.error(request.getId(), "NOT_FOUND", "Node not found or offline");
            }
            ImNodeDto node = nodeOpt.get();

            // Upsert contact
            ImContactEntity contact = contactRepo.findById(targetNodeId).orElseGet(() ->
                ImContactEntity.builder()
                    .nodeId(node.getNodeId())
                    .pairedAt(LocalDateTime.now())
                    .status("active")
                    .build()
            );
            contact.setDisplayName(node.getDisplayName());
            contact.setPublicKeyEd25519(node.getPublicKeyEd25519());
            contact.setPublicKeyX25519(node.getPublicKeyX25519());
            if (node.getAvatarStyle() != null) contact.setAvatarStyle(node.getAvatarStyle());
            if (node.getAvatarSeed()  != null) contact.setAvatarSeed(node.getAvatarSeed());
            contactRepo.save(contact);

            // Upsert conversation
            ImConversationEntity conv = conversationRepo.findById(targetNodeId).orElseGet(() ->
                ImConversationEntity.builder()
                    .id(targetNodeId)
                    .unreadCount(0)
                    .build()
            );
            conv.setDisplayName(node.getDisplayName());
            conversationRepo.save(conv);

            return RpcResponse.success(request.getId(), Map.of("conversationId", targetNodeId));
        });
    }
}
