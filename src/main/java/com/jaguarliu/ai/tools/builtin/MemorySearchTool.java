package com.jaguarliu.ai.tools.builtin;

import com.jaguarliu.ai.memory.model.MemoryScope;
import com.jaguarliu.ai.memory.search.MemorySearchService;
import com.jaguarliu.ai.memory.search.SearchResult;
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
 * 全局记忆检索工具
 *
 * LLM 通过此工具搜索跨会话的全局记忆，
 * 返回 snippet + 文件路径 + 行号 + 相关性评分。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemorySearchTool implements Tool {

    private final MemorySearchService searchService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("memory_search")
                .description("搜索记忆。从历史记忆中检索与查询相关的内容片段。"
                        + "用于回忆之前的对话、笔记和经验。"
                        + "返回相关片段的内容、来源文件和相关性评分。"
                        + "scope 默认 both（当前 Agent 私有 + 全局共享），可选 agent/global。")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of(
                                        "type", "string",
                                        "description", "搜索查询（自然语言，如：'用户的编程语言偏好'）"
                                ),
                                "scope", Map.of(
                                        "type", "string",
                                        "enum", List.of("both", "agent", "global"),
                                        "description", "检索作用域：both=agent+global（默认）；agent=仅当前 Agent 私有；global=仅共享记忆"
                                )
                        ),
                        "required", List.of("query")
                ))
                .hitl(false)
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String query = (String) arguments.get("query");
            String scopeArg = (String) arguments.get("scope");
            if (query == null || query.isBlank()) {
                return ToolResult.error("Missing required parameter: query");
            }

            try {
                MemoryScope scope = resolveScope(scopeArg);
                String agentId = resolveAgentId();
                List<SearchResult> results = searchService.search(query, scope, agentId);

                if (results.isEmpty()) {
                    return ToolResult.success("No relevant memories found for: " + query
                            + " (scope=" + scope.name().toLowerCase() + ", agentId=" + agentId + ")");
                }

                StringBuilder sb = new StringBuilder();
                sb.append("Found ").append(results.size())
                        .append(" relevant memories (scope=")
                        .append(scope.name().toLowerCase())
                        .append(", agentId=").append(agentId).append("):\n\n");

                for (int i = 0; i < results.size(); i++) {
                    SearchResult r = results.get(i);
                    sb.append("--- [").append(i + 1).append("] ");
                    sb.append(r.getFilePath());
                    sb.append(" (L").append(r.getLineStart());
                    sb.append("-L").append(r.getLineEnd());
                    sb.append(", score=").append(String.format("%.2f", r.getScore()));
                    sb.append(", via=").append(r.getSource());
                    sb.append(") ---\n");
                    sb.append(r.getSnippet()).append("\n\n");
                }

                log.info("memory_search '{}': {} results (scope={}, agentId={})",
                        query, results.size(), scope.name().toLowerCase(), agentId);
                return ToolResult.success(sb.toString());

            } catch (Exception e) {
                log.error("memory_search failed: {}", e.getMessage(), e);
                return ToolResult.error("Memory search failed: " + e.getMessage());
            }
        });
    }

    private MemoryScope resolveScope(String scopeArg) {
        if (scopeArg == null || scopeArg.isBlank()) {
            return MemoryScope.BOTH;
        }
        String normalized = scopeArg.trim().toLowerCase();
        return switch (normalized) {
            case "both" -> MemoryScope.BOTH;
            case "agent" -> MemoryScope.AGENT;
            case "global" -> MemoryScope.GLOBAL;
            default -> throw new IllegalArgumentException(
                    "Invalid scope: " + scopeArg + ". Must be 'both', 'agent' or 'global'");
        };
    }

    private String resolveAgentId() {
        ToolExecutionContext context = ToolExecutionContext.current();
        if (context == null || context.getAgentId() == null || context.getAgentId().isBlank()) {
            return "main";
        }
        return context.getAgentId();
    }
}
