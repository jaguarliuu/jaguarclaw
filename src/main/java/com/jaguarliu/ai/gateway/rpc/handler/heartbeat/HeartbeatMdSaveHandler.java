package com.jaguarliu.ai.gateway.rpc.handler.heartbeat;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.heartbeat.HeartbeatConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class HeartbeatMdSaveHandler implements RpcHandler {

    private final HeartbeatConfigService heartbeatConfigService;

    @Override
    public String getMethod() {
        return "heartbeat.md.save";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            if (!(request.getPayload() instanceof Map<?, ?> payload)) {
                return RpcResponse.error(request.getId(), "INVALID_PAYLOAD", "Expected object payload");
            }

            String agentId = payload.get("agentId") instanceof String s && !s.isBlank() ? s : "main";
            Object contentObj = payload.get("content");
            if (!(contentObj instanceof String content)) {
                return RpcResponse.error(request.getId(), "INVALID_PAYLOAD", "Missing required field: content");
            }

            heartbeatConfigService.writeHeartbeatMd(agentId, content);
            return RpcResponse.success(request.getId(), Map.of("success", true));
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
