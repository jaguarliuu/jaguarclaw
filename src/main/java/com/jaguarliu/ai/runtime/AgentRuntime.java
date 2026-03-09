package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.gateway.events.AgentEvent;
import com.jaguarliu.ai.gateway.events.EventBus;
import com.jaguarliu.ai.llm.LlmClient;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.llm.model.LlmResponse;
import com.jaguarliu.ai.llm.model.ToolCall;
import com.jaguarliu.ai.memory.flush.PreCompactionFlushHook;
import com.jaguarliu.ai.session.SessionFileService;
import com.jaguarliu.ai.subagent.SubagentCompletionTracker;
import com.jaguarliu.ai.tools.ToolDispatcher;
import com.jaguarliu.ai.tools.ToolRegistry;
import com.jaguarliu.ai.tools.ToolResult;
import com.jaguarliu.ai.tools.ToolVisibilityResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

/**
 * Agent 运行时（重构版）
 * 
 * 职责：协调 ReAct 循环的主控制器
 * 
 * 组件化设计：
 * - LoopOrchestrator：循环控制（步数/超时/取消）
 * - ToolExecutor：工具执行（HITL/分发/结果）
 * - SkillActivator：Skill 激活（检测/激活/上下文）
 * - SubagentBarrier：子代理屏障（等待/结果格式化）
 * 
 * 流程：
 * 1. 调用 LLM（带 tools）
 * 2. 如果返回 tool_calls → ToolExecutor 执行
 * 3. 如果返回 use_skill tool call → SkillActivator 激活
 * 4. 将工具结果追加到上下文
 * 5. LoopOrchestrator 检查是否继续
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRuntime {

    private final DecisionInputFactory decisionInputFactory;
    private final OutcomeApplier outcomeApplier;
    private final PlanEngine planEngine;
    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final ToolDispatcher toolDispatcher;
    private final EventBus eventBus;
    private final LoopConfig loopConfig;
    private final CancellationManager cancellationManager;
    private final HitlManager hitlManager;
    private final ContextBuilder contextBuilder;
    private final PreCompactionFlushHook flushHook;
    private final SubagentCompletionTracker subagentCompletionTracker;
    private final SessionFileService sessionFileService;

    // 新组件
    private final LoopOrchestrator loopOrchestrator;
    private final ToolExecutor toolExecutor;
    private final SkillActivator skillActivator;
    private final SubagentBarrier subagentBarrier;
    private final DecisionEngine defaultDecisionEngine;
    private final PolicySupervisor policySupervisor;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 执行 ReAct 多步循环
     */
    public String executeLoop(String connectionId, String runId, String sessionId,
                              List<LlmRequest.Message> messages, String originalInput) throws TimeoutException {
        RunContext context = RunContext.create(runId, connectionId, sessionId,
                loopConfig, cancellationManager);
        context.setOriginalInput(originalInput);
        cancellationManager.register(runId);

        return executeLoopWithContext(context, messages, originalInput);
    }

    /**
     * 执行 ReAct 循环（带完整上下文）
     */
    public String executeLoopWithContext(RunContext context, List<LlmRequest.Message> messages,
                                          String originalInput) throws TimeoutException {
        if (originalInput != null) {
            context.setOriginalInput(originalInput);
            if (context.getTaskContract() == null) {
                PolicyDecision decision = policySupervisor.evaluate(originalInput, List.of());
                context.setTaskContract(decision.contract());
                if (decision.outcome() != null && !decision.enterHeavyLoop()) {
                    return outcomeApplier.apply(context, Decision.terminal(decision.outcome(), "policy_gate", decision.outcome().detail()), decision.outcome().detail());
                }
            }
        }
        return executeLoopWithContext(context, messages);
    }

    /**
     * 执行 ReAct 循环（带完整上下文，无 originalInput）
     */
    public String executeLoopWithContext(RunContext context, List<LlmRequest.Message> messages) 
            throws TimeoutException {
        try {
            return doExecuteLoop(context, messages);
        } finally {
            cancellationManager.clearCancellation(context.getRunId());
        }
    }

    /**
     * 核心 ReAct 循环
     */
    private String doExecuteLoop(RunContext context, List<LlmRequest.Message> messages) throws TimeoutException {
        log.info("Starting ReAct loop: runId={}, maxSteps={}, timeout={}s",
                context.getRunId(), context.getConfig().getMaxSteps(),
                context.getConfig().getRunTimeoutSeconds());

        List<String> pendingSubRunIds = new ArrayList<>();
        initializeExecutionPlanIfNeeded(context, messages);

        while (true) {
            StopDecision stopDecision = loopOrchestrator.checkStopDecision(context);
            if (stopDecision.stop()) {
                if (stopDecision.isCancelled()) {
                    throw new CancellationException("Run cancelled by user");
                }
                if (stopDecision.isTimeout()) {
                    throw new TimeoutException("ReAct loop timeout after " +
                            context.getElapsedSeconds() + " seconds");
                }
                if (stopDecision.isMaxSteps()) {
                    log.warn("Loop reached max steps: runId={}, maxSteps={}",
                            context.getRunId(), context.getConfig().getMaxSteps());
                    StepResult finalResult = executeSingleStep(context, messages);
                    messages.add(LlmRequest.Message.assistant(finalResult.content()));
                    DecisionInput decisionInput = decisionInputFactory.fromAssistantStep(context, finalResult.content());
                    Decision decision = resolveDecisionStage(context)
                            .verify(context, decisionInput.assistantReply(), decisionInput.observations());
                    if (decision.action() == DecisionAction.ITEM_DONE) {
                        return applyDecision(context, Decision.taskDone(RunOutcome.completed(finalResult.content()), decision.reason(), decision.targetItemId()), finalResult.content());
                    }
                    return applyDecision(context, decision, finalResult.content());
                }
                if (stopDecision.outcome() != null) {
                    return outcomeApplier.apply(context, Decision.terminal(stopDecision.outcome(), null, stopDecision.reason()), stopDecision.outcome().detail());
                }
            }

            // Pre-compaction flush
            flushHook.checkAndFlush(context.getRunId(), messages);

            // 执行单步 LLM 调用
            StepResult result = executeSingleStep(context, messages);

            // 没有 tool_calls
            if (!result.hasToolCalls()) {
                context.clearRuntimeFailureCategories();
                // SubAgent 屏障等待
                if (!pendingSubRunIds.isEmpty() && context.isMain()) {
                    log.info("Waiting for {} pending subagents: runId={}",
                            pendingSubRunIds.size(), context.getRunId());

                    messages.add(LlmRequest.Message.assistant(result.content()));

                    String subagentResultsSummary = subagentBarrier.waitForCompletion(
                            pendingSubRunIds, context);
                    pendingSubRunIds.clear();

                    messages.add(LlmRequest.Message.user(subagentResultsSummary));
                    log.info("Subagent results injected: runId={}", context.getRunId());
                    continue;
                }

                messages.add(LlmRequest.Message.assistant(result.content()));
                DecisionInput decisionInput = decisionInputFactory.fromAssistantStep(context, result.content());
                Decision decision = resolveDecisionStage(context)
                        .verify(context, decisionInput.assistantReply(), decisionInput.observations());
                DecisionResolution resolution = handleDecision(context, decision, result.content(), pendingSubRunIds);
                if (resolution.finalResponse() != null) {
                    return resolution.finalResponse();
                }
                if (resolution.continueLoop()) {
                    loopOrchestrator.incrementStepAndPublish(context);
                    continue;
                }
                log.info("Loop completed normally: runId={}, steps={}",
                        context.getRunId(), context.getCurrentStep());
                return applyDecision(context, decision, result.content());
            }

            // 有 tool_calls，使用 ToolExecutor 执行
            log.info("Step {} has {} tool calls: runId={}",
                    context.getCurrentStep(), result.toolCalls().size(), context.getRunId());

            // 前置 use_skill 激活：先激活 skill，再进入下一轮调用，避免同轮普通工具抢跑。
            Optional<SkillActivator.SkillActivation> toolActivation =
                    skillActivator.detectToolActivation(result.toolCalls(), context);
            if (toolActivation.isPresent()
                    && context.getActiveSkill() != null
                    && context.getActiveSkill().hasActiveSkill()) {
                log.info("skill.activation_skipped reason=already_active skill={} runId={}",
                        toolActivation.get().skillName(), context.getRunId());
            }
            if (toolActivation.isPresent()
                    && (context.getActiveSkill() == null || !context.getActiveSkill().hasActiveSkill())) {
                String skillName = toolActivation.get().skillName();
                List<LlmRequest.Message> cleanHistory = extractHistory(messages);

                Optional<SkillActivator.SkillAwareRequest> skillRequest =
                        skillActivator.applyActivation(toolActivation.get(), cleanHistory,
                                context.getOriginalInput(), context.getAgentId());

                if (skillRequest.isPresent()) {
                    context.incrementSkillActivation(skillName);
                    skillActivator.publishActivationEvent(context, toolActivation.get());

                    List<LlmRequest.Message> historyForContext = extractHistory(messages);
                    messages.clear();
                    messages.addAll(skillRequest.get().messages());

                    Optional<ContextBuilder.SkillAwareRequest> ctxRequest =
                            contextBuilder.handleSkillActivationByName(skillName,
                                    context.getOriginalInput(), historyForContext, true, context.getAgentId());
                    if (ctxRequest.isPresent()) {
                        context.setActiveSkill(ctxRequest.get());
                        context.setSkillBasePath(ctxRequest.get().skillBasePath());
                    }

                    long skippedCalls = result.toolCalls().stream()
                            .filter(tc -> !"use_skill".equals(tc.getName()))
                            .count();
                    if (skippedCalls > 0) {
                        log.info("skill.late_activation_prevented skill={} skippedCalls={} runId={}",
                                skillName, skippedCalls, context.getRunId());
                    }
                    log.info("Re-invoking with skill (via pre-tool activation): {}, runId={}",
                            skillName, context.getRunId());
                    continue;
                }
                log.warn("skill.activation_skipped reason=apply_failed skill={} runId={}",
                        skillName, context.getRunId());
            }

            messages.add(LlmRequest.Message.assistantWithToolCalls(result.toolCalls()));

            // 使用 ToolExecutor 执行工具
            List<ToolExecutor.ToolExecutionResult> toolResults =
                    toolExecutor.executeToolCalls(context, result.toolCalls());

            boolean shouldStopOnHitlReject = false;
            String rejectedToolName = null;
            for (int i = 0; i < result.toolCalls().size(); i++) {
                ToolCall toolCall = result.toolCalls().get(i);
                ToolExecutor.ToolExecutionResult execResult = toolResults.get(i);
                messages.add(LlmRequest.Message.toolResult(
                        toolCall.getId(), execResult.result().getContent()));

                if (isHitlRejectedResult(execResult)) {
                    shouldStopOnHitlReject = true;
                    rejectedToolName = toolCall.getName();
                }

                // 跟踪 sessions_spawn 的 subRunId
                if ("sessions_spawn".equals(toolCall.getName()) && execResult.result().isSuccess()) {
                    String subRunId = parseSubRunIdFromToolResult(execResult.result().getContent());
                    if (subRunId != null) {
                        pendingSubRunIds.add(subRunId);
                        context.currentPlanItem().ifPresent(item -> planEngine.markDelegated(context.getExecutionPlan(), item.getId(), subRunId));
                        log.info("Tracked spawned subagent: subRunId={}, runId={}",
                                subRunId, context.getRunId());
                    }
                }
            }

            if (shouldStopOnHitlReject) {
                String rejectedTool = rejectedToolName != null ? rejectedToolName : "unknown";
                String stopMessage = "Tool call was rejected by user ("
                        + rejectedTool
                        + "). Execution stopped to avoid automatic retry loop.";
                if (context.hasExecutionPlan() && context.currentPlanItem().isPresent()) {
                    planEngine.markBlocked(
                            context.getExecutionPlan(),
                            context.currentPlanItem().get().getId(),
                            "HITL rejected tool: " + rejectedTool
                    );
                }
                messages.add(LlmRequest.Message.assistant(stopMessage));
                log.info("Loop stopped due to HITL rejection: runId={}, tool={}",
                        context.getRunId(), rejectedTool);
                return stopMessage;
            }

            recordToolRoundProgress(context, toolResults);
            DecisionInput decisionInput = decisionInputFactory.fromToolRound(context, toolResults, pendingSubRunIds);
            context.replaceRuntimeFailureCategories(decisionInput.runtimeFailureCategories());
            Decision decision = resolveDecisionStage(context)
                    .verify(context, decisionInput.assistantReply(), decisionInput.observations());
            DecisionResolution resolution = handleDecision(context, decision, decision.feedback(), pendingSubRunIds);
            if (resolution.finalResponse() != null) {
                return resolution.finalResponse();
            }
            if (decision.feedback() != null && !decision.feedback().isBlank()
                    && decision.action() == DecisionAction.CONTINUE_ITEM) {
                messages.add(LlmRequest.Message.user(decision.feedback()));
            }

            loopOrchestrator.incrementStepAndPublish(context);
        }
    }

    void initializeExecutionPlanIfNeeded(RunContext context, List<LlmRequest.Message> messages) {
        if (context == null || context.isPlanInitialized() || context.getOriginalInput() == null || context.getOriginalInput().isBlank()) {
            return;
        }
        ExecutionPlan plan = planEngine.createInitialPlan(context, extractHistory(messages));
        context.setExecutionPlan(plan);
        context.setPlanInitialized(true);
    }

    private List<LlmRequest.Message> enrichMessagesWithPlan(RunContext context, List<LlmRequest.Message> messages) {
        if (context == null || !context.hasExecutionPlan()) {
            return messages;
        }
        List<LlmRequest.Message> enriched = new ArrayList<>(messages);
        String reminder = buildPlanReminder(context);
        if (reminder != null && !reminder.isBlank()) {
            enriched.add(LlmRequest.Message.system(reminder));
        }
        return enriched;
    }

    private String buildPlanReminder(RunContext context) {
        if (!context.hasExecutionPlan()) {
            return null;
        }
        ExecutionPlan plan = context.getExecutionPlan();
        StringBuilder builder = new StringBuilder();
        builder.append("Execution plan reminder:\n")
                .append("- goal: ").append(plan.getGoal()).append("\n")
                .append("- planStatus: ").append(plan.getStatus()).append("\n");
        context.currentPlanItem().ifPresent(item -> builder.append("- currentItem: ")
                .append(item.getId())
                .append(": ")
                .append(item.getTitle())
                .append("\n"));
        String remaining = plan.getItems().stream()
                .filter(item -> item.getStatus() != PlanItemStatus.DONE && item.getStatus() != PlanItemStatus.CANCELLED)
                .map(item -> item.getId() + ":" + item.getTitle() + "(" + item.getStatus() + ")")
                .collect(java.util.stream.Collectors.joining(", "));
        builder.append("- remainingItems: ").append(remaining.isBlank() ? "none" : remaining).append("\n")
                .append("Prefer finishing or advancing the current item before claiming the whole task is done.");
        return builder.toString();
    }

    private DecisionResolution handleDecision(
            RunContext context,
            Decision decision,
            String fallbackMessage,
            List<String> pendingSubRunIds
    ) {
        if (decision == null) {
            return DecisionResolution.keepGoing();
        }
        String targetItemId = decision.targetItemId();
        if (targetItemId == null && context.currentPlanItem().isPresent()) {
            targetItemId = context.currentPlanItem().get().getId();
        }

        return switch (decision.action()) {
            case CONTINUE_ITEM -> DecisionResolution.keepGoing();
            case ITEM_DONE -> {
                if (context.hasExecutionPlan() && targetItemId != null) {
                    planEngine.markDone(context.getExecutionPlan(), targetItemId, decision.reason());
                    planEngine.advance(context.getExecutionPlan());
                    if (context.getExecutionPlan().allItemsDone()) {
                        yield DecisionResolution.finish(applyDecision(
                                context,
                                Decision.taskDone(RunOutcome.completed(fallbackMessage), decision.reason(), targetItemId),
                                fallbackMessage
                        ));
                    }
                }
                yield DecisionResolution.keepGoing();
            }
            case ASK_USER -> {
                context.setPendingQuestion(decision.feedback());
                yield DecisionResolution.finish(decision.feedback() != null ? decision.feedback() : fallbackMessage);
            }
            case BLOCK_ITEM -> {
                if (context.hasExecutionPlan() && targetItemId != null) {
                    planEngine.markBlocked(context.getExecutionPlan(), targetItemId, decision.reason());
                }
                context.setPendingQuestion(decision.feedback());
                yield DecisionResolution.finish(decision.feedback() != null ? decision.feedback() : fallbackMessage);
            }
            case DELEGATE_ITEM -> DecisionResolution.keepGoing();
            case TASK_DONE, STOP -> DecisionResolution.finish(applyDecision(context, decision, fallbackMessage));
        };
    }

    private record DecisionResolution(boolean continueLoop, String finalResponse) {
        static DecisionResolution keepGoing() {
            return new DecisionResolution(true, null);
        }

        static DecisionResolution finish(String response) {
            return new DecisionResolution(false, response);
        }
    }

    RuntimeDecisionStage resolveDecisionStage(RunContext context) {
        return context.getRuntimeDecisionStage() != null ? context.getRuntimeDecisionStage() : defaultDecisionEngine;
    }

    String applyDecision(RunContext context, Decision decision, String fallbackMessage) {
        return outcomeApplier.apply(context, decision, fallbackMessage);
    }

    void recordToolRoundProgress(RunContext context, List<ToolExecutor.ToolExecutionResult> toolResults) {
        boolean hasSuccess = toolResults.stream().anyMatch(result -> result.result() != null && result.result().isSuccess());
        if (hasSuccess) {
            context.recordMeaningfulProgress();
            return;
        }
        context.recordLowProgressRound();
        toolResults.stream()
                .map(ToolExecutor.ToolExecutionResult::failureCategory)
                .filter(category -> category != null && !category.isBlank())
                .forEach(context::recordFailure);
    }


    /**
     * 执行单步 LLM 调用
     */
    private StepResult executeSingleStep(RunContext context, List<LlmRequest.Message> messages) {
        LlmRequest.LlmRequestBuilder requestBuilder = LlmRequest.builder()
                .messages(enrichMessagesWithPlan(context, messages))
                .toolChoice("auto");

        Set<String> excluded = context.getExcludedMcpServers();
        ContextBuilder.SkillAwareRequest activeSkill = context.getActiveSkill();

        Set<String> strategyAllowedTools = resolveStrategyAllowedTools(context, activeSkill);
        Set<String> skillAllowedTools = resolveSkillAllowedTools(activeSkill);

        ToolVisibilityResolver.VisibilityRequest visibilityRequest = ToolVisibilityResolver.VisibilityRequest.builder()
                .agentId(context.getAgentId())
                .agentAllowedTools(context.getAgentAllowedTools())
                .agentDeniedTools(context.getAgentDeniedTools())
                .strategyAllowedTools(strategyAllowedTools)
                .skillAllowedTools(skillAllowedTools)
                .excludedMcpServers(excluded)
                .build();

        List<Map<String, Object>> tools = toolRegistry.toOpenAiTools(visibilityRequest);
        Set<String> resolvedToolNames = toolRegistry.listVisibleToolNames(visibilityRequest);

        if (activeSkill != null && activeSkill.hasActiveSkill()) {
            tools = tools.stream()
                    .filter(t -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> fn = (Map<String, Object>) t.get("function");
                        return fn == null || !"use_skill".equals(fn.get("name"));
                    })
                    .toList();
            resolvedToolNames = resolvedToolNames.stream()
                    .filter(name -> !"use_skill".equals(name))
                    .collect(java.util.stream.Collectors.toSet());
        }

        context.setResolvedToolNames(resolvedToolNames);
        requestBuilder.tools(tools);

        if (context.getModelSelection() != null) {
            String[] parts = context.getModelSelection().split(":", 2);
            if (parts.length == 2) {
                requestBuilder.providerId(parts[0]);
                requestBuilder.model(parts[1]);
            }
        }

        return streamLlmCall(context, requestBuilder.build());
    }

    private Set<String> resolveStrategyAllowedTools(
            RunContext context,
            ContextBuilder.SkillAwareRequest activeSkill
    ) {
        if (context.getStrategyAllowedTools() != null && !context.getStrategyAllowedTools().isEmpty()) {
            return context.getStrategyAllowedTools();
        }
        // 兼容历史链路：曾借用 activeSkill(无 name) 传递 strategy 白名单
        if (activeSkill != null && !activeSkill.hasActiveSkill()) {
            return activeSkill.allowedTools();
        }
        return null;
    }

    private Set<String> resolveSkillAllowedTools(ContextBuilder.SkillAwareRequest activeSkill) {
        if (activeSkill == null || !activeSkill.hasActiveSkill()) {
            return null;
        }
        return activeSkill.allowedTools();
    }

    /**
     * 流式调用 LLM
     */
    private StepResult streamLlmCall(RunContext context, LlmRequest request) {
        String connectionId = context.getConnectionId();
        String runId = context.getRunId();
        StringBuilder content = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();
        ArtifactStreamExtractor artifactExtractor = new ArtifactStreamExtractor();
        LlmResponse.Usage[] usageHolder = {null};

        if (context.isAborted()) {
            throw new CancellationException("Run cancelled by user");
        }

        llmClient.stream(request)
                .doOnNext(chunk -> {
                    if (context.isAborted()) {
                        throw new CancellationException("Run cancelled by user");
                    }

                    if (chunk.getDelta() != null) {
                        content.append(chunk.getDelta());
                        context.setLatestAssistantDraft(content.toString());
                        eventBus.publish(AgentEvent.assistantDelta(connectionId, runId, chunk.getDelta()));
                    }

                    // 收集 tool_calls（最后一个 chunk 包含完整 tool_calls）
                    if (chunk.hasToolCalls()) {
                        toolCalls.clear();
                        toolCalls.addAll(chunk.getToolCalls());
                    }

                    // Artifact 流式提取：基于 write_file 的 arguments delta
                    if ("write_file".equals(chunk.getToolCallFunctionName())) {
                        artifactExtractor.activate();
                    }
                    if (artifactExtractor.isActive() && chunk.getToolCallArgumentsDelta() != null) {
                        ArtifactStreamExtractor.ExtractionResult result =
                                artifactExtractor.append(chunk.getToolCallArgumentsDelta());
                        if (result.pathDetected() != null) {
                            eventBus.publish(AgentEvent.artifactOpen(connectionId, runId, result.pathDetected()));
                        }
                        if (result.contentDelta() != null) {
                            eventBus.publish(AgentEvent.artifactDelta(connectionId, runId, result.contentDelta()));
                        }
                    }

                    if (chunk.getUsage() != null) {
                        usageHolder[0] = chunk.getUsage();
                    }
                })
                .blockLast();

        if (context.isAborted()) {
            throw new CancellationException("Run cancelled by user");
        }

        if (usageHolder[0] != null) {
            context.addUsage(usageHolder[0]);
            int historyCount = (int) request.getMessages().stream()
                    .filter(m -> !"system".equals(m.getRole()))
                    .count() - 1;
            historyCount = Math.max(0, historyCount);
            eventBus.publish(AgentEvent.tokenUsage(
                    connectionId, runId, usageHolder[0], historyCount, context.getCurrentStep()));
            log.debug("Token usage: step={}, input={}, output={}, cacheRead={}",
                    context.getCurrentStep(),
                    usageHolder[0].getPromptTokens(),
                    usageHolder[0].getCompletionTokens(),
                    usageHolder[0].getCacheReadInputTokens());
        }

        return new StepResult(content.toString(), toolCalls);
    }

    /**
     * 提取历史消息
     */
    private List<LlmRequest.Message> extractHistory(List<LlmRequest.Message> messages) {
        List<LlmRequest.Message> history = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            LlmRequest.Message msg = messages.get(i);
            if ("system".equals(msg.getRole())) continue;
            if (i == messages.size() - 1 && "user".equals(msg.getRole())) continue;
            history.add(msg);
        }
        return history;
    }

    /**
     * 解析 sessions_spawn 结果中的 subRunId
     */
    private String parseSubRunIdFromToolResult(String resultContent) {
        if (resultContent == null || resultContent.isBlank()) return null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = MAPPER.readValue(resultContent, Map.class);
            Object accepted = map.get("accepted");
            if (Boolean.TRUE.equals(accepted)) {
                return (String) map.get("subRunId");
            }
        } catch (Exception e) {
            log.debug("Failed to parse subRunId from tool result: {}", e.getMessage());
        }
        return null;
    }

    static boolean isHitlRejectedResult(ToolExecutor.ToolExecutionResult result) {
        return result != null && RuntimeFailureCategories.HITL_REJECTED.equals(result.failureCategory());
    }

    /**
     * 单步结果
     */
    private record StepResult(String content, List<ToolCall> toolCalls) {
        boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }
}
