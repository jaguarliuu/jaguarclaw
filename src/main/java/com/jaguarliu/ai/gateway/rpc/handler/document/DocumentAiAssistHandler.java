package com.jaguarliu.ai.gateway.rpc.handler.document;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.document.DocumentService;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.handler.agent.AgentRunHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import com.jaguarliu.ai.session.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentAiAssistHandler implements RpcHandler {

    private final DocumentService documentService;
    private final SessionService sessionService;
    private final AgentRunHandler agentRunHandler;
    private final ConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    @Override
    public String getMethod() { return "document.ai.assist"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.defer(() -> {
            try {
                Map<String, Object> p = objectMapper.convertValue(request.getPayload(),
                        new TypeReference<Map<String, Object>>() {});
                String docId     = (String) p.get("docId");
                String action    = (String) p.get("action");
                String selection = (String) p.get("selection");

                if (docId == null || docId.isBlank())
                    return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "docId is required"));
                if (action == null || action.isBlank())
                    return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "action is required"));

                String ownerId = resolveOwner(connectionId);
                var doc     = documentService.get(docId, ownerId);
                var session = sessionService.findOrCreateDocumentSession(docId, ownerId);
                String prompt = buildPrompt(action, doc.getContent(), selection);

                // Build synthetic agent.run request using the actual key names AgentRunHandler reads:
                // extractSessionId reads "sessionId", extractPrompt reads "prompt"
                var syntheticPayload = Map.of(
                    "sessionId", session.getId(),
                    "prompt", prompt
                );
                var syntheticReq = RpcRequest.builder()
                        .id(UUID.randomUUID().toString())
                        .method("agent.run")
                        .payload(syntheticPayload)
                        .build();

                return agentRunHandler.handle(connectionId, syntheticReq)
                        .map(runResponse -> {
                            if (runResponse.getError() != null) {
                                log.error("Agent run failed for document AI assist: {}", runResponse);
                                return RpcResponse.error(request.getId(), "AI_ASSIST_FAILED", "Agent run failed");
                            }
                            // Pass through the agent.run response (contains runId)
                            return RpcResponse.success(request.getId(), runResponse.getPayload());
                        });
            } catch (Exception e) {
                log.error("document.ai.assist setup failed: {}", e.getMessage(), e);
                return Mono.just(RpcResponse.error(request.getId(), "AI_ASSIST_ERROR", e.getMessage()));
            }
        });
    }

    private String buildPrompt(String action, String docContent, String selection) {
        String target = (selection != null && !selection.isBlank()) ? selection : docContent;
        return switch (action) {
            case "continue"  -> "请续写以下内容，保持原有文风，只输出续写部分，不重复已有内容：\n\n" + target;
            case "optimize"  -> "请润色以下文本，改善表达，保留原意，只输出润色后的完整文本：\n\n" + target;
            case "rewrite"   -> "请改写以下文本，使其更清晰简洁，只输出改写结果：\n\n" + target;
            case "summarize" -> "请提炼以下文档的核心要点，以3-5条Markdown列表形式输出，只输出摘要：\n\n" + target;
            case "translate" -> "请将以下文本翻译（中英互译），只输出译文：\n\n" + target;
            default          -> "请处理以下内容：\n\n" + target;
        };
    }

    private String resolveOwner(String cid) {
        var principal = connectionManager.getPrincipal(cid);
        return principal != null ? principal.getPrincipalId() : "local-default";
    }
}
