package com.jaguarliu.ai.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface DocumentRepository extends JpaRepository<DocumentEntity, String> {

    @Query("SELECT d FROM DocumentEntity d WHERE d.ownerId = :ownerId AND d.parentId IS NULL ORDER BY d.sortOrder ASC")
    List<DocumentEntity> findRoots(@Param("ownerId") String ownerId);

    List<DocumentEntity> findByParentIdOrderBySortOrderAsc(String parentId);

    List<DocumentEntity> findByOwnerIdOrderByCreatedAtDesc(String ownerId);
}
