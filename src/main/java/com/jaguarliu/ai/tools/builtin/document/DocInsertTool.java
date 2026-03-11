package com.jaguarliu.ai.tools.builtin.document;

import com.jaguarliu.ai.document.DocumentEntity;
import com.jaguarliu.ai.document.DocumentService;
import com.jaguarliu.ai.gateway.events.AgentEvent;
import com.jaguarliu.ai.gateway.events.EventBus;
import com.jaguarliu.ai.gateway.security.ConnectionPrincipal;
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
            String ownerId = resolveOwnerId(ctx, connectionId);

            // Publish real-time event to the editor
            if (connectionId != null && runId != null) {
                eventBus.publish(AgentEvent.docContentInsert(connectionId, runId, content));
            } else {
                log.warn("doc_insert: connectionId or runId is null, skipping WebSocket event push. " +
                        "connectionId={}, runId={}", connectionId, runId);
            }

            // Best-effort DB persistence — the frontend will re-save the TipTap state on its own
            try {
                DocumentEntity doc = documentService.get(docId, ownerId);
                String existing = doc.getContent();
                // Append the new content as plain text after the existing content.
                // TipTap JSON cannot be naively appended; we store a simple concatenation
                // that the editor will overwrite on next save. This keeps the DB reasonably
                // up-to-date without attempting full TipTap JSON manipulation.
                String appended = (existing == null || existing.isBlank() || "{}".equals(existing.trim()))
                        ? content
                        : existing + "\n\n" + content;
                int wordCount = doc.getWordCount() + estimateWordCount(content);
                documentService.update(docId, null, appended, wordCount, ownerId);
            } catch (Exception e) {
                log.warn("doc_insert: failed to persist content to DB (non-fatal): {}", e.getMessage());
            }

            return ToolResult.success("内容已插入文档");
        });
    }

    private String resolveOwnerId(ToolExecutionContext ctx, String connectionId) {
        if (connectionId != null) {
            ConnectionPrincipal principal = connectionManager.getPrincipal(connectionId);
            if (principal != null && principal.getPrincipalId() != null) {
                return principal.getPrincipalId();
            }
        }
        return "local-default";
    }

    /**
     * Simple word-count estimate (split on whitespace).
     */
    private int estimateWordCount(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }
}
