package com.jaguarliu.ai.document;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;

    public record DocumentNode(String id, String parentId, String title,
                               int sortOrder, int wordCount,
                               String createdAt, String updatedAt,
                               List<DocumentNode> children) {}

    // ---------- CRUD ----------

    @Transactional
    public DocumentEntity create(String title, String parentId, String ownerId) {
        var entity = DocumentEntity.builder()
                .title(title != null && !title.isBlank() ? title : "Untitled")
                .content("{}")
                .parentId(parentId)
                .ownerId(ownerId != null ? ownerId : "local-default")
                .build();
        return documentRepository.save(entity);
    }

    public DocumentEntity get(String id, String ownerId) {
        return documentRepository.findById(id)
                .filter(d -> ownerId == null || ownerId.equals(d.getOwnerId()))
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));
    }

    @Transactional
    public DocumentEntity update(String id, String title, String content, int wordCount, String ownerId) {
        var doc = get(id, ownerId);
        if (title != null) doc.setTitle(title);
        if (content != null) doc.setContent(content);
        doc.setWordCount(wordCount);
        return documentRepository.save(doc);
    }

    @Transactional
    public void delete(String id, String ownerId) {
        var doc = get(id, ownerId);
        // Reparent children to null (they become roots), then delete the document
        var children = documentRepository.findByParentIdOrderBySortOrderAsc(id);
        children.forEach(c -> { c.setParentId(null); documentRepository.save(c); });
        documentRepository.delete(doc);
    }

    // ---------- Tree ----------

    public List<DocumentNode> getTree(String ownerId) {
        var roots = documentRepository.findRoots(ownerId);
        return roots.stream().map(this::buildNode).toList();
    }

    private DocumentNode buildNode(DocumentEntity e) {
        var children = documentRepository.findByParentIdOrderBySortOrderAsc(e.getId())
                .stream().map(this::buildNode).toList();
        return new DocumentNode(
                e.getId(), e.getParentId(), e.getTitle(),
                e.getSortOrder(), e.getWordCount(),
                e.getCreatedAt() != null ? e.getCreatedAt().toString() : "",
                e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : "",
                children);
    }
}
