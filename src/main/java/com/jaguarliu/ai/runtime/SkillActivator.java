package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.gateway.events.EventBus;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.llm.model.ToolCall;
import com.jaguarliu.ai.skills.selector.SkillSelector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Skill 激活器
 * 负责检测和激活 Skill
 */
@Component
@RequiredArgsConstructor
public class SkillActivator {

    private final SkillSelector skillSelector;
    private final ContextBuilder contextBuilder;
    private final EventBus eventBus;

    /**
     * 检测自动 Skill 激活。
     */
    public Optional<SkillActivation> detectAutoActivation(
            String llmResponse,
            RunContext context
    ) {
        throw new UnsupportedOperationException();
    }

    /**
     * 检测 use_skill 工具激活。
     */
    public Optional<SkillActivation> detectToolActivation(
            List<ToolCall> toolCalls,
            RunContext context
    ) {
        throw new UnsupportedOperationException();
    }

    /**
     * 应用 Skill 激活。
     */
    public Optional<ContextBuilder.SkillAwareRequest> applyActivation(
            SkillActivation activation,
            List<LlmRequest.Message> history,
            String originalInput
    ) {
        throw new UnsupportedOperationException();
    }

    public record SkillActivation(String skillName, String triggerType) {}
}
