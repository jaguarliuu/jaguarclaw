package com.jaguarliu.ai.runtime.prompt;

import com.jaguarliu.ai.runtime.SystemPromptBuilder;
import com.jaguarliu.ai.soul.SoulConfigService;
import lombok.extern.slf4j.Slf4j;

/**
 * Rule Facet — injects RULE.md content into the system prompt.
 */
@Slf4j
public class RulePromptFacet implements PromptFacet {

    private final SoulConfigService soulConfigService;

    public RulePromptFacet(SoulConfigService soulConfigService) {
        this.soulConfigService = soulConfigService;
    }

    @Override
    public String key() {
        return "RULE";
    }

    @Override
    public boolean supports(PromptAssemblyContext context) {
        return context.getMode() == SystemPromptBuilder.PromptMode.FULL;
    }

    @Override
    public String render(PromptAssemblyContext context) {
        try {
            String md = soulConfigService.readRuleMd(context.getAgentId());
            return md.isBlank() ? "" : md.trim() + "\n\n";
        } catch (Exception e) {
            log.warn("Failed to build rule prompt facet", e);
            return "";
        }
    }
}
