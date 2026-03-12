package com.jaguarliu.ai.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.agents.context.AgentWorkspaceResolver;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;

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

    /**
     * HITL 拒绝标记：用于运行时识别并阻断自动重试循环。
     */
    public static final String HITL_REJECTED_MARKER = "HITL_REJECTED";

    /**
     * HITL 拒绝错误文案（会被 ToolResult.error 包装为 "Error: ..."）。
     */
    public static final String HITL_REJECTED_MESSAGE =
            HITL_REJECTED_MARKER + ": Tool execution rejected by user";

    private final ToolRegistry toolRegistry;
    private final ToolDispatcher toolDispatcher;
    private final HitlManager hitlManager;
    private final EventBus eventBus;
    private final SessionFileService sessionFileService;
    private final AgentWorkspaceResolver agentWorkspaceResolver;

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
            if (context.isAborted()) {
                throw new CancellationException("Run cancelled by user");
            }
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
        if (context.isAborted()) {
            throw new CancellationException("Run cancelled by user");
        }

        String toolName = toolCall.getName();
        String callId = toolCall.getId();
        String argumentsJson = toolCall.getArguments();

        log.info("Executing tool: name={}, callId={}, runId={}, step={}",
                toolName, callId, context.getRunId(), context.getCurrentStep());

        Map<String, Object> arguments = parseArguments(argumentsJson);
        Set<String> resolvedToolNames = context.getResolvedToolNames();
        Set<String> confirmBefore = resolveConfirmBefore(context);

        // 定时任务运行时跳过所有 HITL 确认
        boolean isScheduledRun = "scheduled".equals(context.getRunKind());
        boolean requiresHitl = !isScheduledRun && toolDispatcher.requiresHitl(toolName, confirmBefore, arguments);

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

                ToolResult rejectResult = ToolResult.error(
                        HITL_REJECTED_MESSAGE +
                        ". Do not retry automatically; ask user for explicit approval.",
                        RuntimeFailureCategories.HITL_REJECTED
                );

                // 发布 tool.result 事件（被拒绝）
                eventBus.publish(AgentEvent.toolResult(
                        context.getConnectionId(),
                        context.getRunId(),
                        callId,
                        false,
                        rejectResult.getContent()));

                return new ToolExecutionResult(callId, rejectResult, inferFailureCategory(rejectResult));
            }

            // 如果用户修改了参数，使用修改后的参数
            if (decision.getModifiedArguments() != null && !decision.getModifiedArguments().isEmpty()) {
                arguments = decision.getModifiedArguments();
                log.info("Using modified arguments for tool: name={}, callId={}", toolName, callId);
            }
        }

        if (context.isAborted()) {
            throw new CancellationException("Run cancelled by user");
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
            result = toolDispatcher.dispatch(toolName, arguments, resolvedToolNames).block();
        } finally {
            ToolExecutionContext.clear();
        }

        if (result == null) {
            result = ToolResult.error("Tool execution returned null");
        }

        // 记录成功写入文件的工具（write_file 及所有 producesFile=true 的工具）
        final ToolResult finalResult = result;
        final Map<String, Object> finalArguments = arguments;
        if (finalResult.isSuccess()) {
            toolRegistry.get(toolName).ifPresent(tool -> {
                var def = tool.getDefinition();
                if (def != null && def.isProducesFile()) {
                    recordFileCreated(context, finalArguments);
                }
            });
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

        return new ToolExecutionResult(callId, result, inferFailureCategory(result));
    }

    private Set<String> resolveConfirmBefore(RunContext context) {
        ContextBuilder.SkillAwareRequest activeSkill = context.getActiveSkill();
        if (activeSkill != null && activeSkill.hasActiveSkill()) {
            return activeSkill.confirmBefore();
        }
        return null;
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

        builder.sessionWorkspacePath(agentWorkspaceResolver.resolveAgentWorkspace(context.getAgentId()));

        // 如果有激活的 skill，添加其资源目录到允许路径
        if (context.getSkillBasePath() != null) {
            builder.addAllowedPath(context.getSkillBasePath());
            log.debug("Added skill resource path to tool context: {}", context.getSkillBasePath());
        }

        ToolExecutionContext.set(builder.build());
    }

    /**
     * 记录文件产出工具成功创建的文件，发布 FILE_CREATED 事件。
     * 适用于 write_file、create_xlsx、create_docx 等工具。
     */
    private void recordFileCreated(RunContext context, Map<String, Object> arguments) {
        String pathArg = (String) arguments.get("path");
        if (pathArg == null) return;

        // 尝试从实际磁盘获取文件大小（适用于二进制文件）
        long size = 0;
        try {
            Path workspace = agentWorkspaceResolver.resolveAgentWorkspace(context.getAgentId());
            Path filePath = workspace.resolve(pathArg).normalize();
            if (Files.exists(filePath)) {
                size = Files.size(filePath);
            }
        } catch (Exception e) {
            // fallback：text 工具通过 content 参数估算大小
            String content = (String) arguments.get("content");
            if (content != null) {
                size = content.getBytes(StandardCharsets.UTF_8).length;
            }
        }

        String fileName = Path.of(pathArg).getFileName().toString();

        try {
            var entity = sessionFileService.record(
                    context.getSessionId(),
                    context.getRunId(),
                    pathArg,
                    fileName,
                    size
            );

            // 发布 file.created 事件
            eventBus.publish(AgentEvent.fileCreated(
                    context.getConnectionId(),
                    context.getRunId(),
                    entity.getId(),
                    pathArg,
                    fileName,
                    size
            ));
        } catch (Exception e) {
            log.warn("Failed to record file created: path={}, error={}", pathArg, e.getMessage());
        }
    }

    /**
     * 工具执行结果
     */
    private String inferFailureCategory(ToolResult result) {
        if (result == null || result.isSuccess()) {
            return null;
        }
        if (result.getFailureCategory() != null && !result.getFailureCategory().isBlank()) {
            return result.getFailureCategory();
        }
        return RuntimeFailureCategories.TOOL_ERROR;
    }

    public record ToolExecutionResult(String callId, ToolResult result, String failureCategory) {
        public ToolExecutionResult(String callId, ToolResult result) {
            this(callId, result, null);
        }
    }
}
