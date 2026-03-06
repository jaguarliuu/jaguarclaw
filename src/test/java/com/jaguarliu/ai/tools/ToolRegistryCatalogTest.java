package com.jaguarliu.ai.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

@DisplayName("Tool Registry Catalog Tests")
class ToolRegistryCatalogTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        registry = new ToolRegistry(applicationContext);

        registry.register(new FakeTool("web_search", "Search web", false, null));
        registry.register(new FakeTool("shell", "Execute shell", true, null));
        registry.register(new FakeTool("read_file", "Read file", false, null));
        registry.register(new FakeTool("mcp_fs_read", "Read via MCP", false, "filesystem"));
    }

    @Test
    @DisplayName("listCatalog returns deterministic category-first ordering")
    void listCatalogShouldBeCategoryFirstAndDeterministic() {
        List<ToolCatalogEntry> catalog = registry.listCatalog();

        assertEquals(4, catalog.size());
        assertEquals("read_file", catalog.get(0).name());
        assertEquals("filesystem", catalog.get(0).category());

        assertEquals("shell", catalog.get(1).name());
        assertEquals("command_local", catalog.get(1).category());

        assertEquals("web_search", catalog.get(2).name());
        assertEquals("network", catalog.get(2).category());

        assertEquals("mcp_fs_read", catalog.get(3).name());
        assertEquals("mcp", catalog.get(3).category());
    }

    private static class FakeTool implements Tool {
        private final String name;
        private final String description;
        private final boolean hitl;
        private final String mcpServer;

        FakeTool(String name, String description, boolean hitl, String mcpServer) {
            this.name = name;
            this.description = description;
            this.hitl = hitl;
            this.mcpServer = mcpServer;
        }

        @Override
        public ToolDefinition getDefinition() {
            return ToolDefinition.builder()
                    .name(name)
                    .description(description)
                    .hitl(hitl)
                    .parameters(Map.of("type", "object", "properties", Map.of()))
                    .build();
        }

        @Override
        public reactor.core.publisher.Mono<ToolResult> execute(Map<String, Object> arguments) {
            return reactor.core.publisher.Mono.just(ToolResult.success("ok"));
        }

        @Override
        public String getMcpServerName() {
            return mcpServer;
        }
    }
}
