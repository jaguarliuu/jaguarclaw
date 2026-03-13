package com.jaguarliu.ai.im.handler;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.im.service.ImRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ImNodesListHandler implements RpcHandler {

    private final ImRegistryService registryService;

    @Override
    public String getMethod() { return "im.nodes.list"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() ->
            RpcResponse.success(request.getId(), registryService.listOnlineNodes())
        );
    }
}
