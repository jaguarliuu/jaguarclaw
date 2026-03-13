package com.jaguarliu.ai.im.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.im.config.ImLettuceConfig;
import com.jaguarliu.ai.im.entity.ImIdentityEntity;
import com.jaguarliu.ai.im.service.ImIdentityService;
import com.jaguarliu.ai.im.service.ImRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ImSettingsSaveHandler implements RpcHandler {

    private final ImIdentityService identityService;
    private final ImLettuceConfig lettuceConfig;
    private final ImRegistryService registryService;
    private final ObjectMapper objectMapper;

    @Override
    public String getMethod() { return "im.settings.save"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> params = objectMapper.convertValue(request.getPayload(), Map.class);
            String displayName = (String) params.get("displayName");
            String redisUrl    = (String) params.get("redisUrl");
            String redisPwd    = (String) params.get("redisPassword");

            ImIdentityEntity id = identityService.getCached();
            if (displayName != null && !displayName.isBlank()) id.setDisplayName(displayName);
            if (redisUrl != null) id.setRedisUrl(redisUrl.trim());
            if (redisPwd != null) id.setRedisPassword(redisPwd);
            identityService.save(id);

            lettuceConfig.configure(id.getRedisUrl(), id.getRedisPassword());
            if (lettuceConfig.isConfigured()) {
                registryService.registerSelf();
            }

            return RpcResponse.success(request.getId(), Map.of("ok", true));
        });
    }
}
