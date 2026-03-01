package com.jaguarliu.ai.memory.index;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * memory_chunks 存储库
 *
 * 全局记忆检索，不区分会话。
 * 数据库特有的检索操作（向量/FTS）已提取到 {@link MemoryChunkSearchOps}。
 */
@Repository
public interface MemoryChunkRepository extends JpaRepository<MemoryChunkEntity, String> {

    /**
     * 按文件路径查找所有 chunks
     */
    List<MemoryChunkEntity> findByFilePath(String filePath);

    /**
     * 按作用域和 agent 维度查找 chunks（Task07 使用）
     */
    List<MemoryChunkEntity> findByScope(String scope);
    List<MemoryChunkEntity> findByScopeAndAgentId(String scope, String agentId);
    List<MemoryChunkEntity> findByFilePathAndScope(String filePath, String scope);
    List<MemoryChunkEntity> findByFilePathAndScopeAndAgentId(String filePath, String scope, String agentId);

    /**
     * 删除指定文件的所有 chunks
     */
    @Transactional
    @Modifying
    void deleteByFilePath(String filePath);

    @Transactional
    @Modifying
    void deleteByFilePathAndScope(String filePath, String scope);

    @Transactional
    @Modifying
    void deleteByFilePathAndScopeAndAgentId(String filePath, String scope, String agentId);

    /**
     * 删除所有 chunks（重建索引时使用）
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM MemoryChunkEntity")
    void deleteAllChunks();

    @Transactional
    @Modifying
    void deleteByScope(String scope);

    @Transactional
    @Modifying
    void deleteByScopeAndAgentId(String scope, String agentId);
}
