package com.jaguarliu.ai.tools.builtin.document;

import com.jaguarliu.ai.document.DocumentEntity;
import com.jaguarliu.ai.document.DocumentService;
import com.jaguarliu.ai.gateway.events.AgentEvent;
import com.jaguarliu.ai.gateway.events.EventBus;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolExecutionContext;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 文档内容插入工具
 * 向当前文档末尾追加内容，并实时通过 WebSocket 推送到编辑器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocInsertTool implements Tool {

    private final DocumentService documentService;
    private final ConnectionManager connectionManager;
    private final EventBus eventBus;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("doc_insert")
                .description("向当前文档末尾追加内容。内容会实时显示在用户的编辑器中。每次调用追加一段内容（不超过500字）。内容应为纯文本或 Markdown 格式。")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "doc_id", Map.of(
                                        "type", "string",
                                        "description", "文档 ID"
                                ),
                                "content", Map.of(
                                        "type", "string",
                                        "description", "要追加的文本内容，Markdown 格式"
                                )
                        ),
                        "required", List.of("doc_id", "content")
                ))
                .hitl(false)
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String docId = (String) arguments.get("doc_id");
            String content = (String) arguments.get("content");

            if (docId == null || docId.isBlank()) {
                return ToolResult.error("doc_id is required");
            }
            if (content == null || content.isBlank()) {
                return ToolResult.error("content is required");
            }

            ToolExecutionContext ctx = ToolExecutionContext.current();
            String connectionId = ctx != null ? ctx.getConnectionId() : null;
            String runId = ctx != null ? ctx.getRunId() : null;
            String ownerId = DocumentToolSupport.resolveOwnerId(ctx, connectionManager);

            // Publish real-time event to the editor
            if (connectionId != null && runId != null) {
                eventBus.publish(AgentEvent.docContentInsert(connectionId, runId, content));
            } else {
                log.warn("doc_insert: connectionId or runId is null, skipping WebSocket event push. " +
                        "connectionId={}, runId={}", connectionId, runId);
            }

            // Best-effort DB persistence — only update wordCount; pass null content so
            // we never corrupt the TipTap JSON that the frontend editor owns.
            try {
                DocumentEntity doc = documentService.get(docId, ownerId);
                int wordCount = doc.getWordCount() + estimateWordCount(content);
                documentService.update(docId, null, null, wordCount, ownerId);
            } catch (Exception e) {
                log.warn("doc_insert: failed to update wordCount in DB (non-fatal): {}", e.getMessage());
            }

            return ToolResult.success("内容已插入文档");
        });
    }

    /**
     * Simple word-count estimate (split on whitespace).
     */
    private int estimateWordCount(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }
}
