package com.jaguarliu.ai.im.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.*;
import com.jaguarliu.ai.im.service.ImPairingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.Map;

@Component @RequiredArgsConstructor
public class ImPairRequestHandler implements RpcHandler {
    private final ImPairingService pairingService;
    private final ObjectMapper objectMapper;

    @Override public String getMethod() { return "im.pair.request"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> p = objectMapper.convertValue(request.getPayload(), Map.class);
            String targetNodeId = (String) p.get("targetNodeId");
            if (targetNodeId == null || targetNodeId.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "targetNodeId required");
            }
            pairingService.sendPairRequest(targetNodeId, null);
            return RpcResponse.success(request.getId(), Map.of("ok", true));
        });
    }
}
