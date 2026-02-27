package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.gateway.events.AgentEvent;
import com.jaguarliu.ai.gateway.events.EventBus;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.llm.model.ToolCall;
import com.jaguarliu.ai.runtime.ContextBuilder;
import com.jaguarliu.ai.skills.selector.SkillSelection;
import com.jaguarliu.ai.skills.selector.SkillSelector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Skill 激活器
 * 负责检测和激活 Skill
 *
 * 从 AgentRuntime 迁移的逻辑：
 * - detectAutoSkillActivation() -> detectAutoActivation()
 * - detectUseSkillActivation() -> detectToolActivation()
 * - handleSkillActivationByName() -> applyActivation()
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillActivator {

    private final SkillSelector skillSelector;
    private final ContextBuilder contextBuilder;
    private final EventBus eventBus;

    /**
     * 检测自动 Skill 激活
     * 从 LLM 响应中解析 [USE_SKILL:xxx] 标记
     *
     * @param llmResponse LLM 响应文本
     * @param context     运行上下文
     * @return 激活结果，如果没有检测到返回 empty
     */
    public Optional<SkillActivation> detectAutoActivation(
            String llmResponse,
            RunContext context
    ) {
        if (llmResponse == null || llmResponse.isBlank()) {
            return Optional.empty();
        }

        // 使用 SkillSelector 解析
        SkillSelection selection = skillSelector.parseFromLlmResponse(
                llmResponse, context.getOriginalInput());

        if (!selection.isSelected()) {
            return Optional.empty();
        }

        String skillName = selection.getSkillName();

        // 检查激活限制（每个 skill 最多激活 3 次）
        if (context.isSkillActivationLimitReached(skillName)) {
            log.info("Skill activation limit reached: skill={}, runId={}",
                    skillName, context.getRunId());
            return Optional.empty();
        }

        log.info("Detected auto skill activation: skill={}, runId={}",
                skillName, context.getRunId());

        return Optional.of(new SkillActivation(
                skillName,
                "auto",
                selection.getArguments() != null ? Map.of("args", selection.getArguments()) : null
        ));
    }

    /**
     * 检测 use_skill 工具激活
     * 从 tool_calls 中提取 use_skill 调用
     *
     * @param toolCalls 工具调用列表
     * @param context   运行上下文
     * @return 激活结果，如果没有检测到返回 empty
     */
    public Optional<SkillActivation> detectToolActivation(
            List<ToolCall> toolCalls,
            RunContext context
    ) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return Optional.empty();
        }

        for (ToolCall toolCall : toolCalls) {
            if (!"use_skill".equals(toolCall.getName())) {
                continue;
            }

            Map<String, Object> args = parseArguments(toolCall.getArguments());
            String skillName = (String) args.get("skill_name");

            if (skillName == null || skillName.isBlank()) {
                continue;
            }

            skillName = skillName.trim();

            // 检查激活限制
            if (context.isSkillActivationLimitReached(skillName)) {
                log.info("Skill activation limit reached: skill={}, runId={}",
                        skillName, context.getRunId());
                continue;
            }

            log.info("Detected tool-based skill activation: skill={}, runId={}",
                    skillName, context.getRunId());

            return Optional.of(new SkillActivation(
                    skillName,
                    "tool",
                    null
            ));
        }

        return Optional.empty();
    }

    /**
     * 应用 Skill 激活
     * 构建 skill 上下文并返回新的消息列表
     *
     * @param activation   激活信息
     * @param history      历史消息（不含 use_skill 调用）
     * @param originalInput 原始用户输入
     * @return Skill 感知请求，包含新消息和允许工具列表
     */
    public Optional<SkillAwareRequest> applyActivation(
            SkillActivation activation,
            List<LlmRequest.Message> history,
            String originalInput
    ) {
        String skillName = activation.skillName();

        log.info("Applying skill activation: skill={}, trigger={}",
                skillName, activation.triggerType());

        try {
            Optional<ContextBuilder.SkillAwareRequest> skillRequest =
                    contextBuilder.handleSkillActivationByName(
                            skillName,
                            originalInput,
                            history,
                            "auto".equals(activation.triggerType()));

            if (skillRequest.isEmpty()) {
                log.warn("Failed to activate skill: skill={}", skillName);
                return Optional.empty();
            }

            ContextBuilder.SkillAwareRequest request = skillRequest.get();

            log.info("Skill activated successfully: skill={}, allowedTools={}",
                    skillName, request.allowedTools().size());

            return Optional.of(new SkillAwareRequest(
                    request.request().getMessages(),
                    request.allowedTools(),
                    request.skillBasePath() != null ? request.skillBasePath().toString() : null,
                    request.hasActiveSkill()
            ));

        } catch (Exception e) {
            log.error("Error activating skill: skill={}", skillName, e);
            return Optional.empty();
        }
    }

    /**
     * 发布 skill 激活事件
     */
    public void publishActivationEvent(RunContext context, SkillActivation activation) {
        eventBus.publish(AgentEvent.skillActivated(
                context.getConnectionId(),
                context.getRunId(),
                activation.skillName(),
                activation.triggerType()
        ));
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
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(argumentsJson, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse tool arguments: {}", argumentsJson, e);
            return Map.of();
        }
    }

    /**
     * Skill 激活信息
     */
    public record SkillActivation(
            String skillName,
            String triggerType,  // "auto" 或 "tool"
            Map<String, Object> arguments
    ) {
        public boolean isAutoActivation() {
            return "auto".equals(triggerType);
        }

        public boolean isToolActivation() {
            return "tool".equals(triggerType);
        }
    }

    /**
     * Skill 感知请求
     */
    public record SkillAwareRequest(
            List<LlmRequest.Message> messages,
            java.util.Set<String> allowedTools,
            String skillBasePath,
            boolean hasActiveSkill
    ) {
    }
}
