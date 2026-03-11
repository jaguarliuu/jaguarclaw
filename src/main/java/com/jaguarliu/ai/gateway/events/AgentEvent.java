package com.jaguarliu.ai.gateway.events;

import com.jaguarliu.ai.llm.model.LlmResponse;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.util.Map;

/**
 * Agent 事件模型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentEvent {

    /**
     * 事件类型
     */
    private EventType type;

    /**
     * 关联的 runId
     */
    private String runId;

    /**
     * 关联的 connectionId（用于路由到正确的 WebSocket 连接）
     */
    private String connectionId;

    /**
     * 事件数据
     */
    private Object data;

    /**
     * 事件类型枚举
     */
    public enum EventType {
        LIFECYCLE_START("lifecycle.start"),
        ASSISTANT_DELTA("assistant.delta"),
        LIFECYCLE_END("lifecycle.end"),
        LIFECYCLE_ERROR("lifecycle.error"),
        STEP_COMPLETED("step.completed"),
        TOOL_CALL("tool.call"),
        TOOL_RESULT("tool.result"),
        TOOL_CONFIRM_REQUEST("tool.confirm_request"),
        SKILL_ACTIVATED("skill.activated"),
        // SubAgent 事件
        SUBAGENT_SPAWNED("subagent.spawned"),
        SUBAGENT_STARTED("subagent.started"),
        SUBAGENT_ANNOUNCED("subagent.announced"),
        SUBAGENT_FAILED("subagent.failed"),
        SESSION_RENAMED("session.renamed"),
        // Artifact 流式预览事件
        ARTIFACT_OPEN("artifact.open"),
        ARTIFACT_DELTA("artifact.delta"),
        // 文件创建事件
        FILE_CREATED("file.created"),
        // Heartbeat 通知
        HEARTBEAT_NOTIFY("heartbeat.notify"),
        // Token 监控事件
        TOKEN_USAGE("token.usage"),
        RUN_OUTCOME("run.outcome"),
        // P2 扩展占位：上下文压缩通知
        CONTEXT_COMPACTED("context.compacted"),
        // 文档内容插入事件（document-writer agent 实时推送）
        DOC_CONTENT_INSERT("doc.content.insert"),
        // 文档节点插入事件（draw_mermaid / draw_chart 工具推送 TipTap 节点）
        DOC_NODE_INSERT("doc.node.insert");

        private final String value;

        EventType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 创建 lifecycle.start 事件
     */
    public static AgentEvent lifecycleStart(String connectionId, String runId) {
        return AgentEvent.builder()
                .type(EventType.LIFECYCLE_START)
                .connectionId(connectionId)
                .runId(runId)
                .build();
    }

    /**
     * 创建 assistant.delta 事件
     */
    public static AgentEvent assistantDelta(String connectionId, String runId, String content) {
        return AgentEvent.builder()
                .type(EventType.ASSISTANT_DELTA)
                .connectionId(connectionId)
                .runId(runId)
                .data(new DeltaData(content))
                .build();
    }

    /**
     * 创建 lifecycle.end 事件
     */
    public static AgentEvent lifecycleEnd(String connectionId, String runId) {
        return AgentEvent.builder()
                .type(EventType.LIFECYCLE_END)
                .connectionId(connectionId)
                .runId(runId)
                .build();
    }

    /**
     * 创建 lifecycle.error 事件
     */
    public static AgentEvent lifecycleError(String connectionId, String runId, String error) {
        return AgentEvent.builder()
                .type(EventType.LIFECYCLE_ERROR)
                .connectionId(connectionId)
                .runId(runId)
                .data(new ErrorData(error))
                .build();
    }

    /**
     * 创建 step.completed 事件
     */
    public static AgentEvent stepCompleted(String connectionId, String runId, int step, int maxSteps, long elapsedSeconds) {
        return AgentEvent.builder()
                .type(EventType.STEP_COMPLETED)
                .connectionId(connectionId)
                .runId(runId)
                .data(new StepData(step, maxSteps, elapsedSeconds))
                .build();
    }

    /**
     * 创建 tool.call 事件（工具调用开始）
     */
    public static AgentEvent toolCall(String connectionId, String runId, String callId, String toolName, Object arguments) {
        return AgentEvent.builder()
                .type(EventType.TOOL_CALL)
                .connectionId(connectionId)
                .runId(runId)
                .data(new ToolCallData(callId, toolName, arguments))
                .build();
    }

    /**
     * 创建 tool.result 事件（工具调用结果）
     */
    public static AgentEvent toolResult(String connectionId, String runId, String callId, boolean success, String content) {
        return AgentEvent.builder()
                .type(EventType.TOOL_RESULT)
                .connectionId(connectionId)
                .runId(runId)
                .data(new ToolResultData(callId, success, content))
                .build();
    }

    /**
     * 创建 tool.confirm_request 事件（请求 HITL 确认）
     */
    public static AgentEvent toolConfirmRequest(String connectionId, String runId, String callId, String toolName, Object arguments) {
        return AgentEvent.builder()
                .type(EventType.TOOL_CONFIRM_REQUEST)
                .connectionId(connectionId)
                .runId(runId)
                .data(new ToolConfirmRequestData(callId, toolName, arguments))
                .build();
    }

    /**
     * 创建 skill.activated 事件
     */
    public static AgentEvent skillActivated(String connectionId, String runId, String skillName, String source) {
        return AgentEvent.builder()
                .type(EventType.SKILL_ACTIVATED)
                .connectionId(connectionId)
                .runId(runId)
                .data(new SkillActivatedData(skillName, source))
                .build();
    }

    /**
     * 创建 session.renamed 事件
     */
    public static AgentEvent sessionRenamed(String connectionId, String runId, String sessionId, String name) {
        return AgentEvent.builder()
                .type(EventType.SESSION_RENAMED)
                .connectionId(connectionId)
                .runId(runId)
                .data(new SessionRenamedData(sessionId, name))
                .build();
    }

    /**
     * 创建 artifact.open 事件（AI 开始写文件，打开预览面板）
     */
    public static AgentEvent artifactOpen(String connectionId, String runId, String path) {
        return AgentEvent.builder()
                .type(EventType.ARTIFACT_OPEN)
                .connectionId(connectionId)
                .runId(runId)
                .data(new ArtifactOpenData(path))
                .build();
    }

    /**
     * 创建 artifact.delta 事件（文件内容增量到达）
     */
    public static AgentEvent artifactDelta(String connectionId, String runId, String content) {
        return AgentEvent.builder()
                .type(EventType.ARTIFACT_DELTA)
                .connectionId(connectionId)
                .runId(runId)
                .data(new ArtifactDeltaData(content))
                .build();
    }

    /**
     * 创建 file.created 事件（文件写入成功）
     */
    public static AgentEvent fileCreated(String connectionId, String runId,
                                          String fileId, String path, String fileName, long size) {
        return AgentEvent.builder()
                .type(EventType.FILE_CREATED)
                .connectionId(connectionId)
                .runId(runId)
                .data(new FileCreatedData(fileId, path, fileName, size))
                .build();
    }

    /**
     * 创建 heartbeat.notify 事件
     */
    public static AgentEvent heartbeatNotify(String connectionId, String agentId, String content, String sessionId, String runId) {
        return AgentEvent.builder()
                .type(EventType.HEARTBEAT_NOTIFY)
                .connectionId(connectionId)
                .runId(runId)
                .data(new HeartbeatNotifyData(agentId, content, sessionId, runId))
                .build();
    }

    /**
     * 创建 token.usage 事件（每次 LLM 调用后发布）
     */
    public static AgentEvent tokenUsage(String connectionId, String runId,
                                        LlmResponse.Usage usage,
                                        int historyMessages, int step) {
        int cacheRead = usage.getCacheReadInputTokens() != null
                ? usage.getCacheReadInputTokens() : 0;
        return AgentEvent.builder()
                .type(EventType.TOKEN_USAGE)
                .connectionId(connectionId)
                .runId(runId)
                .data(new TokenUsageData(
                        usage.getPromptTokens() != null ? usage.getPromptTokens() : 0,
                        usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0,
                        usage.getTotalTokens() != null ? usage.getTotalTokens() : 0,
                        cacheRead,
                        historyMessages,
                        step
                ))
                .build();
    }

    public static AgentEvent runOutcome(String connectionId, String runId,
                                        String status, String reason,
                                        int step, int totalTokens) {
        return runOutcome(connectionId, runId, status, reason, null, null, null, null, null, step, totalTokens);
    }

    public static AgentEvent runOutcome(String connectionId, String runId,
                                        String status, String reason,
                                        String message, String detail,
                                        String pendingQuestion,
                                        String planStatus,
                                        String currentItemId,
                                        int step, int totalTokens) {
        return AgentEvent.builder()
                .type(EventType.RUN_OUTCOME)
                .connectionId(connectionId)
                .runId(runId)
                .data(new RunOutcomeData(status, reason, message, detail, pendingQuestion, planStatus, currentItemId, step, totalTokens))
                .build();
    }

    /**
     * 创建 doc.content.insert 事件（document-writer agent 向编辑器实时插入内容）
     */
    public static AgentEvent docContentInsert(String connectionId, String runId, String content) {
        return AgentEvent.builder()
                .type(EventType.DOC_CONTENT_INSERT)
                .connectionId(connectionId)
                .runId(runId)
                .data(new DocContentInsertData(content))
                .build();
    }

    @Data
    @AllArgsConstructor
    public static class DeltaData {
        private String content;
    }

    @Data
    @AllArgsConstructor
    public static class ErrorData {
        private String message;
    }

    @Data
    @AllArgsConstructor
    public static class StepData {
        private int step;
        private int maxSteps;
        private long elapsedSeconds;
    }

    @Data
    @AllArgsConstructor
    public static class ToolCallData {
        private String callId;
        private String toolName;
        private Object arguments;
    }

    @Data
    @AllArgsConstructor
    public static class ToolResultData {
        private String callId;
        private boolean success;
        private String content;
    }

    @Data
    @AllArgsConstructor
    public static class ToolConfirmRequestData {
        private String callId;
        private String toolName;
        private Object arguments;
    }

    @Data
    @AllArgsConstructor
    public static class SkillActivatedData {
        private String skillName;
        private String source;  // "manual" or "auto"
    }

    @Data
    @AllArgsConstructor
    public static class SessionRenamedData {
        private String sessionId;
        private String name;
    }

    @Data
    @AllArgsConstructor
    public static class ArtifactOpenData {
        private String path;
    }

    @Data
    @AllArgsConstructor
    public static class ArtifactDeltaData {
        private String content;
    }

    @Data
    @AllArgsConstructor
    public static class FileCreatedData {
        private String fileId;
        private String path;
        private String fileName;
        private long size;
    }

    // ==================== SubAgent 事件 ====================

    /**
     * 创建 subagent.spawned 事件（子代理已派生）
     */
    public static AgentEvent subagentSpawned(String connectionId, String parentRunId,
                                              String subRunId, String subSessionId,
                                              String sessionKey, String agentId,
                                              String task, String lane) {
        return AgentEvent.builder()
                .type(EventType.SUBAGENT_SPAWNED)
                .connectionId(connectionId)
                .runId(parentRunId)
                .data(new SubagentSpawnedData(subRunId, subSessionId, sessionKey, agentId, task, lane))
                .build();
    }

    /**
     * 创建 subagent.started 事件（子代理开始执行）
     */
    public static AgentEvent subagentStarted(String connectionId, String parentRunId, String subRunId) {
        return AgentEvent.builder()
                .type(EventType.SUBAGENT_STARTED)
                .connectionId(connectionId)
                .runId(parentRunId)
                .data(new SubagentStartedData(subRunId))
                .build();
    }

    /**
     * 创建 subagent.announced 事件（子代理完成并回传结果）
     */
    public static AgentEvent subagentAnnounced(String connectionId, String parentRunId,
                                                String subRunId, String subSessionId,
                                                String sessionKey, String agentId,
                                                String task, String status,
                                                String result, String error,
                                                long durationMs) {
        return AgentEvent.builder()
                .type(EventType.SUBAGENT_ANNOUNCED)
                .connectionId(connectionId)
                .runId(parentRunId)
                .data(new SubagentAnnouncedData(subRunId, subSessionId, sessionKey,
                        agentId, task, status, result, error, durationMs))
                .build();
    }

    /**
     * 创建 subagent.failed 事件（子代理执行失败）
     */
    public static AgentEvent subagentFailed(String connectionId, String parentRunId,
                                             String subRunId, String agentId,
                                             String task, String error) {
        return AgentEvent.builder()
                .type(EventType.SUBAGENT_FAILED)
                .connectionId(connectionId)
                .runId(parentRunId)
                .data(new SubagentFailedData(subRunId, agentId, task, error))
                .build();
    }

    @Data
    @AllArgsConstructor
    public static class SubagentSpawnedData {
        private String subRunId;
        private String subSessionId;
        private String sessionKey;
        private String agentId;
        private String task;
        private String lane;
    }

    @Data
    @AllArgsConstructor
    public static class SubagentStartedData {
        private String subRunId;
    }

    @Data
    @AllArgsConstructor
    public static class SubagentAnnouncedData {
        private String subRunId;
        private String subSessionId;
        private String sessionKey;
        private String agentId;
        private String task;
        private String status;
        private String result;
        private String error;
        private long durationMs;
    }

    @Data
    @AllArgsConstructor
    public static class SubagentFailedData {
        private String subRunId;
        private String agentId;
        private String task;
        private String error;
    }

    @Data
    @AllArgsConstructor
    public static class HeartbeatNotifyData {
        private String agentId;
        private String content;
        private String sessionId;
        private String runId;
    }

    @Data
    @AllArgsConstructor
    public static class TokenUsageData {
        private int inputTokens;
        private int outputTokens;
        private int totalTokens;
        private int cacheReadTokens;
        private int historyMessages;
        private int step;
    }

    @Data
    @AllArgsConstructor
    public static class RunOutcomeData {
        private String status;
        private String reason;
        private String message;
        private String detail;
        private String pendingQuestion;
        private String planStatus;
        private String currentItemId;
        private int step;
        private int totalTokens;
    }

    @Data
    @AllArgsConstructor
    public static class DocContentInsertData {
        private String content;
    }

    /**
     * 创建 doc.node.insert 事件（draw_mermaid / draw_chart 工具向编辑器插入 TipTap 节点）
     */
    public static AgentEvent docNodeInsert(String connectionId, String runId, String docId, Object node) {
        return AgentEvent.builder()
                .type(EventType.DOC_NODE_INSERT)
                .connectionId(connectionId)
                .runId(runId)
                .data(new DocNodeInsertData(docId, node))
                .build();
    }

    @Data
    @AllArgsConstructor
    public static class DocNodeInsertData {
        private String docId;
        private Object node;
    }

}
