package com.jaguarliu.ai.runtime.prompt;

import com.jaguarliu.ai.runtime.SystemPromptBuilder;

import java.util.Set;

/**
 * Prompt 组装上下文
 * 统一携带 mode 与作用域参数，供各个 Facet 构建片段。
 */
public class PromptAssemblyContext {

    private final SystemPromptBuilder.PromptMode mode;
    private final Set<String> allowedTools;
    private final Set<String> excludedMcpServers;
    private final String dataSourceId;
    private final String agentId;

    public PromptAssemblyContext(SystemPromptBuilder.PromptMode mode,
                                 Set<String> allowedTools,
                                 Set<String> excludedMcpServers,
                                 String dataSourceId,
                                 String agentId) {
        this.mode = mode;
        this.allowedTools = allowedTools;
        this.excludedMcpServers = excludedMcpServers;
        this.dataSourceId = dataSourceId;
        this.agentId = (agentId == null || agentId.isBlank()) ? "main" : agentId;
    }

    public SystemPromptBuilder.PromptMode getMode() {
        return mode;
    }

    public Set<String> getAllowedTools() {
        return allowedTools;
    }

    public Set<String> getExcludedMcpServers() {
        return excludedMcpServers;
    }

    public String getDataSourceId() {
        return dataSourceId;
    }

    public String getAgentId() {
        return agentId;
    }
}
