package com.jaguarliu.ai.gateway.rpc.handler.soul;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.soul.SoulConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class SoulGetHandler implements RpcHandler {

    private final SoulConfigService soulConfigService;

    @Override
    public String getMethod() {
        return soul.get;
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> RpcResponse.success(request.getId(), soulConfigService.getConfig()))
                .subscribeOn(Schedulers.boundedElastic());
    }
}

