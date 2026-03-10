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
public class HeartbeatMdGetHandler implements RpcHandler {

    private final HeartbeatConfigService heartbeatConfigService;

    @Override
    public String getMethod() {
        return "heartbeat.md.get";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            String agentId = extractAgentId(request.getPayload());
            String content = heartbeatConfigService.readHeartbeatMd(agentId);
            return RpcResponse.success(request.getId(), Map.of("content", content));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private String extractAgentId(Object payload) {
        if (payload instanceof Map<?, ?> map) {
            Object agentId = map.get("agentId");
            return agentId instanceof String s && !s.isBlank() ? s : "main";
        }
        return "main";
    }
}
