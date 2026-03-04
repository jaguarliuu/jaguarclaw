package com.jaguarliu.ai.tools.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.nodeconsole.NodeEntity;
import com.jaguarliu.ai.nodeconsole.NodeService;
import com.jaguarliu.ai.tools.ToolResult;
import com.jaguarliu.ai.tools.builtin.node.NodeStatusCheckTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("NodeStatusCheckTool Tests")
class NodeStatusCheckToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("checks single alias and executes default ssh status command")
    @SuppressWarnings("unchecked")
    void checksAliasWithDefaultCommand() throws Exception {
        NodeService nodeService = mock(NodeService.class);
        NodeStatusCheckTool tool = new NodeStatusCheckTool(nodeService, objectMapper);

        NodeEntity node = NodeEntity.builder()
                .id("n1")
                .alias("prod-web-1")
                .connectorType("ssh")
                .tags("prod,web")
                .build();

        when(nodeService.findByAlias("prod-web-1")).thenReturn(Optional.of(node));
        when(nodeService.testConnectionDetailed("n1")).thenReturn(
                new NodeService.ConnectionTestReport(
                        "n1", "prod-web-1", true, null, "Connection successful", 200L,
                        LocalDateTime.of(2026, 3, 4, 12, 30)
                )
        );
        when(nodeService.executeCommand("prod-web-1", "uptime")).thenReturn("up 3 days");

        ToolResult result = tool.execute(Map.of("alias", "prod-web-1")).block();
        assertNotNull(result);
        assertTrue(result.isSuccess());

        Map<String, Object> payload = objectMapper.readValue(result.getContent(), Map.class);
        assertEquals(1, ((Number) payload.get("total")).intValue());

        List<Map<String, Object>> reports = (List<Map<String, Object>>) payload.get("reports");
        assertEquals(1, reports.size());
        assertEquals("prod-web-1", reports.get(0).get("alias"));
        assertEquals("uptime", reports.get(0).get("command"));
        assertEquals("up 3 days", reports.get(0).get("output"));
    }

    @Test
    @DisplayName("returns error when no node matched")
    void returnsErrorWhenNoMatch() {
        NodeService nodeService = mock(NodeService.class);
        NodeStatusCheckTool tool = new NodeStatusCheckTool(nodeService, objectMapper);

        when(nodeService.findByAlias("missing")).thenReturn(Optional.empty());

        ToolResult result = tool.execute(Map.of("alias", "missing")).block();
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getContent().contains("No nodes matched the filter"));
    }

    @Test
    @DisplayName("filters by tag and applies max_nodes limit")
    @SuppressWarnings("unchecked")
    void filtersByTagAndLimit() throws Exception {
        NodeService nodeService = mock(NodeService.class);
        NodeStatusCheckTool tool = new NodeStatusCheckTool(nodeService, objectMapper);

        NodeEntity n1 = NodeEntity.builder().id("n1").alias("a").connectorType("ssh").tags("prod,web").build();
        NodeEntity n2 = NodeEntity.builder().id("n2").alias("b").connectorType("ssh").tags("prod,db").build();
        NodeEntity n3 = NodeEntity.builder().id("n3").alias("c").connectorType("ssh").tags("dev,web").build();

        when(nodeService.listAll()).thenReturn(List.of(n1, n2, n3));
        when(nodeService.testConnectionDetailed(anyString())).thenReturn(
                new NodeService.ConnectionTestReport("id", "alias", true, null, "Connection successful", 100L, LocalDateTime.now())
        );
        when(nodeService.executeCommand(anyString(), eq("uptime"))).thenReturn("ok");

        ToolResult result = tool.execute(Map.of("tag", "prod", "max_nodes", 1)).block();
        assertNotNull(result);
        assertTrue(result.isSuccess());

        Map<String, Object> payload = objectMapper.readValue(result.getContent(), Map.class);
        List<Map<String, Object>> reports = (List<Map<String, Object>>) payload.get("reports");
        assertEquals(1, reports.size());
    }
}
