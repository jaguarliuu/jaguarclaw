package com.jaguarliu.ai.gateway.rpc.handler.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.document.DocumentEntity;
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
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentGetHandler implements RpcHandler {

    private final DocumentService documentService;
    private final ConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    @Override
    public String getMethod() { return "document.get"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> params = objectMapper.convertValue(request.getPayload(), Map.class);
            String id = (String) params.get("id");
            if (id == null || id.isBlank())
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "id is required");

            String ownerId = resolveOwner(connectionId);
            DocumentEntity doc = documentService.get(id, ownerId);
            return RpcResponse.success(request.getId(), toDto(doc));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> {
              log.error("document.get failed: {}", e.getMessage());
              return Mono.just(RpcResponse.error(request.getId(), "GET_FAILED", e.getMessage()));
          });
    }

    private Map<String, Object> toDto(DocumentEntity doc) {
        var dto = new HashMap<String, Object>();
        dto.put("id", doc.getId());
        dto.put("parentId", doc.getParentId() != null ? doc.getParentId() : "");
        dto.put("title", doc.getTitle());
        dto.put("content", doc.getContent());
        dto.put("wordCount", doc.getWordCount());
        dto.put("sortOrder", doc.getSortOrder());
        dto.put("createdAt", doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : "");
        dto.put("updatedAt", doc.getUpdatedAt() != null ? doc.getUpdatedAt().toString() : "");
        return dto;
    }

    private String resolveOwner(String connectionId) {
        var principal = connectionManager.getPrincipal(connectionId);
        return principal != null ? principal.getPrincipalId() : "local-default";
    }
}
