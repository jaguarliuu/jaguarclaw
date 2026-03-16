package com.jaguarliu.ai.im.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.im.config.ImLettuceConfig;
import com.jaguarliu.ai.im.entity.ImIdentityEntity;
import com.jaguarliu.ai.im.service.ImIdentityService;
import com.jaguarliu.ai.im.service.ImMessagingService;
import com.jaguarliu.ai.im.service.ImPairingService;
import com.jaguarliu.ai.im.service.ImRegistryService;
import jakarta.annotation.PostConstruct;
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
    private final ImPairingService pairingService;
    private final ImMessagingService messagingService;
    private final ObjectMapper objectMapper;

    @PostConstruct
    void onStartup() {
        ImIdentityEntity id = identityService.getCached();
        if (id.getRedisUrl() != null && !id.getRedisUrl().isBlank()) {
            lettuceConfig.configure(id.getRedisUrl(), id.getRedisPassword());
            registryService.registerSelf();
            pairingService.startSubscriptions();
            messagingService.startSubscriptions();
        }
    }

    @Override
    public String getMethod() { return "im.settings.save"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> params = objectMapper.convertValue(request.getPayload(), Map.class);
            String displayName = (String) params.get("displayName");
            String redisUrl    = (String) params.get("redisUrl");
            String redisPwd    = (String) params.get("redisPassword");
            String avatarStyle = (String) params.get("avatarStyle");
            String avatarSeed  = (String) params.get("avatarSeed");

            ImIdentityEntity id = identityService.getCached();
            if (displayName != null && !displayName.isBlank()) id.setDisplayName(displayName);
            if (redisUrl != null) id.setRedisUrl(redisUrl.trim());
            if (redisPwd != null) id.setRedisPassword(redisPwd);
            if (avatarStyle != null && !avatarStyle.isBlank()) id.setAvatarStyle(avatarStyle);
            if (avatarSeed != null) id.setAvatarSeed(avatarSeed);
            identityService.save(id);

            lettuceConfig.configure(id.getRedisUrl(), id.getRedisPassword());
            if (lettuceConfig.isConfigured()) {
                registryService.registerSelf();
                pairingService.startSubscriptions();
                messagingService.startSubscriptions();
                pairingService.broadcastProfileUpdate();
            }

            return RpcResponse.success(request.getId(), Map.of("ok", true));
        });
    }
}
