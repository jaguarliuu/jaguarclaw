package com.jaguarliu.ai.im.handler;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.im.entity.ImIdentityEntity;
import com.jaguarliu.ai.im.service.ImIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ImSettingsGetHandler implements RpcHandler {

    private final ImIdentityService identityService;

    @Override
    public String getMethod() { return "im.settings.get"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            ImIdentityEntity id = identityService.getCached();
            return RpcResponse.success(request.getId(), Map.of(
                "nodeId",          id.getNodeId(),
                "displayName",     id.getDisplayName(),
                "redisUrl",        id.getRedisUrl() != null ? id.getRedisUrl() : "",
                "redisConfigured", id.getRedisUrl() != null && !id.getRedisUrl().isBlank()
            ));
        });
    }
}
