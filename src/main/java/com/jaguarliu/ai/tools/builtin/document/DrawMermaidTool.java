package com.jaguarliu.ai.tools.builtin.document;

import com.jaguarliu.ai.gateway.events.AgentEvent;
import com.jaguarliu.ai.gateway.events.EventBus;
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
 * 在当前文档中插入 Mermaid 图表节点
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DrawMermaidTool implements Tool {

    private final EventBus eventBus;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("draw_mermaid")
                .description("在当前打开的文档中插入一个 Mermaid 图（流程图、时序图、类图、甘特图等）。" +
                        "图表会实时渲染并显示在用户的文档编辑器中。" +
                        "注意：code 参数必须是纯 Mermaid DSL，禁止使用 HTML 标签（如 <br>），节点文本内换行请用空格替代，" +
                        "多行文本可拆分成多个节点。例如：graph TD\\n  A[开始] --> B[处理] --> C[结束]")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "doc_id", Map.of(
                                        "type", "string",
                                        "description", "当前文档的 ID"
                                ),
                                "code", Map.of(
                                        "type", "string",
                                        "description", "Mermaid DSL 代码"
                                )
                        ),
                        "required", List.of("doc_id", "code")
                ))
                .hitl(false)
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String docId = (String) arguments.get("doc_id");
            String code = (String) arguments.get("code");

            if (docId == null || docId.isBlank()) {
                return ToolResult.error("doc_id is required");
            }
            if (code == null || code.isBlank()) {
                return ToolResult.error("code is required");
            }

            ToolExecutionContext ctx = ToolExecutionContext.current();
            String connectionId = ctx != null ? ctx.getConnectionId() : null;
            String runId = ctx != null ? ctx.getRunId() : null;

            if (connectionId == null || runId == null) {
                log.warn("draw_mermaid: connectionId or runId is null, cannot push node to editor");
                return ToolResult.error("无法推送图表：WebSocket 连接不可用");
            }

            // Build TipTap codeBlock node for mermaid
            Map<String, Object> textNode = Map.of("type", "text", "text", code);
            Map<String, Object> node = Map.of(
                    "type", "codeBlock",
                    "attrs", Map.of("language", "mermaid"),
                    "content", List.of(textNode)
            );

            eventBus.publish(AgentEvent.docNodeInsert(connectionId, runId, docId, node));
            log.info("draw_mermaid: pushed mermaid node to doc={} connection={}", docId, connectionId);

            return ToolResult.success("Mermaid 图已插入文档");
        });
    }
}
