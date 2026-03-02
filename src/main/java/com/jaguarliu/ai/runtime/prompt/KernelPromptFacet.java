package com.jaguarliu.ai.runtime.prompt;

import com.jaguarliu.ai.runtime.SystemPromptBuilder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Kernel Prompt Facet
 * 负责维护按 mode 缓存的模板骨架，并将各 Facet 片段填充到模板。
 */
public class KernelPromptFacet {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{[A-Z_]+}}");

    private final Map<SystemPromptBuilder.PromptMode, String> templateCache = new ConcurrentHashMap<>();
    private final Map<SystemPromptBuilder.PromptMode, AtomicInteger> templateBuildCount = new ConcurrentHashMap<>();

    public String assemble(PromptAssemblyContext context, Map<String, String> blocks) {
        String template = templateFor(context.getMode());
        String rendered = template;
        for (Map.Entry<String, String> entry : blocks.entrySet()) {
            String token = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue() : "";
            rendered = rendered.replace(token, value);
        }
        rendered = PLACEHOLDER_PATTERN.matcher(rendered).replaceAll("");

        // 简单规范化空行，避免模板替换后的多余间距
        while (rendered.contains("\n\n\n")) {
            rendered = rendered.replace("\n\n\n", "\n\n");
        }
        return rendered;
    }

    public int getTemplateBuildCount(SystemPromptBuilder.PromptMode mode) {
        AtomicInteger counter = templateBuildCount.get(mode);
        return counter == null ? 0 : counter.get();
    }

    private String templateFor(SystemPromptBuilder.PromptMode mode) {
        return templateCache.computeIfAbsent(mode, this::compileTemplate);
    }

    private String compileTemplate(SystemPromptBuilder.PromptMode mode) {
        templateBuildCount.computeIfAbsent(mode, ignored -> new AtomicInteger()).incrementAndGet();
        return switch (mode) {
            case FULL -> """
                    {{IDENTITY}}

                    {{SOUL}}{{RULE}}{{PROFILE}}{{TOOLS}}{{SAFETY}}{{PLANNING}}{{SUBAGENT}}{{MEMORY}}{{HEARTBEAT}}{{SKILLS}}{{WORKSPACE}}{{DATASOURCE}}{{DATETIME}}{{RUNTIME}}{{MCP}}{{CUSTOM}}
                    """;
            case MINIMAL -> """
                    {{IDENTITY}}
                    
                    {{TOOLS}}{{WORKSPACE}}{{RUNTIME}}
                    """;
            case SKILL -> """
                    {{IDENTITY}}
                    
                    {{WORKSPACE}}{{RUNTIME}}
                    """;
            case NONE -> "{{IDENTITY}}";
        };
    }
}
