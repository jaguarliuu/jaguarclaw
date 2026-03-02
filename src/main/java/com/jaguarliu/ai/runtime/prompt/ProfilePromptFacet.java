package com.jaguarliu.ai.runtime.prompt;

import com.jaguarliu.ai.runtime.SystemPromptBuilder;
import com.jaguarliu.ai.soul.SoulConfigService;
import lombok.extern.slf4j.Slf4j;

/**
 * Profile Facet — injects PROFILE.md content into the system prompt.
 */
@Slf4j
public class ProfilePromptFacet implements PromptFacet {

    private final SoulConfigService soulConfigService;

    public ProfilePromptFacet(SoulConfigService soulConfigService) {
        this.soulConfigService = soulConfigService;
    }

    @Override
    public String key() {
        return "PROFILE";
    }

    @Override
    public boolean supports(PromptAssemblyContext context) {
        return context.getMode() == SystemPromptBuilder.PromptMode.FULL;
    }

    @Override
    public String render(PromptAssemblyContext context) {
        try {
            String md = soulConfigService.readProfileMd(context.getAgentId());
            return md.isBlank() ? "" : md.trim() + "\n\n";
        } catch (Exception e) {
            log.warn("Failed to build profile prompt facet", e);
            return "";
        }
    }
}
