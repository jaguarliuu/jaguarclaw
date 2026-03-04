package com.jaguarliu.ai.tools.builtin;

import com.jaguarliu.ai.memory.index.MemoryIndexer;
import com.jaguarliu.ai.memory.model.MemoryScope;
import com.jaguarliu.ai.memory.search.MemorySearchService;
import com.jaguarliu.ai.memory.store.MemoryStore;
import com.jaguarliu.ai.tools.ToolExecutionContext;
import com.jaguarliu.ai.tools.ToolResult;
import com.jaguarliu.ai.tools.builtin.memory.MemorySearchTool;
import com.jaguarliu.ai.tools.builtin.memory.MemoryWriteTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Memory Scope Tools Tests")
class MemoryScopeToolsTest {

    @Mock
    private MemoryStore memoryStore;
    @Mock
    private MemoryIndexer memoryIndexer;
    @Mock
    private MemorySearchService memorySearchService;

    @AfterEach
    void tearDown() {
        ToolExecutionContext.clear();
    }

    @Test
    @DisplayName("工具 schema 应暴露 scope 参数")
    void toolSchemasShouldExposeScope() {
        MemoryWriteTool writeTool = new MemoryWriteTool(memoryStore, memoryIndexer);
        MemorySearchTool searchTool = new MemorySearchTool(memorySearchService);

        @SuppressWarnings("unchecked")
        Map<String, Object> writeProperties = (Map<String, Object>) writeTool.getDefinition().getParameters().get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> searchProperties = (Map<String, Object>) searchTool.getDefinition().getParameters().get("properties");

        assertTrue(writeProperties.containsKey("scope"));
        assertTrue(searchProperties.containsKey("scope"));
    }

    @Test
    @DisplayName("memory_search 默认 scope=both")
    void memorySearchDefaultShouldBeBoth() {
        ToolExecutionContext.set(ToolExecutionContext.builder().agentId("agent-a").build());
        MemorySearchTool searchTool = new MemorySearchTool(memorySearchService);
        when(memorySearchService.search("python", MemoryScope.BOTH, "agent-a")).thenReturn(List.of());

        ToolResult result = searchTool.execute(Map.of("query", "python")).block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        verify(memorySearchService).search("python", MemoryScope.BOTH, "agent-a");
    }

    @Test
    @DisplayName("memory_write 默认 scope=agent（写入今日日记）")
    void memoryWriteDefaultShouldBeAgent() throws IOException {
        ToolExecutionContext.set(ToolExecutionContext.builder().agentId("agent-a").build());
        MemoryWriteTool writeTool = new MemoryWriteTool(memoryStore, memoryIndexer);

        ToolResult result = writeTool.execute(Map.of("content", "note")).block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        verify(memoryStore).appendToDaily("note", "agent-a", MemoryScope.AGENT);
    }

    @Test
    @DisplayName("memory_write 可显式写入 global")
    void memoryWriteShouldSupportGlobalScope() throws IOException {
        ToolExecutionContext.set(ToolExecutionContext.builder().agentId("agent-a").build());
        MemoryWriteTool writeTool = new MemoryWriteTool(memoryStore, memoryIndexer);

        ToolResult result = writeTool.execute(Map.of(
                "content", "share",
                "scope", "global"
        )).block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        verify(memoryStore).appendToDaily("share", "agent-a", MemoryScope.GLOBAL);
    }
}
