package com.jaguarliu.ai.memory.index;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * PostgreSQL 实现：使用 pgvector + tsvector native query
 */
@Repository
@Profile("pg")
public class PgMemoryChunkSearchOps implements MemoryChunkSearchOps {

    @PersistenceContext
    private EntityManager em;

    @Override
    public List<Object[]> searchByVector(String embedding, int limit) {
        return searchByVector(embedding, limit, null, null);
    }

    @Override
    public List<Object[]> searchByVector(String embedding, int limit, String scope, String agentId) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, file_path, line_start, line_end, content,
                       1 - (embedding <=> cast(:embedding as vector)) AS similarity,
                       scope, agent_id
                FROM memory_chunks
                WHERE embedding IS NOT NULL
                """);
        if (scope != null) {
            sql.append(" AND scope = :scope");
        }
        if (agentId != null) {
            sql.append(" AND agent_id = :agentId");
        }
        sql.append(" ORDER BY embedding <=> cast(:embedding as vector) LIMIT :limit");

        var query = em.createNativeQuery(sql.toString())
                .setParameter("embedding", embedding)
                .setParameter("limit", limit);
        if (scope != null) {
            query.setParameter("scope", scope);
        }
        if (agentId != null) {
            query.setParameter("agentId", agentId);
        }
        return query.getResultList();
    }

    @Override
    public List<Object[]> searchByFts(String query, int limit) {
        return searchByFts(query, limit, null, null);
    }

    @Override
    public List<Object[]> searchByFts(String ftsQuery, int limit, String scope, String agentId) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, file_path, line_start, line_end, content,
                       ts_rank(tsv, plainto_tsquery('simple', :query)) AS rank,
                       scope, agent_id
                FROM memory_chunks
                WHERE tsv @@ plainto_tsquery('simple', :query)
                """);
        if (scope != null) {
            sql.append(" AND scope = :scope");
        }
        if (agentId != null) {
            sql.append(" AND agent_id = :agentId");
        }
        sql.append(" ORDER BY rank DESC LIMIT :limit");

        var query = em.createNativeQuery(sql.toString())
                .setParameter("query", ftsQuery)
                .setParameter("limit", limit);
        if (scope != null) {
            query.setParameter("scope", scope);
        }
        if (agentId != null) {
            query.setParameter("agentId", agentId);
        }
        return query.getResultList();
    }

    @Override
    @Transactional
    public void updateEmbedding(String id, String embedding) {
        em.createNativeQuery("""
                UPDATE memory_chunks SET embedding = cast(:embedding as vector), updated_at = NOW()
                WHERE id = :id
                """)
                .setParameter("embedding", embedding)
                .setParameter("id", id)
                .executeUpdate();
    }

    @Override
    public long countWithEmbedding() {
        return ((Number) em.createNativeQuery("SELECT COUNT(*) FROM memory_chunks WHERE embedding IS NOT NULL")
                .getSingleResult()).longValue();
    }

    @Override
    public long countTotal() {
        return ((Number) em.createNativeQuery("SELECT COUNT(*) FROM memory_chunks")
                .getSingleResult()).longValue();
    }

    @Override
    public List<Object[]> findChunksWithoutEmbedding(int limit) {
        return em.createNativeQuery("""
                SELECT id, file_path, line_start, line_end, content
                FROM memory_chunks
                WHERE embedding IS NULL
                LIMIT :limit
                """)
                .setParameter("limit", limit)
                .getResultList();
    }
}
