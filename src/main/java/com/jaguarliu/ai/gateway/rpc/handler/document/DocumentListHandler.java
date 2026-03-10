package com.jaguarliu.ai.gateway.rpc.handler.document;

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

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentListHandler implements RpcHandler {

    private final DocumentService documentService;
    private final ConnectionManager connectionManager;

    @Override
    public String getMethod() { return "document.list"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            String ownerId = resolveOwner(connectionId);
            var tree = documentService.getTree(ownerId);
            return RpcResponse.success(request.getId(), Map.of("documents", tree));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> {
              log.error("document.list failed: {}", e.getMessage());
              return Mono.just(RpcResponse.error(request.getId(), "LIST_FAILED", e.getMessage()));
          });
    }

    private String resolveOwner(String connectionId) {
        var principal = connectionManager.getPrincipal(connectionId);
        return principal != null ? principal.getPrincipalId() : "local-default";
    }
}
