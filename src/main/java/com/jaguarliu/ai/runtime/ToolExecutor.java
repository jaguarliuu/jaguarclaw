package com.jaguarliu.ai.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.events.AgentEvent;
import com.jaguarliu.ai.gateway.events.EventBus;
import com.jaguarliu.ai.llm.model.ToolCall;
import com.jaguarliu.ai.session.SessionFileService;
import com.jaguarliu.ai.tools.ToolDispatcher;
import com.jaguarliu.ai.tools.ToolExecutionContext;
import com.jaguarliu.ai.tools.ToolRegistry;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工具执行器
 * 负责 HITL 确认、工具分发与结果处理
 *
 * 从 AgentRuntime 迁移的逻辑：
 * - executeToolCall() → executeSingleTool()
 * - parseArguments()
 * - setupToolExecutionContext()
 * - recordWriteFile()
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolExecutor {

    private final ToolRegistry toolRegistry;
    private final ToolDispatcher toolDispatcher;
    private final HitlManager hitlManager;
    private final EventBus eventBus;
    private final SessionFileService sessionFileService;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 执行工具调用列表
     *
     * @param context   运行上下文
     * @param toolCalls 工具调用列表
     * @return 执行结果列表
     */
    public List<ToolExecutionResult> executeToolCalls(
            RunContext context,
            List<ToolCall> toolCalls
    ) {
        List<ToolExecutionResult> results = new ArrayList<>();
        for (ToolCall call : toolCalls) {
            results.add(executeSingleTool(context, call));
        }
        return results;
    }

    /**
     * 执行单个工具（含 HITL）
     */
    private ToolExecutionResult executeSingleTool(
            RunContext context,
            ToolCall toolCall
    ) {
        String toolName = toolCall.getName();
        String callId = toolCall.getId();
        String argumentsJson = toolCall.getArguments();

        log.info("Executing tool: name={}, callId={}, runId={}, step={}",
                toolName, callId, context.getRunId(), context.getCurrentStep());

        Map<String, Object> arguments = parseArguments(argumentsJson);

        // 定时任务运行时跳过所有 HITL 确认
        boolean isScheduledRun = "scheduled".equals(context.getRunKind());
        boolean requiresHitl = !isScheduledRun && toolDispatcher.requiresHitl(toolName, null, arguments);

        if (requiresHitl) {
            log.info("Tool requires HITL confirmation: name={}, callId={}", toolName, callId);

            // 发布 tool.confirm_request 事件
            eventBus.publish(AgentEvent.toolConfirmRequest(
                    context.getConnectionId(),
                    context.getRunId(),
                    callId,
                    toolName,
                    arguments));

            // 等待用户决策
            HitlDecision decision = hitlManager.requestConfirmation(callId, toolName).block();

            if (decision == null || !decision.isApproved()) {
                log.info("Tool rejected by HITL: name={}, callId={}", toolName, callId);

                ToolResult rejectResult = ToolResult.error("Tool execution rejected by user");

                // 发布 tool.result 事件（被拒绝）
                eventBus.publish(AgentEvent.toolResult(
                        context.getConnectionId(),
                        context.getRunId(),
                        callId,
                        false,
                        rejectResult.getContent()));

                return new ToolExecutionResult(callId, rejectResult);
            }

            // 如果用户修改了参数，使用修改后的参数
            if (decision.getModifiedArguments() != null && !decision.getModifiedArguments().isEmpty()) {
                arguments = decision.getModifiedArguments();
                log.info("Using modified arguments for tool: name={}, callId={}", toolName, callId);
            }
        }

        // 发布 tool.call 事件
        eventBus.publish(AgentEvent.toolCall(
                context.getConnectionId(),
                context.getRunId(),
                callId,
                toolName,
                arguments));

        // 设置工具执行上下文
        setupToolExecutionContext(context);

        // 执行工具
        ToolResult result;
        try {
            result = toolDispatcher.dispatch(toolName, arguments).block();
        } finally {
            ToolExecutionContext.clear();
        }

        if (result == null) {
            result = ToolResult.error("Tool execution returned null");
        }

        // 记录 write_file 成功创建的文件
        if ("write_file".equals(toolName) && result.isSuccess()) {
            recordWriteFile(context, arguments);
        }

        // 发布 tool.result 事件
        eventBus.publish(AgentEvent.toolResult(
                context.getConnectionId(),
                context.getRunId(),
                callId,
                result.isSuccess(),
                result.getContent()));

        log.info("Tool executed: name={}, success={}, runId={}",
                toolName, result.isSuccess(), context.getRunId());

        return new ToolExecutionResult(callId, result);
    }

    /**
     * 解析工具参数 JSON
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(argumentsJson, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse tool arguments: {}", argumentsJson, e);
            return Map.of();
        }
    }

    /**
     * 设置工具执行上下文
     */
    private void setupToolExecutionContext(RunContext context) {
        ToolExecutionContext.Builder builder = ToolExecutionContext.builder()
                .runId(context.getRunId())
                .sessionId(context.getSessionId())
                .connectionId(context.getConnectionId())
                .agentId(context.getAgentId())
                .runKind(context.getRunKind())
                .parentRunId(context.getParentRunId())
                .depth(context.getDepth());

        // 如果有激活的 skill，添加其资源目录到允许路径
        if (context.getSkillBasePath() != null) {
            builder.addAllowedPath(context.getSkillBasePath());
            log.debug("Added skill resource path to tool context: {}", context.getSkillBasePath());
        }

        ToolExecutionContext.set(builder.build());
    }

    /**
     * 记录 write_file 成功创建的文件
     */
    private void recordWriteFile(RunContext context, Map<String, Object> arguments) {
        String path = (String) arguments.get("path");
        String content = (String) arguments.get("content");
        if (path == null) return;

        String fileName = Path.of(path).getFileName().toString();
        long size = content != null ? content.getBytes(StandardCharsets.UTF_8).length : 0;

        try {
            var entity = sessionFileService.record(
                    context.getSessionId(),
                    context.getRunId(),
                    path,
                    fileName,
                    size
            );

            // 发布 file.created 事件
            eventBus.publish(AgentEvent.fileCreated(
                    context.getConnectionId(),
                    context.getRunId(),
                    entity.getId(),
                    path,
                    fileName,
                    size
            ));
        } catch (Exception e) {
            log.warn("Failed to record write_file: path={}, error={}", path, e.getMessage());
        }
    }

    /**
     * 工具执行结果
     */
    public record ToolExecutionResult(String callId, ToolResult result) {
    }
}
