package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.skills.index.SkillIndexBuilder;
import com.jaguarliu.ai.skills.model.LoadedSkill;
import com.jaguarliu.ai.skills.registry.SkillRegistry;
import com.jaguarliu.ai.skills.selector.SkillSelection;
import com.jaguarliu.ai.skills.selector.SkillSelector;
import com.jaguarliu.ai.skills.template.SkillTemplateEngine;
import com.jaguarliu.ai.tools.ToolRegistry;
import com.jaguarliu.ai.tools.ToolVisibilityResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 上下文构建器
 * 负责组装 LLM 请求的 messages 列表
 *
 * 支持 Skill 集成：
 * - 索引注入：在 system prompt 中添加可用 skill 列表
 * - Skill 激活：激活 skill 后注入完整指令
 * - 工具限制：根据 skill 的 allowed-tools 过滤工具
 */
@Slf4j
@Component
public class ContextBuilder {

    private final ToolRegistry toolRegistry;
    private final SkillRegistry skillRegistry;
    private final SkillIndexBuilder skillIndexBuilder;
    private final SkillSelector skillSelector;
    private final SkillTemplateEngine templateEngine;
    private final SystemPromptBuilder systemPromptBuilder;
    private final ContextCompactionService compactionService;

    @Value("${skills.auto-select-enabled:true}")
    private boolean autoSelectEnabled;

    @Value("${agent.max-history-messages:20}")
    private int maxHistoryMessages;

    public ContextBuilder(
            ToolRegistry toolRegistry,
            SkillRegistry skillRegistry,
            SkillIndexBuilder skillIndexBuilder,
            SkillSelector skillSelector,
            SkillTemplateEngine templateEngine,
            SystemPromptBuilder systemPromptBuilder,
            ContextCompactionService compactionService) {
        this.toolRegistry = toolRegistry;
        this.skillRegistry = skillRegistry;
        this.skillIndexBuilder = skillIndexBuilder;
        this.skillSelector = skillSelector;
        this.templateEngine = templateEngine;
        this.systemPromptBuilder = systemPromptBuilder;
        this.compactionService = compactionService;
    }

    /**
     * 构建 LLM 请求
     * @param systemPrompt 系统提示（可选，为空则使用默认）
     * @param history 历史消息
     * @param userPrompt 用户当前输入
     * @return LlmRequest
     */
    public LlmRequest build(String systemPrompt, List<LlmRequest.Message> history, String userPrompt) {
        List<LlmRequest.Message> messages = new ArrayList<>();

        // 1. System prompt
        String system = (systemPrompt != null && !systemPrompt.isBlank())
                ? systemPrompt
                : systemPromptBuilder.build(SystemPromptBuilder.PromptMode.FULL);
        messages.add(LlmRequest.Message.system(system));

        // 2. 历史消息（P2 压缩插槽 -> 滑动窗口）
        if (history != null && !history.isEmpty()) {
            List<LlmRequest.Message> processedHistory = history;
            if (compactionService.shouldCompact(history, estimateTokens(history))) {
                processedHistory = compactionService.compact(history).messages();
                log.info("Context compacted: {} -> {} messages", history.size(), processedHistory.size());
            }
            messages.addAll(applyWindow(processedHistory));
        }

        // 3. 当前用户输入
        messages.add(LlmRequest.Message.user(userPrompt));

        log.debug("Built context: systemPrompt={} chars, history={} msgs, userPrompt={} chars",
                system.length(), history != null ? history.size() : 0, userPrompt.length());

        return LlmRequest.builder()
                .messages(messages)
                .build();
    }

    /**
     * 简化版：只有用户输入
     */
    public LlmRequest build(String userPrompt) {
        return build(null, null, userPrompt);
    }

    /**
     * 带历史的版本
     */
    public LlmRequest buildWithHistory(List<LlmRequest.Message> history, String userPrompt) {
        return build(null, history, userPrompt);
    }

    /**
     * 构建带工具的 LLM 请求
     * @param history 历史消息
     * @param userPrompt 用户当前输入
     * @param enableTools 是否启用工具
     * @return LlmRequest
     */
    public LlmRequest buildWithTools(List<LlmRequest.Message> history, String userPrompt, boolean enableTools) {
        LlmRequest request = build(null, history, userPrompt);

        if (enableTools && toolRegistry.size() > 0) {
            request.setTools(toolRegistry.toOpenAiTools(
                    ToolVisibilityResolver.VisibilityRequest.builder().build()
            ));
            request.setToolChoice("auto");
            log.debug("Enabled {} tools for request", toolRegistry.size());
        }

        return request;
    }

    /**
     * 构建带 Skill 索引的请求
     * 在 system prompt 中注入可用 skill 列表
     *
     * @param history 历史消息
     * @param userPrompt 用户当前输入
     * @param enableTools 是否启用工具
     * @return LlmRequest
     */
    public LlmRequest buildWithSkillIndex(List<LlmRequest.Message> history, String userPrompt, boolean enableTools) {
        return buildWithSkillIndex(history, userPrompt, enableTools, "main");
    }

    public LlmRequest buildWithSkillIndex(List<LlmRequest.Message> history,
                                          String userPrompt,
                                          boolean enableTools,
                                          String agentId) {
        // 使用 SystemPromptBuilder 构建带 skill 索引的完整 system prompt
        // Skills 段落已经包含在 FULL 模式中
        String systemWithSkills = systemPromptBuilder.build(
                SystemPromptBuilder.PromptMode.FULL, null, null, agentId
        );

        LlmRequest request = build(systemWithSkills, history, userPrompt);

        if (enableTools && toolRegistry.size() > 0) {
            request.setTools(toolRegistry.toOpenAiTools(
                    ToolVisibilityResolver.VisibilityRequest.builder()
                            .agentId(agentId)
                            .build()
            ));
            request.setToolChoice("auto");
        }

        log.debug("Built context with skill index: {} available skills",
                skillRegistry.getAvailable(agentId).size());

        return request;
    }

    /**
     * 构建激活 Skill 后的请求
     * 使用 skill 的完整指令替代普通 system prompt
     *
     * @param skill 已加载的 skill
     * @param arguments 用户参数（$ARGUMENTS）
     * @param history 历史消息
     * @param userPrompt 用户当前输入
     * @param enableTools 是否启用工具
     * @return SkillAwareRequest 包含请求和工具限制信息
     */
    public SkillAwareRequest buildWithActiveSkill(
            LoadedSkill skill,
            String arguments,
            List<LlmRequest.Message> history,
            String userPrompt,
            boolean enableTools) {
        return buildWithActiveSkill(skill, arguments, history, userPrompt, enableTools, "main");
    }

    public SkillAwareRequest buildWithActiveSkill(
            LoadedSkill skill,
            String arguments,
            List<LlmRequest.Message> history,
            String userPrompt,
            boolean enableTools,
            String agentId) {

        // 使用模板引擎编译 skill body
        SkillTemplateEngine.TemplateContext context = templateEngine.createContext()
                .withArguments(arguments != null ? arguments : userPrompt)
                .withProjectRoot(System.getProperty("user.dir"))
                .withCwd(System.getProperty("user.dir"));

        // 添加技能基础目录到模板上下文
        if (skill.getBasePath() != null) {
            context.withSkillBasePath(skill.getBasePath().toAbsolutePath().toString());
        }

        String compiledBody = templateEngine.render(skill.getBody(), context);

        // 获取基础 system prompt（SKILL 模式：仅 Identity + Workspace + Runtime，不含工具列表）
        Set<String> allowedTools = skill.getAllowedTools();
        String basePrompt = systemPromptBuilder.build(
                SystemPromptBuilder.PromptMode.SKILL, allowedTools, null, agentId
        );

        // 构建 system prompt
        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append(basePrompt);
        systemPrompt.append("\n\n---\n\n");

        // 添加强制执行指令
        systemPrompt.append("## MANDATORY SKILL EXECUTION\n\n");
        systemPrompt.append("A skill has been activated for this task. You MUST:\n");
        systemPrompt.append("1. **Follow the skill instructions below step by step**\n");
        systemPrompt.append("2. **Use the available tools** to accomplish the task (write files, execute commands)\n");
        systemPrompt.append("3. **Do NOT just describe** what you would do - actually DO it using tools\n");
        systemPrompt.append("4. **Complete the entire workflow** described in the skill\n\n");

        systemPrompt.append("---\n\n");
        systemPrompt.append("## Active Skill: ").append(skill.getName()).append("\n\n");

        systemPrompt.append(compiledBody);

        // 添加工具限制提示
        if (allowedTools != null && !allowedTools.isEmpty()) {
            systemPrompt.append("\n\n---\n");
            systemPrompt.append("**Tool Restriction**: Only use these tools: ");
            systemPrompt.append(String.join(", ", allowedTools));
        }

        // 添加结尾强调
        systemPrompt.append("\n\n---\n\n");
        systemPrompt.append("**REMINDER**: Start executing the skill workflow NOW. Use tools to read files, write code, and complete the task.\n");

        LlmRequest request = build(systemPrompt.toString(), history, userPrompt);

        // 设置工具（可能需要过滤）
        if (enableTools && toolRegistry.size() > 0) {
            ToolVisibilityResolver.VisibilityRequest visibilityRequest = ToolVisibilityResolver.VisibilityRequest.builder()
                    .agentId(agentId)
                    .skillAllowedTools(allowedTools)
                    .build();
            request.setTools(toolRegistry.toOpenAiTools(visibilityRequest));
            request.setToolChoice("auto");
        }

        log.info("Built context with active skill: {} (allowed tools: {}, prompt length: {} chars)",
                skill.getName(),
                allowedTools != null ? allowedTools.size() : "all",
                systemPrompt.length());

        return new SkillAwareRequest(request, skill.getName(), allowedTools, skill.getConfirmBefore(), skill.getBasePath());
    }

    /**
     * 智能构建：自动检测是否为 slash command 并处理
     *
     * @param history 历史消息
     * @param userPrompt 用户当前输入
     * @param enableTools 是否启用工具
     * @return SkillAwareRequest
     */
    public SkillAwareRequest buildSmart(List<LlmRequest.Message> history, String userPrompt, boolean enableTools) {
        return buildSmart(history, userPrompt, enableTools, "main");
    }

    public SkillAwareRequest buildDirectResponse(List<LlmRequest.Message> history,
                                                String userPrompt,
                                                String agentId) {
        String system = systemPromptBuilder.build(SystemPromptBuilder.PromptMode.MINIMAL, null, null, agentId);
        LlmRequest request = build(system, history, userPrompt);
        return new SkillAwareRequest(request, null, null, null, null);
    }

    public SkillAwareRequest buildReactEntry(List<LlmRequest.Message> history,
                                             String userPrompt,
                                             boolean enableTools,
                                             String agentId) {
        return buildSmart(history, userPrompt, enableTools, agentId);
    }

    public SkillAwareRequest buildForPolicyDecision(List<LlmRequest.Message> history,
                                                     String userPrompt,
                                                     boolean enableTools,
                                                     TaskComplexity complexity,
                                                     String agentId) {
        if (complexity == TaskComplexity.DIRECT) {
            return buildDirectResponse(history, userPrompt, agentId);
        }
        return buildReactEntry(history, userPrompt, enableTools, agentId);
    }

    public SkillAwareRequest buildSmart(List<LlmRequest.Message> history,
                                        String userPrompt,
                                        boolean enableTools,
                                        String agentId) {
        // 1. 尝试手动 skill 选择
        SkillSelection selection = skillSelector.tryManualSelection(userPrompt, agentId);

        if (selection.isSelected()) {
            // 激活 skill
            Optional<LoadedSkill> loaded = skillRegistry.activate(selection.getSkillName(), agentId);
            if (loaded.isPresent()) {
                return buildWithActiveSkill(
                        loaded.get(),
                        selection.getArguments(),
                        history,
                        selection.getArguments() != null ? selection.getArguments() : userPrompt,
                        enableTools,
                        agentId
                );
            }
        }

        // 2. 如果启用自动选择，注入 skill 索引
        if (autoSelectEnabled && !skillRegistry.getAvailable(agentId).isEmpty()) {
            LlmRequest request = buildWithSkillIndex(history, userPrompt, enableTools, agentId);
            return new SkillAwareRequest(request, null, null, null, null);
        }

        // 3. 普通请求
        LlmRequest request = buildWithTools(history, userPrompt, enableTools);
        return new SkillAwareRequest(request, null, null, null, null);
    }

    /**
     * 处理 LLM 回复中的 [USE_SKILL:xxx]
     * 如果检测到，返回新的请求用于重新调用 LLM
     *
     * @param llmResponse LLM 的回复
     * @param originalInput 原始用户输入
     * @param history 历史消息
     * @param enableTools 是否启用工具
     * @return 如果需要重新调用返回新请求，否则返回 empty
     */
    public Optional<SkillAwareRequest> handleAutoSkillSelection(
            String llmResponse,
            String originalInput,
            List<LlmRequest.Message> history,
            boolean enableTools) {
        return handleAutoSkillSelection(llmResponse, originalInput, history, enableTools, "main");
    }

    public Optional<SkillAwareRequest> handleAutoSkillSelection(
            String llmResponse,
            String originalInput,
            List<LlmRequest.Message> history,
            boolean enableTools,
            String agentId) {

        SkillSelection selection = skillSelector.parseFromLlmResponse(llmResponse, originalInput, agentId);

        if (!selection.isSelected()) {
            return Optional.empty();
        }

        Optional<LoadedSkill> loaded = skillRegistry.activate(selection.getSkillName(), agentId);
        if (loaded.isEmpty()) {
            return Optional.empty();
        }

        log.info("Auto skill activation from LLM: {}", selection.getSkillName());

        return Optional.of(buildWithActiveSkill(
                loaded.get(),
                selection.getArguments(),
                history,
                originalInput,
                enableTools,
                agentId
        ));
    }

    /**
     * 通过 skill 名称直接激活 skill（use_skill 工具调用路径）
     *
     * @param skillName     要激活的 skill 名称
     * @param originalInput 原始用户输入（作为 $ARGUMENTS）
     * @param history       历史消息
     * @param enableTools   是否启用工具
     * @return 如果激活成功返回新请求，否则返回 empty
     */
    public Optional<SkillAwareRequest> handleSkillActivationByName(
            String skillName,
            String originalInput,
            List<LlmRequest.Message> history,
            boolean enableTools) {
        return handleSkillActivationByName(skillName, originalInput, history, enableTools, "main");
    }

    public Optional<SkillAwareRequest> handleSkillActivationByName(
            String skillName,
            String originalInput,
            List<LlmRequest.Message> history,
            boolean enableTools,
            String agentId) {

        if (skillName == null || skillName.isBlank()) {
            return Optional.empty();
        }

        if (!skillRegistry.isAvailable(skillName, agentId)) {
            log.warn("Skill activation by name failed: {} not available", skillName);
            return Optional.empty();
        }

        Optional<LoadedSkill> loaded = skillRegistry.activate(skillName, agentId);
        if (loaded.isEmpty()) {
            return Optional.empty();
        }

        log.info("Skill activation via use_skill tool: {}", skillName);

        return Optional.of(buildWithActiveSkill(
                loaded.get(),
                originalInput,  // 原始输入作为参数
                history,
                originalInput,
                enableTools,
                agentId
        ));
    }

    /**
     * 构建消息列表（不包含 LlmRequest 包装，供 AgentRuntime 使用）
     * @param history 历史消息
     * @param userPrompt 用户当前输入
     * @return 消息列表（可变）
     */
    public List<LlmRequest.Message> buildMessages(List<LlmRequest.Message> history, String userPrompt) {
        return buildMessages(history, userPrompt, null, "main");
    }

    /**
     * 构建消息列表（支持排除 MCP 服务器）
     * @param history 历史消息
     * @param userPrompt 用户当前输入
     * @param excludedMcpServers 要排除的 MCP 服务器名称集合
     * @return 消息列表（可变）
     */
    public List<LlmRequest.Message> buildMessages(List<LlmRequest.Message> history, String userPrompt,
                                                    Set<String> excludedMcpServers) {
        return buildMessages(history, userPrompt, excludedMcpServers, "main");
    }

    /**
     * 构建消息列表（支持排除 MCP 服务器和 agentId）
     * @param history 历史消息
     * @param userPrompt 用户当前输入
     * @param excludedMcpServers 要排除的 MCP 服务器名称集合
     * @param agentId agent 作用域标识
     * @return 消息列表（可变）
     */
    public List<LlmRequest.Message> buildMessages(List<LlmRequest.Message> history, String userPrompt,
                                                  Set<String> excludedMcpServers, String agentId) {
        List<LlmRequest.Message> messages = new ArrayList<>();

        // 1. System prompt - 使用结构化的完整提示
        String systemPrompt = systemPromptBuilder.build(
                SystemPromptBuilder.PromptMode.FULL, null, excludedMcpServers, agentId
        );
        messages.add(LlmRequest.Message.system(systemPrompt));

        // 2. 历史消息（P2 压缩插槽 -> 滑动窗口）
        if (history != null && !history.isEmpty()) {
            List<LlmRequest.Message> processedHistory = history;
            if (compactionService.shouldCompact(history, estimateTokens(history))) {
                processedHistory = compactionService.compact(history).messages();
                log.info("Context compacted: {} -> {} messages", history.size(), processedHistory.size());
            }
            messages.addAll(applyWindow(processedHistory));
        }

        // 3. 当前用户输入
        messages.add(LlmRequest.Message.user(userPrompt));

        return messages;
    }

    /**
     * 粗略估算 token 数（1 token ≈ 4 chars，仅用于压缩阈值判断）
     */
    private int estimateTokens(List<LlmRequest.Message> messages) {
        return messages.stream()
                .mapToInt(m -> m.getContent() != null ? m.getContent().length() / 4 : 10)
                .sum();
    }

    /**
     * 滑动窗口截断历史消息，保留最近 maxHistoryMessages 条。
     * 确保不以孤立 tool 消息开头。
     */
    private List<LlmRequest.Message> applyWindow(List<LlmRequest.Message> history) {
        if (history == null || history.isEmpty()) {
            return history;
        }
        if (maxHistoryMessages <= 0) {
            return List.of();
        }
        if (history.size() <= maxHistoryMessages) {
            return history;
        }

        List<LlmRequest.Message> windowed = history.subList(
                history.size() - maxHistoryMessages, history.size()
        );
        int start = 0;
        while (start < windowed.size() && "tool".equals(windowed.get(start).getRole())) {
            start++;
        }
        return start == 0 ? windowed : windowed.subList(start, windowed.size());
    }

    /**
     * 带 Skill 信息的请求包装
     */
    public record SkillAwareRequest(
            LlmRequest request,
            String activeSkillName,
            Set<String> allowedTools,
            Set<String> confirmBefore,
            Path skillBasePath
    ) {
        /**
         * 是否有激活的 skill
         */
        public boolean hasActiveSkill() {
            return activeSkillName != null;
        }

        /**
         * 检查工具是否被允许
         */
        public boolean isToolAllowed(String toolName) {
            if (allowedTools == null || allowedTools.isEmpty()) {
                return true; // 无限制
            }
            return allowedTools.contains(toolName);
        }

        /**
         * 检查工具是否需要确认
         */
        public boolean requiresConfirmation(String toolName) {
            if (confirmBefore == null || confirmBefore.isEmpty()) {
                return false;
            }
            return confirmBefore.contains(toolName);
        }
    }
}
