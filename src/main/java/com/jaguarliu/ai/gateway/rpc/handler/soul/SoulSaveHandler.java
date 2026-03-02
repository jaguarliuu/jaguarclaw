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

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SoulSaveHandler implements RpcHandler {

    private final SoulConfigService soulConfigService;

    @Override
    public String getMethod() {
        return "soul.save";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Object payload = request.getPayload();
            if (!(payload instanceof Map)) {
                return RpcResponse.error(request.getId(), "INVALID_PAYLOAD", "Expected object payload");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> payloadMap = (Map<String, Object>) payload;

            String agentId = "main";
            Object agentIdRaw = payloadMap.get("agentId");
            if (agentIdRaw != null && !agentIdRaw.toString().isBlank()) {
                agentId = agentIdRaw.toString();
            }

            String file = payloadMap.getOrDefault("file", "soul").toString();
            String content = payloadMap.getOrDefault("content", "").toString();

            switch (file) {
                case "soul"    -> soulConfigService.writeSoulMd(agentId, content);
                case "rule"    -> soulConfigService.writeRuleMd(agentId, content);
                case "profile" -> soulConfigService.writeProfileMd(agentId, content);
                default -> {
                    return RpcResponse.error(request.getId(), "INVALID_FILE",
                            "file must be one of: soul, rule, profile");
                }
            }

            return RpcResponse.success(request.getId(), Map.of("success", true));
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
