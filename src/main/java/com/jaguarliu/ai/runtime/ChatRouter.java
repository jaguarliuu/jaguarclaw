package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.agents.AgentConstants;
import com.jaguarliu.ai.agents.entity.AgentProfileEntity;
import com.jaguarliu.ai.agents.service.AgentProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析用户输入中的路由信号（如 @agent mention），并输出最终执行 Agent 与用户提示词。
 */
@Component
@RequiredArgsConstructor
public class ChatRouter {

    private static final String FALLBACK_AGENT_ID = AgentConstants.DEFAULT_AGENT_ID;
    private static final Pattern LEADING_MENTION =
            Pattern.compile("^\\s*@([a-zA-Z0-9_-]{1,64})\\s+(.+)$", Pattern.DOTALL);

    private final AgentProfileService agentProfileService;

    public RouteDecision route(String prompt, String requestedAgentId, String sessionAgentId) {
        String originalPrompt = prompt == null ? "" : prompt;
        Mention mention = extractLeadingMention(originalPrompt);

        if (mention != null) {
            if (isEnabled(mention.agentId())) {
                return new RouteDecision(mention.agentId(), mention.cleanedPrompt(), mention.agentId(), true);
            }
            return new RouteDecision(defaultAgentId(), mention.cleanedPrompt(), mention.agentId(), false);
        }

        return new RouteDecision(
                resolveFallbackAgentId(requestedAgentId, sessionAgentId),
                originalPrompt,
                null,
                false
        );
    }

    private Mention extractLeadingMention(String prompt) {
        Matcher matcher = LEADING_MENTION.matcher(prompt);
        if (!matcher.matches()) {
            return null;
        }
        String agentId = matcher.group(1);
        String cleanedPrompt = matcher.group(2) != null ? matcher.group(2).trim() : "";
        return new Mention(agentId, cleanedPrompt);
    }

    private boolean isEnabled(String agentId) {
        return agentProfileService.get(agentId)
                .map(AgentProfileEntity::getEnabled)
                .orElse(false);
    }

    private String resolveFallbackAgentId(String requestedAgentId, String sessionAgentId) {
        if (requestedAgentId != null && !requestedAgentId.isBlank()) {
            return requestedAgentId;
        }
        if (sessionAgentId != null && !sessionAgentId.isBlank()) {
            return sessionAgentId;
        }
        return defaultAgentId();
    }

    private String defaultAgentId() {
        return agentProfileService.list().stream()
                .filter(agent -> Boolean.TRUE.equals(agent.getIsDefault()))
                .map(AgentProfileEntity::getId)
                .findFirst()
                .orElse(FALLBACK_AGENT_ID);
    }

    public record RouteDecision(
            String agentId,
            String prompt,
            String mentionedAgentId,
            boolean mentionResolved
    ) {
    }

    private record Mention(String agentId, String cleanedPrompt) {
    }
}
