package com.jaguarliu.ai.mcp;

import com.jaguarliu.ai.mcp.client.ManagedMcpClient;
import com.jaguarliu.ai.mcp.client.McpClientManager;
import com.jaguarliu.ai.mcp.service.McpServerService;
import com.jaguarliu.ai.mcp.tools.McpToolAdapter;
import com.jaguarliu.ai.tools.ToolVisibilityResolver;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Import;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(McpServerService.class)
@DisplayName("MCP Scope Tests")
class McpScopeTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private McpServerService mcpServerService;

    @MockBean
    private McpClientManager mcpClientManager;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM mcp_servers");
    }

    @Test
    @DisplayName("V21 应为 mcp_servers 增加 scope 与 agent_id 列")
    void shouldAddScopeColumns() {
        List<Map<String, Object>> tableInfo = jdbcTemplate.queryForList("PRAGMA table_info('mcp_servers')");
        Set<String> columns = new HashSet<>();
        for (Map<String, Object> row : tableInfo) {
            Object name = row.get("name");
            if (name != null) {
                columns.add(name.toString().toLowerCase());
            }
        }

        assertThat(columns).contains("scope", "agent_id");
    }

    @Test
    @DisplayName("V21 应创建 scope 与 scope+agent_id 索引")
    void shouldCreateScopeIndexes() {
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList("PRAGMA index_list('mcp_servers')");
        Set<String> indexNames = new HashSet<>();
        for (Map<String, Object> index : indexes) {
            Object name = index.get("name");
            if (name != null) {
                indexNames.add(name.toString().toLowerCase());
            }
        }

        assertThat(indexNames).contains("idx_mcp_servers_scope", "idx_mcp_servers_scope_agent");
    }

    @Test
    @DisplayName("scope=agent 时必须携带 agentId")
    void shouldRequireAgentIdForAgentScope() {
        McpProperties.ServerConfig config = baseConfig("agent-no-id");
        config.setScope("agent");
        config.setAgentId(null);

        assertThatThrownBy(() -> mcpServerService.createServer(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agentId is required");
    }

    @Test
    @DisplayName("listEnabledServers(agentId) 应返回 global + 对应 agent 的 MCP servers")
    void shouldListEnabledServersByScope() {
        when(mcpClientManager.testConnection(any())).thenReturn(true);

        McpProperties.ServerConfig global = baseConfig("global-server");
        global.setScope("global");

        McpProperties.ServerConfig agentA = baseConfig("agent-a-server");
        agentA.setScope("agent");
        agentA.setAgentId("agent-a");

        McpProperties.ServerConfig agentB = baseConfig("agent-b-server");
        agentB.setScope("agent");
        agentB.setAgentId("agent-b");

        McpProperties.ServerConfig disabled = baseConfig("disabled-global");
        disabled.setScope("global");
        disabled.setEnabled(false);

        mcpServerService.createServer(global);
        mcpServerService.createServer(agentA);
        mcpServerService.createServer(agentB);
        mcpServerService.createServer(disabled);

        Set<String> visibleToA = mcpServerService.listEnabledServers("agent-a").stream()
                .map(s -> s.getName())
                .collect(Collectors.toSet());

        assertThat(visibleToA).contains("global-server", "agent-a-server");
        assertThat(visibleToA).doesNotContain("agent-b-server", "disabled-global");
    }

    @Test
    @DisplayName("MCP 工具适配器应暴露 scope 元数据给 ToolVisibilityResolver")
    void mcpAdapterShouldExposeScopeMetadata() {
        McpSchema.Tool mcpTool = mock(McpSchema.Tool.class);
        when(mcpTool.name()).thenReturn("list_files");
        when(mcpTool.description()).thenReturn("list files");
        when(mcpTool.inputSchema()).thenReturn(null);

        McpProperties.ServerConfig config = new McpProperties.ServerConfig();
        config.setName("mcp-agent-a");
        config.setToolPrefix("mcp_");
        config.setScope("agent");
        config.setAgentId("agent-a");

        ManagedMcpClient client = mock(ManagedMcpClient.class);
        when(client.getName()).thenReturn("mcp-agent-a");
        when(client.getConfig()).thenReturn(config);
        when(client.getToolPrefix()).thenReturn("mcp_");

        McpToolAdapter adapter = new McpToolAdapter(mcpTool, client);

        ToolVisibilityResolver.ScopedToolMetadataProvider provider =
                (ToolVisibilityResolver.ScopedToolMetadataProvider) adapter;
        assertThat(provider.getToolDomain()).isEqualTo(ToolVisibilityResolver.ToolDomain.MCP);
        assertThat(provider.getToolScope()).isEqualTo(ToolVisibilityResolver.ToolScope.AGENT);
        assertThat(provider.getScopedAgentId()).isEqualTo("agent-a");
    }

    private McpProperties.ServerConfig baseConfig(String name) {
        McpProperties.ServerConfig config = new McpProperties.ServerConfig();
        config.setName(name);
        config.setTransport(McpProperties.TransportType.STDIO);
        config.setCommand("echo");
        config.setArgs(List.of("hello"));
        config.setEnabled(true);
        return config;
    }
}
