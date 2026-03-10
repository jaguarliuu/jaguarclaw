package com.jaguarliu.ai.gateway.rpc.handler.document;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class DocumentConfigSetHandler implements RpcHandler {

    private final DocumentConfigService documentConfigService;
    private final ObjectMapper objectMapper;

    @Override
    public String getMethod() { return "document.config.set"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> params = objectMapper.convertValue(
                request.getPayload(), new TypeReference<Map<String, Object>>() {});
            String prompt = (String) params.get("systemPrompt");
            if (prompt == null || prompt.isBlank())
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "systemPrompt is required");

            documentConfigService.setSystemPrompt(prompt);
            return RpcResponse.success(request.getId(), Map.of("ok", true));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> {
              log.error("document.config.set failed: {}", e.getMessage());
              return Mono.just(RpcResponse.error(request.getId(), "SET_FAILED", e.getMessage()));
          });
    }
}
