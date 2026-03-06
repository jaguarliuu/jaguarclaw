package com.jaguarliu.ai.gateway.rpc.handler.agent;

import com.jaguarliu.ai.agents.context.AgentWorkspaceResolver;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jaguarliu.ai.gateway.events.AgentEvent;
import com.jaguarliu.ai.gateway.events.EventBus;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import com.jaguarliu.ai.gateway.security.rate.TokenBudgetService;
import com.jaguarliu.ai.nodeconsole.AuditLogService;
import com.jaguarliu.ai.llm.LlmCapabilityService;
import com.jaguarliu.ai.llm.LlmClient;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.llm.model.LlmResponse;
import com.jaguarliu.ai.runtime.AgentRuntime;
import com.jaguarliu.ai.runtime.CancellationManager;
import com.jaguarliu.ai.runtime.ChatRouter;
import com.jaguarliu.ai.runtime.ContextBuilder;
import com.jaguarliu.ai.runtime.LoopConfig;
import com.jaguarliu.ai.runtime.RunContext;
import com.jaguarliu.ai.runtime.SessionLaneManager;
import com.jaguarliu.ai.runtime.strategy.AgentContext;
import com.jaguarliu.ai.runtime.strategy.AgentExecutionPlan;
import com.jaguarliu.ai.runtime.strategy.AgentStrategy;
import com.jaguarliu.ai.runtime.strategy.AgentStrategyResolver;
import com.jaguarliu.ai.session.MessageService;
import com.jaguarliu.ai.session.RunService;
import com.jaguarliu.ai.session.RunStatus;
import com.jaguarliu.ai.session.SessionService;
import com.jaguarliu.ai.storage.entity.MessageEntity;
import com.jaguarliu.ai.storage.entity.RunEntity;
import com.jaguarliu.ai.storage.entity.SessionEntity;
import com.jaguarliu.ai.tools.ToolConfigProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.time.Duration;

/**
 * agent.run 处理器
 * 创建 run 并通过 SessionLane 串行执行 LLM 调用
 * 执行过程中通过 EventBus 推送流式事件
 * 支持多轮对话历史和 ReAct 工具调用
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRunHandler implements RpcHandler {

    private final SessionService sessionService;
    private final AgentWorkspaceResolver agentWorkspaceResolver;
    private final RunService runService;
    private final MessageService messageService;
    private final SessionLaneManager sessionLaneManager;
    private final EventBus eventBus;
    private final ContextBuilder contextBuilder;
    private final AgentRuntime agentRuntime;
    private final LlmClient llmClient;
    private final LlmCapabilityService llmCapabilityService;
    private final ToolConfigProperties toolConfigProperties;
    private final AgentStrategyResolver strategyResolver;
    private final LoopConfig loopConfig;
    private final CancellationManager cancellationManager;
    private final ChatRouter chatRouter;
    private final ConnectionManager connectionManager;
    private final TokenBudgetService tokenBudgetService;
    private final AuditLogService auditLogService;

    /**
     * 历史消息数量限制（避免上下文过长）
     */
    @Value("${agent.max-history-messages:20}")
    private int maxHistoryMessages;

    /**
     * 每个 session 的锁对象
     */
    private final Cache<String, Object> sessionLocks = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofMinutes(30))
            .build();

    @Override
    public String getMethod() {
        return "agent.run";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        String principalId = resolvePrincipalId(connectionId);
        if (principalId == null) {
            return Mono.just(RpcResponse.error(request.getId(), "UNAUTHORIZED", "Missing authenticated principal"));
        }

        String sessionId = extractSessionId(request.getPayload());
        String prompt = extractPrompt(request.getPayload());
        String requestedAgentId = extractAgentId(request.getPayload());
        List<AttachmentPayload> attachments = extractAttachments(request.getPayload());
        Set<String> excludedMcpServers = extractExcludedMcpServers(request.getPayload());
        String modelSelection = extractModelSelection(request.getPayload());

        if (hasImageAttachments(attachments) && !llmCapabilityService.supportsVision(modelSelection)) {
            return Mono.just(RpcResponse.error(request.getId(), "MODEL_NOT_SUPPORTED", "Selected model does not support image input"));
        }

        if (prompt == null || prompt.isBlank()) {
            return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "Missing prompt"));
        }
        if (!tokenBudgetService.tryConsume(principalId, tokenBudgetService.estimateTokens(prompt))) {
            auditLogService.logSecurityEvent(
                    "ws.rpc.token_budget_exceeded",
                    sessionId,
                    getMethod(),
                    "rejected",
                    "principalId=%s".formatted(principalId),
                    connectionId,
                    request.getId()
            );
            return Mono.just(RpcResponse.error(request.getId(), "TOKEN_BUDGET_EXCEEDED", "Daily token budget exceeded"));
        }

        // 同步创建 run 并获取序号
        PrepareResult result = prepareRun(sessionId, prompt, requestedAgentId, request.getId(), principalId);
        if (result.error != null) {
            return Mono.just(result.error);
        }

        // 立即返回 runId
        RpcResponse response = RpcResponse.success(request.getId(), Map.of(
                "runId", result.run.getId(),
                "sessionId", result.sessionId,
                "agentId", result.run.getAgentId(),
                "status", RunStatus.QUEUED.getValue()
        ));

        // 提交到 SessionLane 异步执行
        sessionLaneManager.submit(
                result.sessionId,
                result.run.getId(),
                result.sequence,
                () -> {
                    executeRun(connectionId, result.run, attachments, excludedMcpServers, modelSelection, principalId);
                    return null;
                }
        ).subscribe();

        return Mono.just(response);
    }

    private PrepareResult prepareRun(String sessionId, String prompt, String requestedAgentId, String requestId, String principalId) {
        String resolvedSessionId;
        String resolvedAgentId;
        String routedPrompt;
        ChatRouter.RouteDecision routeDecision;
        if (sessionId == null || sessionId.isBlank()) {
            routeDecision = chatRouter.route(prompt, requestedAgentId, null);
            resolvedAgentId = routeDecision.agentId();
            routedPrompt = routeDecision.prompt();
            SessionEntity session = sessionService.create("New Session", resolvedAgentId, principalId);
            resolvedSessionId = session.getId();
        } else {
            var sessionOpt = sessionService.get(sessionId, principalId);
            if (sessionOpt.isEmpty()) {
                return new PrepareResult(null, null, 0,
                        RpcResponse.error(requestId, "NOT_FOUND", "Session not found: " + sessionId));
            }
            SessionEntity session = sessionOpt.get();
            routeDecision = chatRouter.route(prompt, requestedAgentId, session.getAgentId());
            resolvedAgentId = routeDecision.agentId();
            routedPrompt = routeDecision.prompt();
            resolvedSessionId = sessionId;
        }

        if (routedPrompt == null || routedPrompt.isBlank()) {
            return new PrepareResult(null, null, 0,
                    RpcResponse.error(requestId, "INVALID_PARAMS", "Missing prompt"));
        }

        if (routeDecision.mentionedAgentId() != null && !routeDecision.mentionResolved()) {
            log.warn("Mentioned agent not found or disabled, fallback to default: mention={}, resolved={}",
                    routeDecision.mentionedAgentId(), resolvedAgentId);
        }

        Object lock = sessionLocks.get(resolvedSessionId, k -> new Object());
        synchronized (lock) {
            long sequence = sessionLaneManager.nextSequence(resolvedSessionId);
            RunEntity run = runService.create(resolvedSessionId, routedPrompt, resolvedAgentId, principalId);
            log.info("Prepared run: sessionId={}, runId={}, seq={}", resolvedSessionId, run.getId(), sequence);
            return new PrepareResult(resolvedSessionId, run, sequence, null);
        }
    }

    /**
     * 执行 run（使用 Agent Strategy 路由到不同策略）
     */
    private void executeRun(String connectionId, RunEntity run, List<AttachmentPayload> attachments, Set<String> excludedMcpServers, String modelSelection, String principalId) {
        String runId = run.getId();
        String sessionId = run.getSessionId();
        String prompt = run.getPrompt();
        RunContext context = null;

        try {
            // 取消可能发生在队列等待阶段，执行前先读取最新状态
            var latestRun = runService.get(runId, principalId);
            if (latestRun.isPresent()) {
                RunStatus latestStatus = RunStatus.fromValue(latestRun.get().getStatus());
                if (latestStatus == RunStatus.CANCELED) {
                    log.info("Skip execution for canceled queued run: id={}", runId);
                    return;
                }
            }

            // 1. lifecycle.start
            runService.updateStatus(runId, RunStatus.RUNNING);
            eventBus.publish(AgentEvent.lifecycleStart(connectionId, runId));

            // 2. 保存用户消息
            LlmRequest.Message currentUserMessage = buildUserMessage(prompt, attachments, run.getAgentId());
            messageService.saveUserMessage(sessionId, runId, currentUserMessage, principalId);

            // 3. 获取历史消息
            List<MessageEntity> history = messageService.getSessionHistory(sessionId, maxHistoryMessages, principalId);
            // 排除刚保存的这条用户消息
            List<LlmRequest.Message> historyMessages = messageService.toRequestMessages(history.stream()
                    .filter(m -> !runId.equals(m.getRunId()))
                    .toList());

            // 4. 构建策略上下文
            AgentContext agentCtx = AgentContext.builder()
                    .sessionId(sessionId)
                    .runId(runId)
                    .connectionId(connectionId)
                    .agentId(run.getAgentId())
                    .prompt(prompt)
                    .excludedMcpServers(excludedMcpServers)
                    .build();

            // 5. 解析策略并生成执行方案
            AgentStrategy strategy = strategyResolver.resolve(agentCtx);
            AgentExecutionPlan plan = strategy.prepare(agentCtx);

            // 6. 组装消息（使用策略的 system prompt + 会话历史 + 用户输入）
            List<LlmRequest.Message> messages = new ArrayList<>();
            messages.add(LlmRequest.Message.system(plan.getSystemPrompt()));
            messages.addAll(historyMessages);
            messages.add(currentUserMessage);

            log.debug("Context built: strategy={}, history={} messages",
                    plan.getStrategyName(), historyMessages.size());

            // 7. 构建 RunContext 并设置工具白名单
            LoopConfig effectiveConfig = plan.getMaxStepsOverride() != null
                    ? LoopConfig.withMaxSteps(plan.getMaxStepsOverride(), loopConfig)
                    : loopConfig;
            context = RunContext.create(runId, connectionId, sessionId,
                    run.getAgentId(), effectiveConfig, cancellationManager);
            context.setExcludedMcpServers(plan.getExcludedMcpServers());
            context.setStrategyAllowedTools(plan.getAllowedTools());
            context.setOriginalInput(prompt);
            if (modelSelection != null && !modelSelection.isBlank()) {
                context.setModelSelection(modelSelection);
            }

            // 8. 执行
            String response = agentRuntime.executeLoopWithContext(context, messages, prompt);

            // 9. 保存助手消息
            messageService.saveAssistantMessage(sessionId, runId, response, principalId);
            tokenBudgetService.tryConsume(principalId, tokenBudgetService.estimateTokens(response));

            // 10. lifecycle.end
            runService.updateStatus(runId, RunStatus.DONE);
            eventBus.publish(AgentEvent.lifecycleEnd(connectionId, runId));

            log.info("Run completed: id={}, strategy={}, response length={}",
                    runId, plan.getStrategyName(), response.length());

            // 11. 自动生成会话标题（首轮对话）
            tryGenerateSessionTitle(connectionId, runId, sessionId, prompt, response, modelSelection, principalId);

        } catch (java.util.concurrent.CancellationException e) {
            log.info("Run cancelled: id={}", runId);
            try {
                String persisted = buildCancellationAssistantMessage(context);
                messageService.saveAssistantMessage(sessionId, runId, persisted, principalId);
            } catch (Exception ex) {
                log.warn("Failed to persist cancellation assistant message: runId={}, error={}",
                        runId, ex.getMessage());
            }
            try {
                runService.updateStatus(runId, RunStatus.CANCELED);
            } catch (Exception ignored) {}
            eventBus.publish(AgentEvent.lifecycleError(connectionId, runId, "Cancelled by user"));
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("Run timed out: id={}", runId);
            try {
                runService.updateStatus(runId, RunStatus.ERROR);
            } catch (Exception ignored) {}
            eventBus.publish(AgentEvent.lifecycleError(connectionId, runId, e.getMessage()));
        } catch (Exception e) {
            log.error("Run failed: id={}", runId, e);
            try {
                runService.updateStatus(runId, RunStatus.ERROR);
            } catch (Exception ignored) {}
            eventBus.publish(AgentEvent.lifecycleError(connectionId, runId, e.getMessage()));
        } finally {
            // 清除搜索结果临时白名单
            toolConfigProperties.clearSearchDiscoveredDomains();
        }
    }

    private String buildCancellationAssistantMessage(RunContext context) {
        String draft = context != null ? context.getLatestAssistantDraft() : null;
        if (draft != null && !draft.isBlank()) {
            return draft + "\n\n---\n_Cancelled by user_";
        }
        return "_Cancelled by user_";
    }

    /**
     * 尝试自动生成会话标题（首轮对话时）
     */
    private void tryGenerateSessionTitle(String connectionId, String runId,
                                          String sessionId, String prompt, String response,
                                          String modelSelection, String principalId) {
        try {
            var sessionOpt = sessionService.get(sessionId, principalId);
            if (sessionOpt.isEmpty()) return;
            String currentName = sessionOpt.get().getName();
            // 只对默认名称的会话生成标题
            if (!"New Conversation".equals(currentName) && !"New Session".equals(currentName)) return;

            // 异步生成，不阻塞主流程
            CompletableFuture.runAsync(() -> {
                try {
                    String title = generateTitle(prompt, response, modelSelection);
                    if (title != null && !title.isBlank()) {
                        sessionService.rename(sessionId, title);
                        eventBus.publish(AgentEvent.sessionRenamed(connectionId, runId, sessionId, title));
                        log.info("Auto-named session: id={}, title={}", sessionId, title);
                    }
                } catch (Exception e) {
                    log.warn("Failed to auto-name session: id={}", sessionId, e);
                }
            });
        } catch (Exception e) {
            log.warn("Failed to check session for auto-naming: id={}", sessionId, e);
        }
    }

    /**
     * 调用 LLM 生成会话标题
     */
    private String generateTitle(String prompt, String response, String modelSelection) {
        String truncatedPrompt = prompt.length() > 500 ? prompt.substring(0, 500) : prompt;
        String truncatedResponse = response.length() > 500 ? response.substring(0, 500) : response;

        LlmRequest.LlmRequestBuilder requestBuilder = LlmRequest.builder()
                .messages(List.of(
                        LlmRequest.Message.system("Generate a short title (max 8 words) for this conversation. " +
                                "Return ONLY the title, no quotes, no punctuation at the end. " +
                                "Use the same language as the user."),
                        LlmRequest.Message.user("User: " + truncatedPrompt + "\n\nAssistant: " + truncatedResponse)
                ))
                .maxTokens(30)
                .temperature(0.5);

        // 使用用户选择的模型（如果有）
        if (modelSelection != null && modelSelection.contains(":")) {
            String[] parts = modelSelection.split(":", 2);
            requestBuilder.providerId(parts[0]);
            requestBuilder.model(parts[1]);
        }

        LlmResponse llmResponse = llmClient.chat(requestBuilder.build());
        String title = llmResponse.getContent();
        if (title == null) return null;
        title = title.trim();
        if (title.length() > 80) title = title.substring(0, 77) + "...";
        return title;
    }

    private String extractSessionId(Object payload) {
        if (payload instanceof Map) {
            Object id = ((Map<?, ?>) payload).get("sessionId");
            return id != null ? id.toString() : null;
        }
        return null;
    }

    private String extractPrompt(Object payload) {
        if (payload instanceof Map) {
            Object prompt = ((Map<?, ?>) payload).get("prompt");
            return prompt != null ? prompt.toString() : null;
        }
        return null;
    }

    private String extractAgentId(Object payload) {
        if (payload instanceof Map) {
            Object agentId = ((Map<?, ?>) payload).get("agentId");
            return agentId != null ? agentId.toString() : null;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<AttachmentPayload> extractAttachments(Object payload) {
        if (!(payload instanceof Map<?, ?> map)) {
            return List.of();
        }

        Object attachments = map.get("attachments");
        if (!(attachments instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }

        List<AttachmentPayload> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> attachmentMap)) {
                continue;
            }
            String type = readString(attachmentMap.get("type"));
            String filePath = readString(attachmentMap.get("filePath"));
            if (type == null || filePath == null) {
                continue;
            }
            result.add(new AttachmentPayload(
                    type,
                    filePath,
                    readString(attachmentMap.get("filename")),
                    readString(attachmentMap.get("mimeType"))
            ));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Set<String> extractExcludedMcpServers(Object payload) {
        if (payload instanceof Map) {
            Object excluded = ((Map<?, ?>) payload).get("excludedMcpServers");
            if (excluded instanceof List<?> list) {
                Set<String> result = new java.util.HashSet<>();
                for (Object item : list) {
                    if (item != null) {
                        result.add(item.toString());
                    }
                }
                return result.isEmpty() ? null : result;
            }
        }
        return null;
    }

    private String extractModelSelection(Object payload) {
        if (payload instanceof Map) {
            Object model = ((Map<?, ?>) payload).get("model");
            return model != null ? model.toString() : null;
        }
        return null;
    }

    private boolean hasImageAttachments(List<AttachmentPayload> attachments) {
        return attachments != null && attachments.stream().anyMatch(AttachmentPayload::isImage);
    }

    private LlmRequest.Message buildUserMessage(String prompt, List<AttachmentPayload> attachments, String agentId) {
        if (attachments == null || attachments.isEmpty()) {
            return LlmRequest.Message.user(prompt);
        }

        List<LlmRequest.ContentPart> parts = new ArrayList<>();
        if (prompt != null && !prompt.isBlank()) {
            parts.add(LlmRequest.ContentPart.text(prompt));
        }

        attachments.stream()
                .filter(AttachmentPayload::isImage)
                .map(attachment -> LlmRequest.ContentPart.image(LlmRequest.ImagePart.builder()
                        .filePath(attachment.filePath())
                        .storagePath(resolveAttachmentStoragePath(agentId, attachment.filePath()))
                        .mimeType(attachment.mimeType())
                        .fileName(attachment.fileName())
                        .build()))
                .forEach(parts::add);

        if (parts.isEmpty()) {
            return LlmRequest.Message.user(prompt);
        }
        return LlmRequest.Message.userWithParts(prompt, parts);
    }

    private String resolveAttachmentStoragePath(String agentId, String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }
        return agentWorkspaceResolver.resolveAgentWorkspace(agentId)
                .resolve(filePath)
                .toAbsolutePath()
                .normalize()
                .toString();
    }

    private String readString(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private String resolvePrincipalId(String connectionId) {
        var principal = connectionManager.getPrincipal(connectionId);
        return principal != null ? principal.getPrincipalId() : null;
    }

    private record PrepareResult(String sessionId, RunEntity run, long sequence, RpcResponse error) {}

    private record AttachmentPayload(String type, String filePath, String fileName, String mimeType) {
        private boolean isImage() {
            if (type == null) {
                return false;
            }
            return "image".equalsIgnoreCase(type);
        }
    }
}
