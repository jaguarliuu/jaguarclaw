package com.jaguarliu.ai.gateway.rpc.handler.document;

import com.jaguarliu.ai.document.DocumentConfigService;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentConfigGetHandler implements RpcHandler {

    private final DocumentConfigService documentConfigService;

    @Override
    public String getMethod() { return "document.config.get"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() ->
            RpcResponse.success(request.getId(), Map.of("systemPrompt", documentConfigService.getSystemPrompt()))
        ).subscribeOn(Schedulers.boundedElastic())
         .onErrorResume(e -> {
             log.error("document.config.get failed: {}", e.getMessage());
             return Mono.just(RpcResponse.error(request.getId(), "GET_FAILED", e.getMessage()));
         });
    }
}
