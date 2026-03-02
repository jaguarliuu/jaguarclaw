package com.jaguarliu.ai.tools.builtin;

import com.jaguarliu.ai.memory.index.MemoryIndexer;
import com.jaguarliu.ai.memory.model.MemoryScope;
import com.jaguarliu.ai.memory.store.MemoryStore;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolExecutionContext;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 全局记忆写入工具
 *
 * LLM 通过此工具写入跨会话的全局记忆：
 * - file 省略：写入今日日期文件（默认日记行为）
 * - file 指定：写入任意命名文件（agent 自行组织），如 file="MEMORY.md" 更新索引
 *
 * 写入后自动触发索引更新。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryWriteTool implements Tool {

    private final MemoryStore memoryStore;
    private final MemoryIndexer memoryIndexer;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("memory_write")
                .description("写入记忆（支持共享/Agent 私有）。将重要信息保存到永久记忆中，供未来检索。"
                        + "file 省略时默认写入今日日期文件；"
                        + "file=\"MEMORY.md\" 用于更新记忆索引（仅写轻量索引，不写实质内容）；"
                        + "file=\"notes.md\" 等写入命名文件。"
                        + "scope 默认 agent（当前 Agent 私有），可选 global。")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "content", Map.of(
                                        "type", "string",
                                        "description", "要写入的内容（Markdown 格式）"
                                ),
                                "file", Map.of(
                                        "type", "string",
                                        "description", "目标文件（相对于 memory 目录的路径）。"
                                                + "省略时默认写今日日期文件（如 2026-03-02.md）；"
                                                + "file=\"MEMORY.md\" 用于更新索引；"
                                                + "file=\"notes.md\" 写入命名文件"
                                ),
                                "scope", Map.of(
                                        "type", "string",
                                        "enum", List.of("agent", "global"),
                                        "description", "写入作用域：agent=当前 Agent 私有（默认）；global=所有 Agent 共享"
                                )
                        ),
                        "required", List.of("content")
                ))
                .hitl(false)
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String content = (String) arguments.get("content");
            String file = (String) arguments.get("file");
            String scopeArg = (String) arguments.get("scope");

            // 参数校验
            if (content == null || content.isBlank()) {
                return ToolResult.error("Missing required parameter: content");
            }

            try {
                MemoryScope scope = resolveWriteScope(scopeArg);
                String agentId = resolveAgentId();
                String filePath;

                if (file == null || file.isBlank()) {
                    // 默认：写入今日日期文件
                    memoryStore.appendToDaily(content, agentId, scope);
                    filePath = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".md";
                } else {
                    // 写入指定文件
                    memoryStore.appendToMemoryFile(file, content, agentId, scope);
                    filePath = file;
                }

                // 异步触发索引更新
                try {
                    memoryIndexer.indexFile(filePath, scope, agentId);
                } catch (Exception e) {
                    log.warn("Failed to index after write: {}", e.getMessage());
                    // 索引失败不影响写入成功
                }

                log.info("memory_write to {} (scope={}, agentId={}): {} chars",
                        filePath, scope.name().toLowerCase(), agentId, content.length());
                return ToolResult.success("Successfully wrote " + content.length()
                        + " chars to " + filePath + " (scope="
                        + scope.name().toLowerCase() + ", agentId=" + agentId + ")");

            } catch (Exception e) {
                log.error("memory_write failed: {}", e.getMessage(), e);
                return ToolResult.error("Memory write failed: " + e.getMessage());
            }
        });
    }

    private String resolveAgentId() {
        ToolExecutionContext context = ToolExecutionContext.current();
        if (context == null || context.getAgentId() == null || context.getAgentId().isBlank()) {
            return "main";
        }
        return context.getAgentId();
    }

    private MemoryScope resolveWriteScope(String scopeArg) {
        if (scopeArg == null || scopeArg.isBlank()) {
            return MemoryScope.AGENT;
        }
        String normalized = scopeArg.trim().toLowerCase();
        return switch (normalized) {
            case "agent" -> MemoryScope.AGENT;
            case "global" -> MemoryScope.GLOBAL;
            default -> throw new IllegalArgumentException(
                    "Invalid scope: " + scopeArg + ". Must be 'agent' or 'global'");
        };
    }
}
