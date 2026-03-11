package com.jaguarliu.ai.gateway.rpc.handler.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.document.DocumentConfigService;
import com.jaguarliu.ai.document.DocumentService;
import com.jaguarliu.ai.gateway.events.AgentEvent;
import com.jaguarliu.ai.gateway.events.EventBus;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import com.jaguarliu.ai.llm.LlmClient;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.runtime.CancellationManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentAiAssistHandler implements RpcHandler {

    private static final Set<String> VALID_ACTIONS = Set.of("continue", "optimize", "rewrite", "summarize", "translate");

    private final DocumentService documentService;
    private final DocumentConfigService documentConfigService;
    private final LlmClient llmClient;
    private final ConnectionManager connectionManager;
    private final CancellationManager cancellationManager;
    private final EventBus eventBus;
    private final ObjectMapper objectMapper;

    @Override
    public String getMethod() { return "document.ai.assist"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.defer(() -> {
            try {
                Object payload = request.getPayload();
                if (!(payload instanceof Map)) {
                    return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "payload must be a map"));
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> p = (Map<String, Object>) payload;

                String docId      = (String) p.get("docId");
                String action     = (String) p.get("action");
                String selection  = (String) p.get("selection");
                String userPrompt = (String) p.get("userPrompt");

                if (docId == null || docId.isBlank())
                    return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "docId is required"));
                if (action == null || action.isBlank() || !VALID_ACTIONS.contains(action))
                    return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "Unknown action: " + action));

                String ownerId = resolveOwner(connectionId);
                var doc = documentService.get(docId, ownerId);

                String runId = "doc-assist-" + UUID.randomUUID();
                cancellationManager.register(runId);

                String systemPrompt = documentConfigService.getSystemPrompt();
                String userMessage  = buildUserMessage(action, doc.getTitle(), doc.getContent(), selection, userPrompt);

                LlmRequest llmRequest = LlmRequest.builder()
                        .messages(List.of(
                                LlmRequest.Message.system(systemPrompt),
                                LlmRequest.Message.user(userMessage)
                        ))
                        .stream(true)
                        .build();

                // Stream asynchronously — return runId immediately so frontend can subscribe
                llmClient.stream(llmRequest)
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe(
                                chunk -> {
                                    String delta = chunk.getDelta();
                                    if (delta != null && !delta.isEmpty()) {
                                        eventBus.publish(AgentEvent.docContentInsert(connectionId, runId, delta));
                                    }
                                },
                                err -> {
                                    log.error("Document AI assist stream error: runId={}", runId, err);
                                    eventBus.publish(AgentEvent.lifecycleError(connectionId, runId, err.getMessage()));
                                },
                                () -> {
                                    log.debug("Document AI assist stream complete: runId={}", runId);
                                    eventBus.publish(AgentEvent.lifecycleEnd(connectionId, runId));
                                }
                        );

                return Mono.just(RpcResponse.success(request.getId(), Map.of("streamRunId", runId)));

            } catch (Exception e) {
                log.error("document.ai.assist setup failed: {}", e.getMessage(), e);
                return Mono.just(RpcResponse.error(request.getId(), "AI_ASSIST_ERROR", e.getMessage()));
            }
        });
    }

    private String buildUserMessage(String action, String title, String rawContent, String selection, String userPrompt) {
        // Extract plain text from TipTap JSON if needed
        String docText = extractText(rawContent);
        String target  = (selection != null && !selection.isBlank()) ? selection : docText;

        String base = switch (action) {
            case "continue"  -> "请续写以下内容，直接输出续写的正文，不要解释，不要前言：";
            case "optimize"  -> "请润色以下文本，直接输出润色后的正文，不要解释：";
            case "rewrite"   -> "请改写以下文本，直接输出改写后的正文，不要解释：";
            case "summarize" -> "请总结以下内容的核心要点，直接输出总结，不要前言：";
            case "translate" -> "请翻译以下内容，直接输出译文，不要前言：";
            default          -> action + "：";
        };

        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank() && !"Untitled".equals(title)) {
            sb.append("文档标题：").append(title).append("\n\n");
        }
        sb.append(base);
        if (userPrompt != null && !userPrompt.isBlank()) {
            sb.append("\n（额外要求：").append(userPrompt).append("）");
        }
        sb.append("\n\n").append(target);
        return sb.toString();
    }

    private String extractText(String content) {
        if (content == null || content.isBlank()) return "";
        try {
            JsonNode root = objectMapper.readTree(content);
            var textNodes = root.findValues("text");
            String joined = textNodes.stream()
                    .map(JsonNode::asText)
                    .filter(s -> !s.isBlank())
                    .collect(java.util.stream.Collectors.joining("\n"));
            return joined.isBlank() ? content : joined;
        } catch (Exception e) {
            return content;
        }
    }

    private String resolveOwner(String cid) {
        var principal = connectionManager.getPrincipal(cid);
        return principal != null ? principal.getPrincipalId() : "local-default";
    }
}
