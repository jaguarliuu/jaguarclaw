package com.jaguarliu.ai.tools.builtin.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.document.DocumentEntity;
import com.jaguarliu.ai.document.DocumentService;
import com.jaguarliu.ai.gateway.security.ConnectionPrincipal;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolExecutionContext;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文档读取工具
 * 读取当前正在编辑的文档内容（标题 + 文本）
 */
@Component
@RequiredArgsConstructor
public class DocReadTool implements Tool {

    private final DocumentService documentService;
    private final ConnectionManager connectionManager;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("doc_read")
                .description("读取当前正在编辑的文档内容。返回完整的文档标题和内容文本。在续写或修改文档前，先调用此工具了解文档现状。")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "doc_id", Map.of(
                                        "type", "string",
                                        "description", "文档 ID"
                                )
                        ),
                        "required", List.of("doc_id")
                ))
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String docId = (String) arguments.get("doc_id");
            if (docId == null || docId.isBlank()) {
                return ToolResult.error("doc_id is required");
            }

            ToolExecutionContext ctx = ToolExecutionContext.current();
            String ownerId = resolveOwnerId(ctx);

            try {
                DocumentEntity doc = documentService.get(docId, ownerId);
                String text = extractText(doc.getContent());
                return ToolResult.success("标题：" + doc.getTitle() + "\n\n" + text);
            } catch (Exception e) {
                return ToolResult.error("读取文档失败: " + e.getMessage());
            }
        });
    }

    private String resolveOwnerId(ToolExecutionContext ctx) {
        if (ctx != null && ctx.getConnectionId() != null) {
            ConnectionPrincipal principal = connectionManager.getPrincipal(ctx.getConnectionId());
            if (principal != null && principal.getPrincipalId() != null) {
                return principal.getPrincipalId();
            }
        }
        return "local-default";
    }

    /**
     * 从 TipTap JSON 中提取纯文本。
     * 若解析失败则返回原始字符串。
     */
    private String extractText(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        try {
            JsonNode root = MAPPER.readTree(content);
            List<JsonNode> textNodes = root.findValues("text");
            String joined = textNodes.stream()
                    .map(JsonNode::asText)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.joining("\n"));
            return joined.isBlank() ? content : joined;
        } catch (Exception e) {
            // Not valid JSON — return raw content
            return content;
        }
    }
}
