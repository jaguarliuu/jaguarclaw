package com.jaguarliu.ai.runtime.prompt;

import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolCatalogEntry;
import com.jaguarliu.ai.tools.ToolCatalogGroup;
import com.jaguarliu.ai.tools.ToolRegistry;
import com.jaguarliu.ai.tools.ToolVisibilityResolver;

import java.util.List;

/**
 * Tool Facet（Agent 作用域）
 */
public class ToolPromptFacet implements PromptFacet {

    private final ToolRegistry toolRegistry;

    public ToolPromptFacet(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @Override
    public String key() {
        return "TOOLS";
    }

    @Override
    public boolean supports(PromptAssemblyContext context) {
        return context.getMode() != com.jaguarliu.ai.runtime.SystemPromptBuilder.PromptMode.SKILL
                && context.getMode() != com.jaguarliu.ai.runtime.SystemPromptBuilder.PromptMode.NONE;
    }

    @Override
    public String render(PromptAssemblyContext context) {
        ToolVisibilityResolver.VisibilityRequest visibilityRequest = ToolVisibilityResolver.VisibilityRequest.builder()
                .agentId(context.getAgentId())
                .strategyAllowedTools(context.getAllowedTools())
                .excludedMcpServers(context.getExcludedMcpServers())
                .build();
        List<ToolDefinition> tools = toolRegistry.listDefinitions(visibilityRequest);
        if (tools == null || tools.isEmpty()) {
            return "";
        }
        List<ToolCatalogGroup> groups = toolRegistry.listCatalogGroups(visibilityRequest);

        StringBuilder sb = new StringBuilder();
        sb.append("## Available Tools\n\n");
        sb.append("You can use the following tools to help complete tasks:\n\n");

        if (groups != null && !groups.isEmpty()) {
            for (ToolCatalogGroup group : groups) {
                sb.append("### ").append(group.label()).append("\n");
                for (ToolCatalogEntry tool : group.tools()) {
                    sb.append(String.format("- **%s**: %s", tool.name(), tool.description()));
                    if (tool.hitl()) {
                        sb.append(" _(requires confirmation)_");
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
        } else {
            // 回退兼容：当目录分组不可用时，保持旧版平铺输出
            for (ToolDefinition tool : tools) {
                sb.append(String.format("- **%s**: %s", tool.getName(), tool.getDescription()));
                if (tool.isHitl()) {
                    sb.append(" _(requires confirmation)_");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        boolean hasScriptableTool = tools.stream().anyMatch(t ->
                "shell".equals(t.getName()) || "remote_exec".equals(t.getName()));
        if (hasScriptableTool) {
            sb.append("""

                    ### Script-First Execution Rule
                    - For multi-step operations (inspection, loops, retries, batch checks), write a script first and execute it.
                    - Prefer `write_file` + `script_path` for shell/remote_exec over long inline command chains.
                    - Use direct `command` only for short, one-off checks.

                    """);
        }

        sb.append("\nUse tools when they help accomplish the task. Always explain what you're doing.\n\n");
        return sb.toString();
    }
}
