package com.jaguarliu.ai.gateway.rpc.handler.document;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.document.DocumentService;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.util.Map;

@Slf4j @Component @RequiredArgsConstructor
public class DocumentDeleteHandler implements RpcHandler {

    private final DocumentService documentService;
    private final ConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    @Override public String getMethod() { return "document.delete"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> p = objectMapper.convertValue(request.getPayload(),
                    new TypeReference<Map<String, Object>>() {});
            String id = (String) p.get("id");
            if (id == null || id.isBlank())
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "id is required");

            String ownerId = resolveOwner(connectionId);
            documentService.delete(id, ownerId);
            return RpcResponse.success(request.getId(), Map.of("success", true));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> {
              log.error("document.delete failed: {}", e.getMessage());
              return Mono.just(RpcResponse.error(request.getId(), "DELETE_FAILED", e.getMessage()));
          });
    }

    private String resolveOwner(String cid) {
        var p = connectionManager.getPrincipal(cid);
        return p != null ? p.getPrincipalId() : "local-default";
    }
}
