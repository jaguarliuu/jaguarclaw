package com.jaguarliu.ai.memory;

import com.jaguarliu.ai.feature.FeatureFlagsProperties;
import com.jaguarliu.ai.memory.index.MemoryChunkSearchOps;
import com.jaguarliu.ai.memory.index.MemoryIndexer;
import com.jaguarliu.ai.memory.model.MemoryScope;
import com.jaguarliu.ai.memory.search.MemorySearchService;
import com.jaguarliu.ai.memory.search.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Memory Search Scope Tests")
class MemorySearchScopeTest {

    @Mock
    private MemoryChunkSearchOps searchOps;

    @Mock
    private MemoryIndexer indexer;

    @Spy
    private MemoryProperties properties = new MemoryProperties();

    @Spy
    private FeatureFlagsProperties featureFlags = new FeatureFlagsProperties();

    @InjectMocks
    private MemorySearchService searchService;

    @BeforeEach
    void setUp() {
        properties.getSearch().setFinalTopK(10);
        when(indexer.isVectorSearchEnabled()).thenReturn(false);
    }

    @Test
    @DisplayName("scope=AGENT 只返回指定 agent 的私有记忆")
    void shouldReturnOnlyRequestedAgentMemoryWhenScopeAgent() {
        when(searchOps.searchByFts(eq("test"), anyInt(), any(), any())).thenReturn(rows(
                row("g-1", "global.md", 1, 2, "global", 0.95, "GLOBAL", null),
                row("a-1", "agent-a.md", 1, 2, "agent-a", 0.80, "AGENT", "agent-a"),
                row("b-1", "agent-b.md", 1, 2, "agent-b", 0.88, "AGENT", "agent-b")
        ));

        List<SearchResult> results = searchService.search("test", MemoryScope.AGENT, "agent-a");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getFilePath()).isEqualTo("agent-a.md");
    }

    @Test
    @DisplayName("scope=GLOBAL 只返回共享记忆")
    void shouldReturnOnlyGlobalMemoryWhenScopeGlobal() {
        when(searchOps.searchByFts(eq("test"), anyInt(), any(), any())).thenReturn(rows(
                row("g-1", "global.md", 1, 2, "global", 0.95, "GLOBAL", null),
                row("a-1", "agent-a.md", 1, 2, "agent-a", 0.80, "AGENT", "agent-a")
        ));

        List<SearchResult> results = searchService.search("test", MemoryScope.GLOBAL, "agent-a");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getFilePath()).isEqualTo("global.md");
    }

    @Test
    @DisplayName("scope=BOTH 返回混合结果且 agent 私有优先")
    void shouldPrioritizeAgentMemoryWhenScopeBoth() {
        when(searchOps.searchByFts(eq("test"), anyInt(), any(), any())).thenReturn(rows(
                row("g-1", "global.md", 1, 2, "global", 0.95, "GLOBAL", null),
                row("a-1", "agent-a.md", 1, 2, "agent-a", 0.40, "AGENT", "agent-a"),
                row("b-1", "agent-b.md", 1, 2, "agent-b", 0.99, "AGENT", "agent-b")
        ));

        List<SearchResult> results = searchService.search("test", MemoryScope.BOTH, "agent-a");

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getFilePath()).isEqualTo("agent-a.md");
        assertThat(results.get(1).getFilePath()).isEqualTo("global.md");
    }

    private static List<Object[]> rows(Object[]... rows) {
        return Arrays.asList(rows);
    }

    private static Object[] row(
            String id,
            String filePath,
            int lineStart,
            int lineEnd,
            String content,
            double score,
            String scope,
            String agentId
    ) {
        return new Object[]{id, filePath, lineStart, lineEnd, content, score, scope, agentId};
    }
}
