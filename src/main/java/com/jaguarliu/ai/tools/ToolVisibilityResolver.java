package com.jaguarliu.ai.tools;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 工具可见性解析器。
 *
 * 目标：
 * 1. Builtin 默认全局可见；
 * 2. MCP/Skill 支持 global/agent 作用域判定；
 * 3. 统一收敛过滤维度：AgentPolicy ∩ Strategy ∩ Skill。
 */
public final class ToolVisibilityResolver {

    private ToolVisibilityResolver() {
    }

    public enum ToolDomain {
        BUILTIN,
        MCP,
        SKILL
    }

    public enum ToolScope {
        GLOBAL,
        AGENT
    }

    /**
     * 可选元数据扩展点，供 MCP/Skill 工具声明作用域。
     * 未实现该接口时走默认推断：
     * - `mcpServerName != null` => MCP + GLOBAL
     * - 其他 => BUILTIN + GLOBAL
     */
    public interface ScopedToolMetadataProvider {
        ToolDomain getToolDomain();
        ToolScope getToolScope();
        String getScopedAgentId();
    }

    public record VisibilityRequest(
            String agentId,
            Set<String> agentAllowedTools,
            Set<String> agentDeniedTools,
            Set<String> strategyAllowedTools,
            Set<String> skillAllowedTools,
            Set<String> excludedMcpServers
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private String agentId;
            private Set<String> agentAllowedTools;
            private Set<String> agentDeniedTools;
            private Set<String> strategyAllowedTools;
            private Set<String> skillAllowedTools;
            private Set<String> excludedMcpServers;

            public Builder agentId(String agentId) {
                this.agentId = agentId;
                return this;
            }

            public Builder agentAllowedTools(Set<String> agentAllowedTools) {
                this.agentAllowedTools = agentAllowedTools;
                return this;
            }

            public Builder agentDeniedTools(Set<String> agentDeniedTools) {
                this.agentDeniedTools = agentDeniedTools;
                return this;
            }

            public Builder strategyAllowedTools(Set<String> strategyAllowedTools) {
                this.strategyAllowedTools = strategyAllowedTools;
                return this;
            }

            public Builder skillAllowedTools(Set<String> skillAllowedTools) {
                this.skillAllowedTools = skillAllowedTools;
                return this;
            }

            public Builder excludedMcpServers(Set<String> excludedMcpServers) {
                this.excludedMcpServers = excludedMcpServers;
                return this;
            }

            public VisibilityRequest build() {
                return new VisibilityRequest(
                        agentId,
                        agentAllowedTools,
                        agentDeniedTools,
                        strategyAllowedTools,
                        skillAllowedTools,
                        excludedMcpServers
                );
            }
        }
    }

    public record VisibilityResult(List<Tool> tools, Set<String> toolNames) {
    }

    public static VisibilityResult resolve(List<Tool> tools, VisibilityRequest request) {
        if (tools == null || tools.isEmpty()) {
            return new VisibilityResult(List.of(), Set.of());
        }

        VisibilityRequest req = request != null ? request : VisibilityRequest.builder().build();
        String currentAgentId = normalizeAgentId(req.agentId());

        List<Tool> visibleTools = new ArrayList<>();
        Set<String> visibleNames = new LinkedHashSet<>();

        for (Tool tool : tools) {
            if (tool == null || !tool.isEnabled()) {
                continue;
            }

            if (!passesMcpServerFilter(tool, req.excludedMcpServers())) {
                continue;
            }

            ToolMetadata metadata = resolveMetadata(tool);
            if (!isScopeVisible(metadata, currentAgentId)) {
                continue;
            }

            String toolName = tool.getName();
            if (!passesAllowFilter(toolName, req.agentAllowedTools())) {
                continue;
            }
            if (!passesAllowFilter(toolName, req.strategyAllowedTools())) {
                continue;
            }
            if (!passesAllowFilter(toolName, req.skillAllowedTools())) {
                continue;
            }
            if (contains(req.agentDeniedTools(), toolName)) {
                continue;
            }

            visibleTools.add(tool);
            visibleNames.add(toolName);
        }

        return new VisibilityResult(List.copyOf(visibleTools), Set.copyOf(visibleNames));
    }

    private static boolean passesMcpServerFilter(Tool tool, Set<String> excludedMcpServers) {
        if (excludedMcpServers == null || excludedMcpServers.isEmpty()) {
            return true;
        }
        String serverName = tool.getMcpServerName();
        return serverName == null || !excludedMcpServers.contains(serverName);
    }

    private static boolean passesAllowFilter(String toolName, Set<String> allowSet) {
        return allowSet == null || allowSet.isEmpty() || allowSet.contains(toolName);
    }

    private static boolean contains(Set<String> set, String value) {
        return set != null && !set.isEmpty() && set.contains(value);
    }

    private static boolean isScopeVisible(ToolMetadata metadata, String currentAgentId) {
        if (metadata.scope() == ToolScope.GLOBAL) {
            return true;
        }
        String scopedAgentId = normalizeAgentId(metadata.scopedAgentId());
        return Objects.equals(scopedAgentId, currentAgentId);
    }

    private static String normalizeAgentId(String agentId) {
        return (agentId == null || agentId.isBlank()) ? "main" : agentId;
    }

    private static ToolMetadata resolveMetadata(Tool tool) {
        ToolDomain inferredDomain = inferDomain(tool);
        ToolScope inferredScope = ToolScope.GLOBAL;
        String scopedAgentId = null;

        if (tool instanceof ScopedToolMetadataProvider provider) {
            ToolDomain domain = provider.getToolDomain();
            ToolScope scope = provider.getToolScope();
            inferredDomain = domain != null ? domain : inferredDomain;
            inferredScope = scope != null ? scope : inferredScope;
            scopedAgentId = provider.getScopedAgentId();
        }

        return new ToolMetadata(inferredDomain, inferredScope, scopedAgentId);
    }

    private static ToolDomain inferDomain(Tool tool) {
        if (tool.getMcpServerName() != null) {
            return ToolDomain.MCP;
        }
        return ToolDomain.BUILTIN;
    }

    private record ToolMetadata(ToolDomain domain, ToolScope scope, String scopedAgentId) {
    }
}
