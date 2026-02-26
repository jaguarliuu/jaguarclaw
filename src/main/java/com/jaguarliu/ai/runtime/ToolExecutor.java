package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.gateway.events.EventBus;
import com.jaguarliu.ai.llm.model.ToolCall;
import com.jaguarliu.ai.session.SessionFileService;
import com.jaguarliu.ai.tools.ToolDispatcher;
import com.jaguarliu.ai.tools.ToolRegistry;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工具执行器
 * 负责 HITL 确认、工具分发与结果处理
 */
@Component
@RequiredArgsConstructor
public class ToolExecutor {

    private final ToolRegistry toolRegistry;
    private final ToolDispatcher toolDispatcher;
    private final HitlManager hitlManager;
    private final EventBus eventBus;
    private final SessionFileService sessionFileService;

    /**
     * 执行工具调用列表。
     */
    public List<ToolExecutionResult> executeToolCalls(
            RunContext context,
            List<ToolCall> toolCalls
    ) {
        throw new UnsupportedOperationException();
    }

    /**
     * 执行单个工具（含 HITL）。
     */
    private ToolExecutionResult executeSingleTool(
            RunContext context,
            ToolCall toolCall
    ) {
        throw new UnsupportedOperationException();
    }

    public record ToolExecutionResult(String callId, ToolResult result) {}
}

