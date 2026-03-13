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
public class ImPairRespondHandler implements RpcHandler {
    private final ImPairingService pairingService;
    private final ObjectMapper objectMapper;

    @Override public String getMethod() { return "im.pair.respond"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> p = objectMapper.convertValue(request.getPayload(), Map.class);
            String fromNodeId      = (String) p.get("fromNodeId");
            String fromDisplayName = (String) p.get("fromDisplayName");
            String fromPubEd25519  = (String) p.get("fromPubEd25519");
            String fromPubX25519   = (String) p.get("fromPubX25519");
            boolean accept = Boolean.TRUE.equals(p.get("accept"));

            pairingService.respondToPairRequest(fromNodeId, fromDisplayName,
                fromPubEd25519, fromPubX25519, accept);
            return RpcResponse.success(request.getId(), Map.of("ok", true));
        });
    }
}
