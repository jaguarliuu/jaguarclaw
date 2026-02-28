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
public class HeartbeatConfigSaveHandler implements RpcHandler {

    private final HeartbeatConfigService heartbeatConfigService;

    @Override
    public String getMethod() {
        return "heartbeat.config.save";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Object payload = request.getPayload();
            if (!(payload instanceof Map)) {
                return RpcResponse.error(request.getId(), "INVALID_PAYLOAD", "Expected object payload");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> config = (Map<String, Object>) payload;
            heartbeatConfigService.saveConfig(config);
            return RpcResponse.success(request.getId(), Map.of("success", true));
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
