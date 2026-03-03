package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.llm.model.LlmResponse;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ReAct 循环运行上下文
 * 封装运行时状态、配置和控制信号
 */
@Getter
@Builder
public class RunContext {

    /**
     * 运行 ID
     */
    private final String runId;

    /**
     * 连接 ID（用于事件推送）
     */
    private final String connectionId;

    /**
     * 会话 ID
     */
    private final String sessionId;

    /**
     * Agent ID（关联的 Agent Profile）
     */
    @Builder.Default
    private final String agentId = "main";

    /**
     * 运行类型：main / subagent（兼容）/ worker（语义别名）
     */
    @Builder.Default
    private final String runKind = "main";

    /**
     * 执行通道：main / subagent（兼容）/ worker（语义别名）
     */
    @Builder.Default
    private final String lane = "main";

    /**
     * 父运行 ID（仅 subagent 有值）
     */
    private final String parentRunId;

    /**
     * 请求方会话 ID（subagent 的父会话）
     */
    private final String requesterSessionId;

    /**
     * 派生深度（main=0, 直接子代理=1）
     */
    @Builder.Default
    private final int depth = 0;

    /**
     * 是否转发中间流到父会话
     */
    @Builder.Default
    private final boolean deliver = false;

    /**
     * 循环开始时间
     */
    private final Instant startTime;

    /**
     * 循环配置
     */
    private final LoopConfig config;

    /**
     * 取消管理器
     */
    private final CancellationManager cancellationManager;

    /**
     * 当前步数（线程安全）
     */
    @Builder.Default
    private final AtomicInteger currentStep = new AtomicInteger(0);

    /**
     * 整个 run 累计 token 消耗（线程安全）
     */
    @Builder.Default
    private final AtomicInteger totalInputTokens = new AtomicInteger(0);

    @Builder.Default
    private final AtomicInteger totalOutputTokens = new AtomicInteger(0);

    @Builder.Default
    private final AtomicInteger totalCacheReadTokens = new AtomicInteger(0);

    /**
     * 原始用户输入（用于 skill 激活）
     */
    @Setter
    private String originalInput;

    /**
     * 当前激活的 skill（如果有）
     */
    @Setter
    private ContextBuilder.SkillAwareRequest activeSkill;

    /**
     * 当前激活的 skill 的资源目录
     */
    @Setter
    private Path skillBasePath;

    /**
     * 排除的 MCP 服务器名称集合（按会话过滤）
     */
    @Setter
    private Set<String> excludedMcpServers;

    /**
     * Agent Profile 级工具白名单（null/empty 表示不限制）
     */
    @Setter
    private Set<String> agentAllowedTools;

    /**
     * Agent Profile 级工具黑名单
     */
    @Setter
    private Set<String> agentDeniedTools;

    /**
     * Strategy 级工具白名单（null/empty 表示不限制）
     */
    @Setter
    private Set<String> strategyAllowedTools;

    /**
     * 当前轮最终可执行的工具名称集合（用于调度层强约束）
     */
    @Setter
    private Set<String> resolvedToolNames;

    /**
     * 用户选择的模型，格式 "providerId:modelName"（可空，为空使用默认模型）
     */
    @Setter
    private String modelSelection;

    /**
     * Skill 激活计数器（skillName -> count）（线程安全）
     */
    @Builder.Default
    private final Map<String, Integer> skillActivationCounts = new ConcurrentHashMap<>();

    /**
     * 最大单个 Skill 激活次数
     */
    private static final int MAX_SKILL_ACTIVATIONS = 3;

    /**
     * 检查是否已被取消
     */
    public boolean isAborted() {
        return cancellationManager.isCancelled(runId);
    }

    /**
     * 记录 Skill 激活并返回当前计数
     */
    public int incrementSkillActivation(String skillName) {
        int count = skillActivationCounts.getOrDefault(skillName, 0) + 1;
        skillActivationCounts.put(skillName, count);
        return count;
    }

    /**
     * 检查 Skill 是否已达到最大激活次数
     */
    public boolean isSkillActivationLimitReached(String skillName) {
        return skillActivationCounts.getOrDefault(skillName, 0) >= MAX_SKILL_ACTIVATIONS;
    }

    /**
     * 检查是否已超时（整个循环）
     */
    public boolean isTimedOut() {
        long elapsedSeconds = Duration.between(startTime, Instant.now()).getSeconds();
        return elapsedSeconds > config.getRunTimeoutSeconds();
    }

    /**
     * 检查是否达到最大步数
     */
    public boolean isMaxStepsReached() {
        return currentStep.get() >= config.getMaxSteps();
    }

    /**
     * 增加步数
     */
    public void incrementStep() {
        this.currentStep.incrementAndGet();
    }

    /**
     * 获取当前步数
     */
    public int getCurrentStep() {
        return currentStep.get();
    }

    /**
     * 累加单次 LLM 调用的 usage（null-safe）
     */
    public void addUsage(LlmResponse.Usage usage) {
        if (usage == null) {
            return;
        }
        if (usage.getPromptTokens() != null) {
            totalInputTokens.addAndGet(usage.getPromptTokens());
        }
        if (usage.getCompletionTokens() != null) {
            totalOutputTokens.addAndGet(usage.getCompletionTokens());
        }
        if (usage.getCacheReadInputTokens() != null) {
            totalCacheReadTokens.addAndGet(usage.getCacheReadInputTokens());
        }
    }

    public int getTotalInputTokens() {
        return totalInputTokens.get();
    }

    public int getTotalOutputTokens() {
        return totalOutputTokens.get();
    }

    public int getTotalCacheReadTokens() {
        return totalCacheReadTokens.get();
    }

    public int getTotalTokens() {
        return totalInputTokens.get() + totalOutputTokens.get();
    }

    /**
     * 获取已用时间（秒）
     */
    public long getElapsedSeconds() {
        return Duration.between(startTime, Instant.now()).getSeconds();
    }

    /**
     * 创建上下文（兼容旧接口，默认为 main run）
     */
    public static RunContext create(
            String runId,
            String connectionId,
            String sessionId,
            LoopConfig config,
            CancellationManager cancellationManager) {
        return create(runId, connectionId, sessionId, "main", config, cancellationManager);
    }

    /**
     * 创建上下文（主运行，显式指定 agentId）
     */
    public static RunContext create(
            String runId,
            String connectionId,
            String sessionId,
            String agentId,
            LoopConfig config,
            CancellationManager cancellationManager) {
        return RunContext.builder()
                .runId(runId)
                .connectionId(connectionId)
                .sessionId(sessionId)
                .agentId(agentId != null && !agentId.isBlank() ? agentId : "main")
                .runKind("main")
                .lane("main")
                .depth(0)
                .deliver(false)
                .startTime(Instant.now())
                .config(config)
                .cancellationManager(cancellationManager)
                .currentStep(new AtomicInteger(0))
                .build();
    }

    /**
     * 创建子代理运行上下文
     *
     * @param runId              子运行 ID
     * @param connectionId       连接 ID
     * @param sessionId          子会话 ID
     * @param agentId            Agent Profile ID
     * @param parentRunId        父运行 ID
     * @param requesterSessionId 请求方会话 ID
     * @param deliver            是否转发中间流
     * @param config             循环配置
     * @param cancellationManager 取消管理器
     * @return 子代理运行上下文
     */
    public static RunContext createSubagent(
            String runId,
            String connectionId,
            String sessionId,
            String agentId,
            String parentRunId,
            String requesterSessionId,
            boolean deliver,
            LoopConfig config,
            CancellationManager cancellationManager) {
        return RunContext.builder()
                .runId(runId)
                .connectionId(connectionId)
                .sessionId(sessionId)
                .agentId(agentId)
                .runKind("subagent")
                .lane("subagent")
                .parentRunId(parentRunId)
                .requesterSessionId(requesterSessionId)
                .depth(1)
                .deliver(deliver)
                .startTime(Instant.now())
                .config(config)
                .cancellationManager(cancellationManager)
                .currentStep(new AtomicInteger(0))
                .build();
    }

    /**
     * 创建 Worker 运行上下文（语义别名，兼容使用 subagent lane/runkind 的旧链路）
     */
    public static RunContext createWorker(
            String runId,
            String connectionId,
            String sessionId,
            String agentId,
            String parentRunId,
            String requesterSessionId,
            boolean deliver,
            LoopConfig config,
            CancellationManager cancellationManager) {
        return createSubagent(
                runId,
                connectionId,
                sessionId,
                agentId,
                parentRunId,
                requesterSessionId,
                deliver,
                config,
                cancellationManager
        );
    }

    /**
     * 判断是否为子代理运行
     */
    public boolean isSubagent() {
        return "subagent".equals(runKind);
    }

    /**
     * 判断是否为 Worker 运行（兼容 subagent 旧值）
     */
    public boolean isWorker() {
        return "worker".equals(runKind) || "subagent".equals(runKind);
    }

    /**
     * 判断是否为主运行
     */
    public boolean isMain() {
        return "main".equals(runKind);
    }

    /**
     * 判断是否为定时任务运行
     */
    public boolean isScheduled() {
        return "scheduled".equals(runKind);
    }

    /**
     * 创建定时任务运行上下文
     *
     * @param runId              运行 ID
     * @param sessionId          会话 ID
     * @param config             循环配置
     * @param cancellationManager 取消管理器
     * @return 定时任务运行上下文
     */
    public static RunContext createScheduled(
            String runId,
            String sessionId,
            LoopConfig config,
            CancellationManager cancellationManager) {
        return createScheduled(runId, sessionId, "main", config, cancellationManager);
    }

    /**
     * 创建定时任务运行上下文（显式指定 agentId）
     */
    public static RunContext createScheduled(
            String runId,
            String sessionId,
            String agentId,
            LoopConfig config,
            CancellationManager cancellationManager) {
        return RunContext.builder()
                .runId(runId)
                .connectionId("__scheduled__")
                .sessionId(sessionId)
                .agentId(agentId != null && !agentId.isBlank() ? agentId : "main")
                .runKind("scheduled")
                .lane("main")
                .depth(0)
                .deliver(false)
                .startTime(Instant.now())
                .config(config)
                .cancellationManager(cancellationManager)
                .currentStep(new AtomicInteger(0))
                .build();
    }
}
