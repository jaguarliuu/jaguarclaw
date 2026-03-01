package com.jaguarliu.ai.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Tool Visibility Resolver Tests")
class ToolVisibilityResolverTest {

    @Test
    @DisplayName("Builtin tools are globally visible by default")
    void builtinToolsShouldBeVisibleByDefault() {
        List<Tool> tools = List.of(
                simpleTool("read_file"),
                mcpGlobalTool("mcp_fs_read", "filesystem")
        );

        ToolVisibilityResolver.VisibilityResult result = ToolVisibilityResolver.resolve(
                tools,
                ToolVisibilityResolver.VisibilityRequest.builder()
                        .agentId("agent-a")
                        .build()
        );

        assertTrue(result.toolNames().contains("read_file"));
    }

    @Test
    @DisplayName("MCP and Skill tools should obey global/agent scope visibility")
    void scopedToolsShouldObeyScopeRules() {
        List<Tool> tools = List.of(
                mcpGlobalTool("mcp_global", "global-server"),
                mcpAgentTool("mcp_agent_a", "agent-server-a", "agent-a"),
                mcpAgentTool("mcp_agent_b", "agent-server-b", "agent-b"),
                skillGlobalTool("skill_global"),
                skillAgentTool("skill_agent_a", "agent-a"),
                skillAgentTool("skill_agent_b", "agent-b")
        );

        ToolVisibilityResolver.VisibilityResult result = ToolVisibilityResolver.resolve(
                tools,
                ToolVisibilityResolver.VisibilityRequest.builder()
                        .agentId("agent-a")
                        .build()
        );

        Set<String> names = result.toolNames();
        assertTrue(names.contains("mcp_global"));
        assertTrue(names.contains("mcp_agent_a"));
        assertTrue(names.contains("skill_global"));
        assertTrue(names.contains("skill_agent_a"));
        assertTrue(!names.contains("mcp_agent_b"));
        assertTrue(!names.contains("skill_agent_b"));
    }

    @Test
    @DisplayName("Final tool set should obey AgentPolicy ∩ Strategy ∩ Skill")
    void shouldIntersectAgentPolicyStrategyAndSkill() {
        List<Tool> tools = List.of(
                simpleTool("read_file"),
                simpleTool("write_file"),
                simpleTool("shell")
        );

        ToolVisibilityResolver.VisibilityResult result = ToolVisibilityResolver.resolve(
                tools,
                ToolVisibilityResolver.VisibilityRequest.builder()
                        .agentId("agent-a")
                        .agentAllowedTools(Set.of("read_file", "write_file"))
                        .agentDeniedTools(Set.of("write_file"))
                        .strategyAllowedTools(Set.of("read_file", "shell"))
                        .skillAllowedTools(Set.of("read_file", "write_file"))
                        .build()
        );

        Set<String> names = result.tools().stream()
                .map(Tool::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("read_file"), names);
    }

    private Tool simpleTool(String name) {
        return new FakeTool(name, null, ToolVisibilityResolver.ToolDomain.BUILTIN,
                ToolVisibilityResolver.ToolScope.GLOBAL, null);
    }

    private Tool mcpGlobalTool(String name, String serverName) {
        return new FakeTool(name, serverName, ToolVisibilityResolver.ToolDomain.MCP,
                ToolVisibilityResolver.ToolScope.GLOBAL, null);
    }

    private Tool mcpAgentTool(String name, String serverName, String agentId) {
        return new FakeTool(name, serverName, ToolVisibilityResolver.ToolDomain.MCP,
                ToolVisibilityResolver.ToolScope.AGENT, agentId);
    }

    private Tool skillGlobalTool(String name) {
        return new FakeTool(name, null, ToolVisibilityResolver.ToolDomain.SKILL,
                ToolVisibilityResolver.ToolScope.GLOBAL, null);
    }

    private Tool skillAgentTool(String name, String agentId) {
        return new FakeTool(name, null, ToolVisibilityResolver.ToolDomain.SKILL,
                ToolVisibilityResolver.ToolScope.AGENT, agentId);
    }

    private static class FakeTool implements Tool, ToolVisibilityResolver.ScopedToolMetadataProvider {
        private final String name;
        private final String mcpServerName;
        private final ToolVisibilityResolver.ToolDomain domain;
        private final ToolVisibilityResolver.ToolScope scope;
        private final String scopedAgentId;

        private FakeTool(String name,
                         String mcpServerName,
                         ToolVisibilityResolver.ToolDomain domain,
                         ToolVisibilityResolver.ToolScope scope,
                         String scopedAgentId) {
            this.name = name;
            this.mcpServerName = mcpServerName;
            this.domain = domain;
            this.scope = scope;
            this.scopedAgentId = scopedAgentId;
        }

        @Override
        public ToolDefinition getDefinition() {
            return ToolDefinition.builder()
                    .name(name)
                    .description(name + " description")
                    .parameters(Map.of("type", "object", "properties", Map.of()))
                    .build();
        }

        @Override
        public reactor.core.publisher.Mono<ToolResult> execute(Map<String, Object> arguments) {
            return reactor.core.publisher.Mono.just(ToolResult.success("ok"));
        }

        @Override
        public String getMcpServerName() {
            return mcpServerName;
        }

        @Override
        public ToolVisibilityResolver.ToolDomain getToolDomain() {
            return domain;
        }

        @Override
        public ToolVisibilityResolver.ToolScope getToolScope() {
            return scope;
        }

        @Override
        public String getScopedAgentId() {
            return scopedAgentId;
        }
    }
}
