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
public class DocumentCreateHandler implements RpcHandler {

    private final DocumentService documentService;
    private final ConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    @Override public String getMethod() { return "document.create"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> p = objectMapper.convertValue(request.getPayload(),
                    new TypeReference<Map<String, Object>>() {});
            String title    = (String) p.get("title");
            String parentId = (String) p.get("parentId");
            String ownerId  = resolveOwner(connectionId);
            var doc = documentService.create(title, parentId, ownerId);
            return RpcResponse.success(request.getId(), Map.of(
                "id", doc.getId(),
                "title", doc.getTitle(),
                "parentId", doc.getParentId() != null ? doc.getParentId() : "",
                "content", doc.getContent(),
                "wordCount", doc.getWordCount(),
                "createdAt", doc.getCreatedAt().toString(),
                "updatedAt", doc.getUpdatedAt().toString()));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> {
              log.error("document.create failed: {}", e.getMessage());
              return Mono.just(RpcResponse.error(request.getId(), "CREATE_FAILED", e.getMessage()));
          });
    }

    private String resolveOwner(String cid) {
        var p = connectionManager.getPrincipal(cid);
        return p != null ? p.getPrincipalId() : "local-default";
    }
}
