package com.jaguarliu.ai.mcp.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MCP Server 配置仓库
 */
@Repository
public interface McpServerRepository extends JpaRepository<McpServerEntity, Long> {

    /**
     * 查找所有已启用的 MCP Server
     */
    List<McpServerEntity> findByEnabledTrue();

    /**
     * 查找指定作用域的已启用 MCP Server
     */
    List<McpServerEntity> findByEnabledTrueAndScope(String scope);

    /**
     * 查找指定作用域+agent 的已启用 MCP Server
     */
    List<McpServerEntity> findByEnabledTrueAndScopeAndAgentId(String scope, String agentId);

    /**
     * 查找对指定 agent 可见的已启用 MCP Server（GLOBAL + AGENT(agentId)）
     */
    @Query("""
            SELECT s FROM McpServerEntity s
            WHERE s.enabled = true
              AND (s.scope = 'GLOBAL' OR (s.scope = 'AGENT' AND s.agentId = :agentId))
            """)
    List<McpServerEntity> findEnabledVisibleToAgent(@Param("agentId") String agentId);

    /**
     * 根据名称查找 MCP Server
     */
    Optional<McpServerEntity> findByName(String name);

    /**
     * 检查指定名称的 MCP Server 是否存在
     */
    boolean existsByName(String name);

    /**
     * 删除指定 agent 的 AGENT-scope MCP Server 配置
     */
    void deleteByScopeAndAgentId(String scope, String agentId);
}
