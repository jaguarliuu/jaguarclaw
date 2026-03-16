package com.jaguarliu.ai.im.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.*;
import com.jaguarliu.ai.im.service.ImMessagingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.Map;

@Component @RequiredArgsConstructor
public class ImMessageSendHandler implements RpcHandler {
    private final ImMessagingService messagingService;
    private final ObjectMapper objectMapper;

    @Override public String getMethod() { return "im.message.send"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> p = objectMapper.convertValue(request.getPayload(), Map.class);
            String toNodeId = (String) p.get("toNodeId");
            String text     = (String) p.get("text");
            if (toNodeId == null || text == null || text.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "toNodeId and text required");
            }
            String messageId = messagingService.sendText(toNodeId, text);
            return RpcResponse.success(request.getId(), Map.of("messageId", messageId));
        }).onErrorResume(e ->
            Mono.just(RpcResponse.error(request.getId(), "SEND_FAILED", e.getMessage()))
        );
    }
}
