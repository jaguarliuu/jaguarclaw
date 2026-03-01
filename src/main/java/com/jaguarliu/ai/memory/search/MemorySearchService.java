package com.jaguarliu.ai.memory.search;

import com.jaguarliu.ai.memory.MemoryProperties;
import com.jaguarliu.ai.memory.embedding.EmbeddingModel;
import com.jaguarliu.ai.memory.model.MemoryScope;
import com.jaguarliu.ai.memory.index.MemoryChunkSearchOps;
import com.jaguarliu.ai.memory.index.MemoryIndexer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 全局记忆检索服务
 *
 * 检索范围：所有历史记忆（全局，跨会话）
 *
 * 三级降级策略：
 * 1. 向量检索（需要 embedding provider）→ 语义相似度
 * 2. 全文检索（PostgreSQL tsvector，始终可用）→ 关键词匹配
 * 3. 合并去重 → 返回 top-k
 *
 * 如果 embedding provider 不可用，只走 FTS。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemorySearchService {

    private final MemoryChunkSearchOps searchOps;
    private final MemoryIndexer indexer;
    private final MemoryProperties properties;

    /**
     * 语义检索全局记忆
     *
     * @param query 检索查询
     * @return 排序后的检索结果（全局，不区分会话）
     */
    public List<SearchResult> search(String query) {
        return search(query, MemoryScope.GLOBAL, null);
    }

    public List<SearchResult> search(String query, MemoryScope scope, String agentId) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        MemoryScope resolvedScope = scope == null ? MemoryScope.GLOBAL : scope;
        String resolvedAgentId = normalizeAgentId(agentId);
        MemoryProperties.SearchConfig config = properties.getSearch();
        Map<String, ScopedSearchResult> resultMap = new LinkedHashMap<>();
        int vectorRequestTopK = requestTopK(config.getVectorTopK(), resolvedScope);
        int ftsRequestTopK = requestTopK(config.getFtsTopK(), resolvedScope);

        // 1. 向量检索（如果可用）
        if (indexer.isVectorSearchEnabled()) {
            try {
                List<ScopedSearchResult> vectorResults = searchByVector(query, vectorRequestTopK, resolvedScope, resolvedAgentId);
                for (ScopedSearchResult r : vectorResults) {
                    if (r.getScore() >= config.getMinSimilarity()) {
                        resultMap.putIfAbsent(r.getDedupeKey(), r);
                    }
                }
                log.debug("Vector search returned {} results (after similarity filter)",
                        vectorResults.stream().filter(r -> r.getScore() >= config.getMinSimilarity()).count());
            } catch (Exception e) {
                log.warn("Vector search failed, falling back to FTS only: {}", e.getMessage());
            }
        }

        // 2. FTS 检索（始终执行，补充向量检索的遗漏）
        try {
            List<ScopedSearchResult> ftsResults = searchByFts(query, ftsRequestTopK, resolvedScope, resolvedAgentId);
            for (ScopedSearchResult r : ftsResults) {
                // 不覆盖已有的向量结果（向量分数更准确）
                resultMap.putIfAbsent(r.getDedupeKey(), r);
            }
            log.debug("FTS search returned {} results", ftsResults.size());
        } catch (Exception e) {
            log.warn("FTS search failed: {}", e.getMessage());
        }

        // 3. 合并排序 → 返回 top-k
        List<SearchResult> merged = resultMap.values().stream()
                .sorted(buildComparator(resolvedScope, resolvedAgentId))
                .limit(config.getFinalTopK())
                .map(ScopedSearchResult::toResult)
                .toList();

        log.info("Memory search for '{}': {} results (scope={}, agentId={}, vector={}, merged={})",
                truncate(query, 50), merged.size(), resolvedScope, resolvedAgentId, indexer.isVectorSearchEnabled(), resultMap.size());

        return merged;
    }

    /**
     * 仅向量检索
     *
     * @param query 检索查询
     * @param topK  返回数量
     * @return 检索结果列表
     */
    public List<SearchResult> searchByVectorOnly(String query, int topK) {
        return searchByVectorOnly(query, topK, MemoryScope.GLOBAL, null);
    }

    public List<SearchResult> searchByVectorOnly(String query, int topK, MemoryScope scope, String agentId) {
        if (!indexer.isVectorSearchEnabled()) {
            log.debug("Vector search not available");
            return List.of();
        }
        return searchByVector(query, requestTopK(topK, scope), scope == null ? MemoryScope.GLOBAL : scope, normalizeAgentId(agentId)).stream()
                .limit(topK)
                .map(ScopedSearchResult::toResult)
                .toList();
    }

    /**
     * 仅 FTS 检索
     *
     * @param query 检索查询
     * @param topK  返回数量
     * @return 检索结果列表
     */
    public List<SearchResult> searchByFtsOnly(String query, int topK) {
        return searchByFtsOnly(query, topK, MemoryScope.GLOBAL, null);
    }

    public List<SearchResult> searchByFtsOnly(String query, int topK, MemoryScope scope, String agentId) {
        return searchByFts(query, requestTopK(topK, scope), scope == null ? MemoryScope.GLOBAL : scope, normalizeAgentId(agentId)).stream()
                .limit(topK)
                .map(ScopedSearchResult::toResult)
                .toList();
    }

    /**
     * 向量检索实现
     */
    private List<ScopedSearchResult> searchByVector(String query, int topK, MemoryScope scope, String agentId) {
        EmbeddingModel embeddingModel = indexer.getEmbeddingModel();
        if (embeddingModel == null) {
            return List.of();
        }

        // 生成查询向量
        float[] queryVector = embeddingModel.embed(query);
        if (queryVector == null || queryVector.length == 0) {
            log.warn("Failed to generate query embedding");
            return List.of();
        }

        // 格式化为 PostgreSQL vector 字符串
        String vectorStr = formatVector(queryVector);

        // 执行检索
        List<Object[]> rows = searchOps.searchByVector(vectorStr, topK);
        int snippetMax = properties.getSearch().getSnippetMaxChars();

        return rows.stream()
                .map(row -> toScopedResult(row, snippetMax, "vector"))
                .filter(Objects::nonNull)
                .filter(r -> matchesScope(r.scope(), r.agentId(), scope, agentId))
                .toList();
    }

    /**
     * 全文检索实现
     */
    private List<ScopedSearchResult> searchByFts(String query, int topK, MemoryScope scope, String agentId) {
        List<Object[]> rows = searchOps.searchByFts(query, topK);
        int snippetMax = properties.getSearch().getSnippetMaxChars();

        return rows.stream()
                .map(row -> toScopedResult(row, snippetMax, "fts"))
                .filter(Objects::nonNull)
                .map(result -> result.withScore(normalizeRank(result.result().getScore())))
                .filter(r -> matchesScope(r.scope(), r.agentId(), scope, agentId))
                .toList();
    }

    /**
     * 将 FTS rank 归一化到 0~1 区间
     * ts_rank 通常在 0~1 之间，但不保证
     */
    private double normalizeRank(double rank) {
        return Math.min(1.0, Math.max(0.0, rank));
    }

    /**
     * 格式化向量为 PostgreSQL vector 字符串
     */
    private String formatVector(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 截断字符串
     */
    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }

    private ScopedSearchResult toScopedResult(Object[] row, int snippetMax, String source) {
        if (row == null || row.length < 6) {
            return null;
        }

        String rowScope = MemoryScope.GLOBAL.name();
        String rowAgentId = null;
        if (row.length > 6 && row[6] != null) {
            rowScope = row[6].toString().toUpperCase(Locale.ROOT);
        }
        if (row.length > 7 && row[7] != null) {
            rowAgentId = row[7].toString();
        }

        SearchResult result = SearchResult.builder()
                .filePath((String) row[1])
                .lineStart(((Number) row[2]).intValue())
                .lineEnd(((Number) row[3]).intValue())
                .snippet(truncate((String) row[4], snippetMax))
                .score(((Number) row[5]).doubleValue())
                .source(source)
                .build();

        return new ScopedSearchResult(result, rowScope, rowAgentId);
    }

    private Comparator<ScopedSearchResult> buildComparator(MemoryScope scope, String agentId) {
        Comparator<ScopedSearchResult> byScore = Comparator.comparingDouble((ScopedSearchResult r) -> r.result().getScore()).reversed();
        if (scope != MemoryScope.BOTH) {
            return byScore;
        }
        return Comparator
                .comparing((ScopedSearchResult r) -> r.isAgentOwnedBy(agentId))
                .reversed()
                .thenComparing(byScore);
    }

    private boolean matchesScope(String rowScope, String rowAgentId, MemoryScope requestedScope, String requestedAgentId) {
        if (requestedScope == MemoryScope.GLOBAL) {
            return MemoryScope.GLOBAL.name().equalsIgnoreCase(rowScope);
        }
        if (requestedScope == MemoryScope.AGENT) {
            return MemoryScope.AGENT.name().equalsIgnoreCase(rowScope)
                    && requestedAgentId != null
                    && requestedAgentId.equals(rowAgentId);
        }
        boolean isGlobal = MemoryScope.GLOBAL.name().equalsIgnoreCase(rowScope);
        boolean isAgent = MemoryScope.AGENT.name().equalsIgnoreCase(rowScope)
                && requestedAgentId != null
                && requestedAgentId.equals(rowAgentId);
        return isGlobal || isAgent;
    }

    private int requestTopK(int topK, MemoryScope scope) {
        MemoryScope resolvedScope = scope == null ? MemoryScope.GLOBAL : scope;
        if (resolvedScope == MemoryScope.GLOBAL) {
            return topK;
        }
        return Math.max(topK * 4, topK);
    }

    private String normalizeAgentId(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return null;
        }
        return agentId;
    }

    private record ScopedSearchResult(SearchResult result, String scope, String agentId) {
        String getDedupeKey() {
            return result.getDedupeKey();
        }

        double getScore() {
            return result.getScore();
        }

        boolean isAgentOwnedBy(String targetAgentId) {
            return MemoryScope.AGENT.name().equalsIgnoreCase(scope)
                    && targetAgentId != null
                    && targetAgentId.equals(agentId);
        }

        SearchResult toResult() {
            return result;
        }

        ScopedSearchResult withScore(double score) {
            return new ScopedSearchResult(
                    SearchResult.builder()
                            .filePath(result.getFilePath())
                            .lineStart(result.getLineStart())
                            .lineEnd(result.getLineEnd())
                            .snippet(result.getSnippet())
                            .score(score)
                            .source(result.getSource())
                            .build(),
                    scope,
                    agentId
            );
        }
    }
}
