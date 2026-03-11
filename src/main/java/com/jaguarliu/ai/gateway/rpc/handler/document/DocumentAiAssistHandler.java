package com.jaguarliu.ai.gateway.rpc.handler.document;

import com.jaguarliu.ai.document.DocumentConfigService;
import com.jaguarliu.ai.document.DocumentService;
import com.jaguarliu.ai.gateway.events.AgentEvent;
import com.jaguarliu.ai.gateway.events.EventBus;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.runtime.AgentRuntime;
import com.jaguarliu.ai.runtime.CancellationManager;
import com.jaguarliu.ai.runtime.LoopConfig;
import com.jaguarliu.ai.runtime.RunContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentAiAssistHandler implements RpcHandler {

    private static final Set<String> DOC_WRITER_TOOLS = Set.of("doc_read", "doc_insert", "web_get", "use_skill");

    private final DocumentService documentService;
    private final DocumentConfigService documentConfigService;
    private final AgentRuntime agentRuntime;
    private final ConnectionManager connectionManager;
    private final LoopConfig loopConfig;
    private final CancellationManager cancellationManager;
    private final EventBus eventBus;

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

                String docId     = (String) p.get("docId");
                String action    = (String) p.get("action");
                String selection = (String) p.get("selection");
                String userPrompt = (String) p.get("userPrompt");

                if (docId == null || docId.isBlank())
                    return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "docId is required"));
                if (action == null || action.isBlank())
                    return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "action is required"));

                var validActions = Set.of("continue", "optimize", "rewrite", "summarize", "translate");
                if (!validActions.contains(action))
                    return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "Unknown action: " + action));

                String ownerId = resolveOwner(connectionId);
                var doc = documentService.get(docId, ownerId);

                String systemPrompt = documentConfigService.getSystemPrompt();
                String userMessage = buildUserMessage(action, doc.getContent(), selection, userPrompt);

                String runId = "doc-assist-" + UUID.randomUUID();
                String sessionId = "doc-session-" + docId;

                List<LlmRequest.Message> messages = new ArrayList<>();
                messages.add(LlmRequest.Message.system(systemPrompt));
                messages.add(LlmRequest.Message.user(userMessage));

                RunContext context = RunContext.create(runId, connectionId, sessionId,
                        "document-writer", loopConfig, cancellationManager);
                context.setAgentAllowedTools(DOC_WRITER_TOOLS);
                context.setOriginalInput(userMessage);
                cancellationManager.register(runId);

                // Run asynchronously, return streamRunId immediately
                Mono.fromCallable(() -> {
                    try {
                        eventBus.publish(AgentEvent.lifecycleStart(connectionId, runId));
                        agentRuntime.executeLoopWithContext(context, messages);
                        eventBus.publish(AgentEvent.lifecycleEnd(connectionId, runId));
                    } catch (java.util.concurrent.CancellationException e) {
                        log.info("Document AI assist cancelled: runId={}", runId);
                        eventBus.publish(AgentEvent.lifecycleError(connectionId, runId, "Cancelled by user"));
                    } catch (java.util.concurrent.TimeoutException e) {
                        log.warn("Document AI assist timed out: runId={}", runId);
                        eventBus.publish(AgentEvent.lifecycleError(connectionId, runId, e.getMessage()));
                    } catch (Exception e) {
                        log.error("Document AI assist failed: runId={}", runId, e);
                        eventBus.publish(AgentEvent.lifecycleError(connectionId, runId, e.getMessage()));
                    }
                    return null;
                }).subscribeOn(Schedulers.boundedElastic()).subscribe();

                return Mono.just(RpcResponse.success(request.getId(), Map.of("streamRunId", runId)));

            } catch (Exception e) {
                log.error("document.ai.assist setup failed: {}", e.getMessage(), e);
                return Mono.just(RpcResponse.error(request.getId(), "AI_ASSIST_ERROR", e.getMessage()));
            }
        });
    }

    private String buildUserMessage(String action, String docContent, String selection, String userPrompt) {
        String target = (selection != null && !selection.isBlank()) ? selection : docContent;
        String base = switch (action) {
            case "continue"  -> "请续写以下内容";
            case "optimize"  -> "请润色以下文本";
            case "rewrite"   -> "请改写以下文本";
            case "summarize" -> "请总结以下内容的核心要点";
            case "translate" -> "请翻译以下内容";
            default          -> action;
        };
        StringBuilder sb = new StringBuilder(base);
        if (userPrompt != null && !userPrompt.isBlank()) {
            sb.append("。具体要求：").append(userPrompt);
        }
        sb.append("：\n\n").append(target);
        return sb.toString();
    }

    private String resolveOwner(String cid) {
        var principal = connectionManager.getPrincipal(cid);
        return principal != null ? principal.getPrincipalId() : "local-default";
    }
}
