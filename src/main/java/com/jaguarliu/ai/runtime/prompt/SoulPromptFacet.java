package com.jaguarliu.ai.runtime.prompt;

import com.jaguarliu.ai.runtime.SystemPromptBuilder;
import com.jaguarliu.ai.soul.SoulConfigService;
import lombok.extern.slf4j.Slf4j;

/**
 * Soul Facet（Agent 作用域）
 */
@Slf4j
public class SoulPromptFacet implements PromptFacet {

    private final SoulConfigService soulConfigService;

    public SoulPromptFacet(SoulConfigService soulConfigService) {
        this.soulConfigService = soulConfigService;
    }

    @Override
    public String key() {
        return "SOUL";
    }

    @Override
    public boolean supports(PromptAssemblyContext context) {
        return context.getMode() == SystemPromptBuilder.PromptMode.FULL;
    }

    @Override
    public String render(PromptAssemblyContext context) {
        try {
            String soulPrompt = soulConfigService.generateSystemPrompt(context.getAgentId());
            if (soulPrompt != null && !soulPrompt.isBlank()) {
                return soulPrompt.trim() + "\n\n";
            }
        } catch (Exception e) {
            log.warn("Failed to build soul prompt facet", e);
        }
        return "";
    }
}
