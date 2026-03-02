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
public class SoulGetHandler implements RpcHandler {

    private final SoulConfigService soulConfigService;

    @Override
    public String getMethod() {
        return "soul.get";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
                    String agentId = extractAgentId(request.getPayload());
                    return RpcResponse.success(request.getId(), Map.of(
                            "soul", soulConfigService.readSoulMd(agentId),
                            "rule", soulConfigService.readRuleMd(agentId),
                            "profile", soulConfigService.readProfileMd(agentId)
                    ));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String extractAgentId(Object payload) {
        if (payload instanceof Map<?, ?> map) {
            Object agentId = map.get("agentId");
            return agentId != null ? agentId.toString() : "main";
        }
        return "main";
    }
}
