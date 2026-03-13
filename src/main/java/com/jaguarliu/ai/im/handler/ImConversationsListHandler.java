package com.jaguarliu.ai.im.handler;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.*;
import com.jaguarliu.ai.im.dto.ImConversationDto;
import com.jaguarliu.ai.im.repository.ImConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.List;

@Component @RequiredArgsConstructor
public class ImConversationsListHandler implements RpcHandler {
    private final ImConversationRepository conversationRepo;

    @Override public String getMethod() { return "im.conversations.list"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            List<ImConversationDto> convs = conversationRepo.findAllOrderByLastMsgAtDesc().stream()
                .map(c -> ImConversationDto.builder()
                    .id(c.getId())
                    .displayName(c.getDisplayName())
                    .lastMsg(c.getLastMsg())
                    .lastMsgAt(c.getLastMsgAt() != null ? c.getLastMsgAt().toString() : null)
                    .unreadCount(c.getUnreadCount())
                    .build())
                .toList();
            return RpcResponse.success(request.getId(), convs);
        });
    }
}
