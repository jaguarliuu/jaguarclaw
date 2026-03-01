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
 * - core: 写入核心记忆 MEMORY.md（长期、重要的信息）
 * - daily: 写入今日日记（日常对话要点）
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
                        + "target='core' 写入核心记忆（用户偏好、重要事实）；"
                        + "target='daily' 写入今日日记（对话要点、临时笔记）。"
                        + "scope 默认 agent（当前 Agent 私有），可选 global。")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "target", Map.of(
                                        "type", "string",
                                        "enum", List.of("core", "daily"),
                                        "description", "写入目标：core=核心记忆，daily=今日日记"
                                ),
                                "content", Map.of(
                                        "type", "string",
                                        "description", "要写入的内容（Markdown 格式）"
                                ),
                                "scope", Map.of(
                                        "type", "string",
                                        "enum", List.of("agent", "global"),
                                        "description", "写入作用域：agent=当前 Agent 私有（默认）；global=所有 Agent 共享"
                                )
                        ),
                        "required", List.of("target", "content")
                ))
                .hitl(false)
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String target = (String) arguments.get("target");
            String content = (String) arguments.get("content");
            String scopeArg = (String) arguments.get("scope");

            // 参数校验
            if (target == null || target.isBlank()) {
                return ToolResult.error("Missing required parameter: target");
            }
            if (content == null || content.isBlank()) {
                return ToolResult.error("Missing required parameter: content");
            }
            if (!target.equals("core") && !target.equals("daily")) {
                return ToolResult.error("Invalid target: " + target + ". Must be 'core' or 'daily'");
            }

            try {
                MemoryScope scope = resolveWriteScope(scopeArg);
                String agentId = resolveAgentId();
                String filePath;

                if ("core".equals(target)) {
                    memoryStore.appendToCore(content, agentId, scope);
                    filePath = "MEMORY.md";
                } else {
                    memoryStore.appendToDaily(content, agentId, scope);
                    filePath = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".md";
                }

                // 异步触发索引更新
                try {
                    memoryIndexer.indexFile(filePath, scope, agentId);
                } catch (Exception e) {
                    log.warn("Failed to index after write: {}", e.getMessage());
                    // 索引失败不影响写入成功
                }

                log.info("memory_write to {} (scope={}, agentId={}): {} chars",
                        target, scope.name().toLowerCase(), agentId, content.length());
                return ToolResult.success("Successfully wrote " + content.length()
                        + " chars to " + target + " memory (" + filePath + ", scope="
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
