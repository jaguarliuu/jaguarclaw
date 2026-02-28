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

@Slf4j
@Component
@RequiredArgsConstructor
public class HeartbeatConfigGetHandler implements RpcHandler {

    private final HeartbeatConfigService heartbeatConfigService;

    @Override
    public String getMethod() {
        return "heartbeat.config.get";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> RpcResponse.success(request.getId(), heartbeatConfigService.getConfig()))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
