package com.jaguarliu.ai.im.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.*;
import com.jaguarliu.ai.im.service.ImMessagingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.Base64;
import java.util.Map;

@Component @RequiredArgsConstructor
public class ImFileSendHandler implements RpcHandler {
    private final ImMessagingService messagingService;
    private final ObjectMapper objectMapper;

    @Override public String getMethod() { return "im.file.send"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> p = objectMapper.convertValue(request.getPayload(), Map.class);
            String toNodeId  = (String) p.get("toNodeId");
            String filename  = (String) p.get("filename");
            String mimeType  = (String) p.get("mimeType");
            String dataB64   = (String) p.get("data");
            if (toNodeId == null || filename == null || dataB64 == null) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS",
                    "toNodeId, filename, and data are required");
            }
            byte[] fileData = Base64.getDecoder().decode(dataB64);
            String messageId = messagingService.sendFile(toNodeId, filename, mimeType, fileData);
            return RpcResponse.success(request.getId(), Map.of("messageId", messageId));
        }).onErrorResume(e ->
            Mono.just(RpcResponse.error(request.getId(), "SEND_FAILED", e.getMessage()))
        );
    }
}
