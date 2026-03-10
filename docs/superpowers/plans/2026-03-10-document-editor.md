# Intelligent Document Editor Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Notion-like tree-structured document editor with TipTap and inline AI writing assistance reusing the existing agent/run pipeline.

**Architecture:** SQLite-backed `documents` table stores TipTap JSON; each document gets a hidden `sessionKind='document'` session (invisible to chat sidebar since `listMainSessions` already filters `sessionKind='main'`); `DocumentAiAssistHandler` injects `AgentRunHandler` and calls it with a synthetic RPC request, reusing the full streaming pipeline. Frontend uses TipTap Vue 3 with BubbleMenu (selection), FloatingMenu (slash commands), and toolbar for full-doc actions; AI results stream inline via the existing `assistant.delta` event.

**Tech Stack:** Spring Boot 3.4.5 / Spring Data JPA / Flyway / SQLite + PostgreSQL; Vue 3 + TypeScript; TipTap Vue 3 + StarterKit + BubbleMenu + FloatingMenu + Suggestion

**Spec:** `docs/superpowers/specs/2026-03-10-document-editor-design.md`

---

## Chunk 1: Database + Backend Foundation

### Task 1: Flyway migration V25 — documents table

**Files:**
- Create: `src/main/resources/db/migration/V25__documents.sql`
- Create: `src/main/resources/db/migration-sqlite/V25__documents.sql`

> No sessions table migration needed. `SessionEntity.sessionKind` already exists and `SessionService.listMainSessions()` already filters `sessionKind='main'` — document sessions (`sessionKind='document'`) will be invisible to the chat sidebar automatically.

- [ ] **Step 1: Write PostgreSQL migration**

```sql
-- src/main/resources/db/migration/V25__documents.sql
CREATE TABLE documents (
    id          VARCHAR(36)  PRIMARY KEY,
    parent_id   VARCHAR(36)  REFERENCES documents(id) ON DELETE SET NULL,
    title       VARCHAR(500) NOT NULL DEFAULT 'Untitled',
    content     TEXT         NOT NULL DEFAULT '{}',
    sort_order  INT          NOT NULL DEFAULT 0,
    word_count  INT          NOT NULL DEFAULT 0,
    owner_id    VARCHAR(64)  NOT NULL DEFAULT 'local-default',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_documents_owner   ON documents(owner_id);
CREATE INDEX idx_documents_parent  ON documents(parent_id);
CREATE INDEX idx_documents_updated ON documents(updated_at DESC);
```

- [ ] **Step 2: Write SQLite migration (identical DDL — SQLite accepts REFERENCES)**

```sql
-- src/main/resources/db/migration-sqlite/V25__documents.sql
-- same content as above
```

- [ ] **Step 3: Start the backend and verify Flyway runs V25 cleanly**

```bash
cd /Users/eumenides/Desktop/jaguarliu/core/miniclaw
./mvnw spring-boot:run -q 2>&1 | grep -E 'V25|Flyway|ERROR' | head -20
```

Expected: `Successfully applied 1 migration to schema "" (execution time ...ms)` for V25. No ERROR lines.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/db/migration/V25__documents.sql \
        src/main/resources/db/migration-sqlite/V25__documents.sql
git commit -m "feat(docs): add documents table migration V25"
```

---

### Task 2: DocumentEntity + DocumentRepository

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/document/DocumentEntity.java`
- Create: `src/main/java/com/jaguarliu/ai/document/DocumentRepository.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/jaguarliu/ai/document/DocumentRepositoryTest.java`:

```java
package com.jaguarliu.ai.document;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class DocumentRepositoryTest {

    @Autowired DocumentRepository repo;

    @Test
    void savesAndLoadsDocument() {
        var doc = DocumentEntity.builder()
                .id("doc-1").title("Test").content("{}")
                .sortOrder(0).wordCount(3).ownerId("user-1").build();
        repo.save(doc);

        List<DocumentEntity> found = repo.findRoots("user-1");
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getTitle()).isEqualTo("Test");
    }

    @Test
    void findsChildrenByParent() {
        var parent = DocumentEntity.builder()
                .id("p1").title("Parent").content("{}").ownerId("user-1").build();
        var child = DocumentEntity.builder()
                .id("c1").title("Child").content("{}").parentId("p1").ownerId("user-1").build();
        repo.saveAll(List.of(parent, child));

        assertThat(repo.findByParentIdOrderBySortOrderAsc("p1")).hasSize(1);
    }
}
```

- [ ] **Step 2: Run the test — it must fail (DocumentEntity does not exist)**

```bash
./mvnw test -pl . -Dtest=DocumentRepositoryTest -q 2>&1 | tail -15
```

Expected: compilation failure.

- [ ] **Step 3: Create DocumentEntity**

```java
// src/main/java/com/jaguarliu/ai/document/DocumentEntity.java
package com.jaguarliu.ai.document;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DocumentEntity {

    @Id private String id;
    private String parentId;

    @Column(nullable = false) private String title;
    @Column(columnDefinition = "TEXT", nullable = false) private String content;

    @Builder.Default private int sortOrder = 0;
    @Builder.Default private int wordCount = 0;
    @Builder.Default private String ownerId = "local-default";

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 4: Create DocumentRepository**

```java
// src/main/java/com/jaguarliu/ai/document/DocumentRepository.java
package com.jaguarliu.ai.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<DocumentEntity, String> {

    /** Root documents (no parent) for a given owner, sorted by sort_order */
    @Query("SELECT d FROM DocumentEntity d WHERE d.ownerId = :ownerId AND d.parentId IS NULL ORDER BY d.sortOrder ASC")
    List<DocumentEntity> findRoots(String ownerId);

    /** Children of a given parent node */
    List<DocumentEntity> findByParentIdOrderBySortOrderAsc(String parentId);

    /** All documents for an owner (for tree building) */
    List<DocumentEntity> findByOwnerIdOrderByCreatedAtDesc(String ownerId);
}
```

- [ ] **Step 5: Run the test — it must pass**

```bash
./mvnw test -pl . -Dtest=DocumentRepositoryTest -q 2>&1 | tail -10
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/document/ \
        src/test/java/com/jaguarliu/ai/document/
git commit -m "feat(docs): add DocumentEntity and DocumentRepository"
```

---

### Task 3: DocumentService — CRUD

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/document/DocumentService.java`

The service handles: CRUD operations, tree building, and finding/creating the hidden document session (for AI use, covered in Task 8).

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/jaguarliu/ai/document/DocumentServiceTest.java`:

```java
package com.jaguarliu.ai.document;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock DocumentRepository documentRepository;
    @InjectMocks DocumentService documentService;

    @Test
    void createDocument_setsOwnerAndReturnsEntity() {
        var saved = DocumentEntity.builder().id("d1").title("Hello").content("{}").ownerId("user-1").build();
        when(documentRepository.save(any())).thenReturn(saved);

        var result = documentService.create("Hello", null, "user-1");

        assertThat(result.getTitle()).isEqualTo("Hello");
        assertThat(result.getOwnerId()).isEqualTo("user-1");
        verify(documentRepository).save(argThat(d -> "Hello".equals(d.getTitle()) && "user-1".equals(d.getOwnerId())));
    }

    @Test
    void deleteDocument_throwsIfNotFound() {
        when(documentRepository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> documentService.delete("missing", "user-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildTree_structuresChildrenCorrectly() {
        var root  = DocumentEntity.builder().id("r1").title("Root").content("{}").ownerId("u1").build();
        var child = DocumentEntity.builder().id("c1").title("Child").content("{}").parentId("r1").ownerId("u1").build();
        when(documentRepository.findByOwnerIdOrderByCreatedAtDesc("u1")).thenReturn(List.of(root, child));
        when(documentRepository.findRoots("u1")).thenReturn(List.of(root));
        when(documentRepository.findByParentIdOrderBySortOrderAsc("r1")).thenReturn(List.of(child));

        var tree = documentService.getTree("u1");

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getId()).isEqualTo("r1");
        assertThat(tree.get(0).getChildren()).hasSize(1);
    }
}
```

- [ ] **Step 2: Run the test — it must fail**

```bash
./mvnw test -pl . -Dtest=DocumentServiceTest -q 2>&1 | tail -10
```

- [ ] **Step 3: Create DocumentService**

```java
// src/main/java/com/jaguarliu/ai/document/DocumentService.java
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
        // Cascade: reparent children to null (they become roots), then delete
        var children = documentRepository.findByParentIdOrderBySortOrderAsc(id);
        children.forEach(c -> { c.setParentId(null); documentRepository.save(c); });
        documentRepository.delete(doc);
    }

    // ---------- Tree ----------

    public List<DocumentNode> getTree(String ownerId) {
        var roots = documentRepository.findRoots(ownerId);
        return roots.stream().map(r -> buildNode(r)).toList();
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
```

- [ ] **Step 4: Run the test — it must pass**

```bash
./mvnw test -pl . -Dtest=DocumentServiceTest -q 2>&1 | tail -10
```

Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/document/DocumentService.java \
        src/test/java/com/jaguarliu/ai/document/DocumentServiceTest.java
git commit -m "feat(docs): add DocumentService with CRUD and tree building"
```

---

## Chunk 2: Backend RPC Handlers

### Task 4: SessionService — add document session support

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/session/SessionService.java`
- Modify: `src/main/java/com/jaguarliu/ai/storage/repository/SessionRepository.java`

This adds `findOrCreateDocumentSession(docId, ownerId)` so `DocumentAiAssistHandler` can find the hidden session bound to a document.

- [ ] **Step 1: Add query to SessionRepository**

Open `SessionRepository.java` and add:

```java
// Add to SessionRepository interface
import java.util.Optional;

Optional<SessionEntity> findBySessionKindAndName(String sessionKind, String name);
```

- [ ] **Step 2: Add `createDocumentSession` and `findOrCreateDocumentSession` to SessionService**

Open `SessionService.java` and add the following methods (after the existing `createScheduledSession` or similar):

```java
/**
 * Creates a hidden session for document AI assistance.
 * Uses sessionKind="document" so it is invisible to listMainSessions().
 */
@Transactional
public SessionEntity createDocumentSession(String docId, String ownerPrincipalId) {
    var entity = new SessionEntity();
    entity.setId(java.util.UUID.randomUUID().toString());
    entity.setName("doc:" + docId);
    entity.setSessionKind("document");
    entity.setOwnerPrincipalId(ownerPrincipalId != null ? ownerPrincipalId : DEFAULT_PRINCIPAL_ID);
    entity.setAgentId(DEFAULT_AGENT_ID);
    entity.setCreatedAt(java.time.LocalDateTime.now());
    entity.setUpdatedAt(java.time.LocalDateTime.now());
    return sessionRepository.save(entity);
}

/**
 * Finds the document session for the given docId, or creates one if it doesn't exist.
 */
@Transactional
public SessionEntity findOrCreateDocumentSession(String docId, String ownerPrincipalId) {
    return sessionRepository.findBySessionKindAndName("document", "doc:" + docId)
            .orElseGet(() -> createDocumentSession(docId, ownerPrincipalId));
}
```

- [ ] **Step 3: Verify the backend starts cleanly**

```bash
./mvnw spring-boot:run -q 2>&1 | grep -E 'Started|ERROR' | head -5
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/session/SessionService.java \
        src/main/java/com/jaguarliu/ai/storage/repository/SessionRepository.java
git commit -m "feat(docs): add document session support to SessionService"
```

---

### Task 5: DocumentListHandler + DocumentGetHandler

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/document/DocumentListHandler.java`
- Create: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/document/DocumentGetHandler.java`

Pattern: follow `ScheduleCreateHandler` exactly — `@Slf4j @Component @RequiredArgsConstructor`, inject services, `Mono.fromCallable`, `Schedulers.boundedElastic()`.

- [ ] **Step 1: Create DocumentListHandler**

```java
// src/main/java/com/jaguarliu/ai/gateway/rpc/handler/document/DocumentListHandler.java
package com.jaguarliu.ai.gateway.rpc.handler.document;

import com.jaguarliu.ai.document.DocumentService;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentListHandler implements RpcHandler {

    private final DocumentService documentService;
    private final ConnectionManager connectionManager;

    @Override
    public String getMethod() { return "document.list"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            String ownerId = resolveOwner(connectionId);
            var tree = documentService.getTree(ownerId);
            return RpcResponse.success(request.getId(), Map.of("documents", tree));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> {
              log.error("document.list failed: {}", e.getMessage());
              return Mono.just(RpcResponse.error(request.getId(), "LIST_FAILED", e.getMessage()));
          });
    }

    private String resolveOwner(String connectionId) {
        var principal = connectionManager.getPrincipal(connectionId);
        return principal != null ? principal.getPrincipalId() : "local-default";
    }
}
```

- [ ] **Step 2: Create DocumentGetHandler**

```java
// src/main/java/com/jaguarliu/ai/gateway/rpc/handler/document/DocumentGetHandler.java
package com.jaguarliu.ai.gateway.rpc.handler.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.document.DocumentEntity;
import com.jaguarliu.ai.document.DocumentService;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentGetHandler implements RpcHandler {

    private final DocumentService documentService;
    private final ConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    @Override
    public String getMethod() { return "document.get"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> params = objectMapper.convertValue(request.getPayload(), Map.class);
            String id = (String) params.get("id");
            if (id == null || id.isBlank())
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "id is required");

            String ownerId = resolveOwner(connectionId);
            DocumentEntity doc = documentService.get(id, ownerId);
            return RpcResponse.success(request.getId(), toDto(doc));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> {
              log.error("document.get failed: {}", e.getMessage());
              return Mono.just(RpcResponse.error(request.getId(), "GET_FAILED", e.getMessage()));
          });
    }

    private Map<String, Object> toDto(DocumentEntity doc) {
        var dto = new HashMap<String, Object>();
        dto.put("id", doc.getId());
        dto.put("parentId", doc.getParentId());
        dto.put("title", doc.getTitle());
        dto.put("content", doc.getContent());
        dto.put("wordCount", doc.getWordCount());
        dto.put("sortOrder", doc.getSortOrder());
        dto.put("createdAt", doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : "");
        dto.put("updatedAt", doc.getUpdatedAt() != null ? doc.getUpdatedAt().toString() : "");
        return dto;
    }

    private String resolveOwner(String connectionId) {
        var principal = connectionManager.getPrincipal(connectionId);
        return principal != null ? principal.getPrincipalId() : "local-default";
    }
}
```

- [ ] **Step 3: Start backend, verify handlers are registered**

```bash
./mvnw spring-boot:run -q 2>&1 | grep -i 'document' | head -10
```

Expected: handlers discovered (no ERROR lines).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/gateway/rpc/handler/document/
git commit -m "feat(docs): add DocumentListHandler and DocumentGetHandler"
```

---

### Task 6: DocumentCreateHandler + DocumentUpdateHandler + DocumentDeleteHandler

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/document/DocumentCreateHandler.java`
- Create: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/document/DocumentUpdateHandler.java`
- Create: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/document/DocumentDeleteHandler.java`

- [ ] **Step 1: Create DocumentCreateHandler**

```java
package com.jaguarliu.ai.gateway.rpc.handler.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.document.DocumentService;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.*;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.util.Map;

@Slf4j @Component @RequiredArgsConstructor
public class DocumentCreateHandler implements RpcHandler {

    private final DocumentService documentService;
    private final ConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    @Override public String getMethod() { return "document.create"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> p = objectMapper.convertValue(request.getPayload(), Map.class);
            String title    = (String) p.get("title");
            String parentId = (String) p.get("parentId");
            String ownerId  = resolveOwner(connectionId);
            var doc = documentService.create(title, parentId, ownerId);
            return RpcResponse.success(request.getId(), Map.of(
                "id", doc.getId(), "title", doc.getTitle(),
                "parentId", doc.getParentId() != null ? doc.getParentId() : "",
                "content", doc.getContent(), "wordCount", doc.getWordCount(),
                "createdAt", doc.getCreatedAt().toString(),
                "updatedAt", doc.getUpdatedAt().toString()));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(RpcResponse.error(request.getId(), "CREATE_FAILED", e.getMessage())));
    }

    private String resolveOwner(String cid) {
        var p = connectionManager.getPrincipal(cid);
        return p != null ? p.getPrincipalId() : "local-default";
    }
}
```

- [ ] **Step 2: Create DocumentUpdateHandler**

```java
package com.jaguarliu.ai.gateway.rpc.handler.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.document.DocumentService;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.*;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.util.Map;

@Slf4j @Component @RequiredArgsConstructor
public class DocumentUpdateHandler implements RpcHandler {

    private final DocumentService documentService;
    private final ConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    @Override public String getMethod() { return "document.update"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> p = objectMapper.convertValue(request.getPayload(), Map.class);
            String id       = (String) p.get("id");
            String title    = (String) p.get("title");
            String content  = (String) p.get("content");
            int wordCount   = p.get("wordCount") instanceof Number n ? n.intValue() : 0;
            String ownerId  = resolveOwner(connectionId);

            if (id == null || id.isBlank())
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "id is required");

            var doc = documentService.update(id, title, content, wordCount, ownerId);
            return RpcResponse.success(request.getId(), Map.of(
                "id", doc.getId(), "updatedAt", doc.getUpdatedAt().toString()));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(RpcResponse.error(request.getId(), "UPDATE_FAILED", e.getMessage())));
    }

    private String resolveOwner(String cid) {
        var p = connectionManager.getPrincipal(cid);
        return p != null ? p.getPrincipalId() : "local-default";
    }
}
```

- [ ] **Step 3: Create DocumentDeleteHandler**

```java
package com.jaguarliu.ai.gateway.rpc.handler.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.document.DocumentService;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.*;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.util.Map;

@Slf4j @Component @RequiredArgsConstructor
public class DocumentDeleteHandler implements RpcHandler {

    private final DocumentService documentService;
    private final ConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    @Override public String getMethod() { return "document.delete"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> p = objectMapper.convertValue(request.getPayload(), Map.class);
            String id = (String) p.get("id");
            if (id == null || id.isBlank())
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "id is required");

            String ownerId = resolveOwner(connectionId);
            documentService.delete(id, ownerId);
            return RpcResponse.success(request.getId(), Map.of("success", true));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(RpcResponse.error(request.getId(), "DELETE_FAILED", e.getMessage())));
    }

    private String resolveOwner(String cid) {
        var p = connectionManager.getPrincipal(cid);
        return p != null ? p.getPrincipalId() : "local-default";
    }
}
```

- [ ] **Step 4: Verify backend starts, all 5 document handlers registered**

```bash
./mvnw spring-boot:run -q 2>&1 | grep -i 'rpc\|handler\|ERROR' | grep -i 'document\|ERROR' | head -20
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/gateway/rpc/handler/document/
git commit -m "feat(docs): add document CRUD RPC handlers"
```

---

### Task 7: DocumentAiAssistHandler

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/document/DocumentAiAssistHandler.java`

This handler: finds/creates the document's hidden session, builds a writing-specific prompt, then delegates to `AgentRunHandler` via a synthetic `RpcRequest` to reuse the full streaming pipeline.

> **Note before implementing:** Open `AgentRunHandler.java` and check the exact field names it reads from `request.getPayload()`. It likely uses `sessionId` and `content` (or `prompt`). Adjust the synthetic payload map keys to match exactly.

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/com/jaguarliu/ai/document/DocumentAiAssistHandlerTest.java
package com.jaguarliu.ai.document;

import com.jaguarliu.ai.gateway.rpc.handler.agent.AgentRunHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.gateway.rpc.handler.document.DocumentAiAssistHandler;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import com.jaguarliu.ai.session.SessionService;
import com.jaguarliu.ai.storage.entity.SessionEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentAiAssistHandlerTest {

    @Mock AgentRunHandler agentRunHandler;
    @Mock DocumentService documentService;
    @Mock SessionService sessionService;
    @Mock ConnectionManager connectionManager;
    @InjectMocks DocumentAiAssistHandler handler;

    @Test
    void delegatesToAgentRunHandler() {
        var session = new SessionEntity();
        session.setId("sess-1");

        var doc = DocumentEntity.builder().id("doc-1").title("T").content("{}").ownerId("u1").build();
        when(documentService.get("doc-1", "local-default")).thenReturn(doc);
        when(sessionService.findOrCreateDocumentSession("doc-1", "local-default")).thenReturn(session);
        when(agentRunHandler.handle(any(), any()))
                .thenReturn(Mono.just(RpcResponse.success("run-req-1", Map.of("runId", "run-42"))));

        var req = RpcRequest.builder()
                .id("req-1").method("document.ai.assist")
                .payload(Map.of("docId", "doc-1", "action", "optimize"))
                .build();

        var resp = handler.handle("conn-1", req).block();

        assertThat(resp).isNotNull();
        assertThat(resp.isSuccess()).isTrue();
        verify(agentRunHandler).handle(eq("conn-1"), any());
    }
}
```

- [ ] **Step 2: Run test — it must fail (handler does not exist)**

```bash
./mvnw test -pl . -Dtest=DocumentAiAssistHandlerTest -q 2>&1 | tail -10
```

- [ ] **Step 3: Create DocumentAiAssistHandler**

```java
package com.jaguarliu.ai.gateway.rpc.handler.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.document.DocumentService;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.handler.agent.AgentRunHandler;
import com.jaguarliu.ai.gateway.rpc.model.*;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import com.jaguarliu.ai.session.SessionService;
import com.jaguarliu.ai.storage.entity.SessionEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentAiAssistHandler implements RpcHandler {

    private final DocumentService documentService;
    private final SessionService sessionService;
    private final AgentRunHandler agentRunHandler;
    private final ConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    @Override
    public String getMethod() { return "document.ai.assist"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> p = objectMapper.convertValue(request.getPayload(), Map.class);
            String docId     = (String) p.get("docId");
            String action    = (String) p.get("action");    // continue|optimize|rewrite|summarize|translate
            String selection = (String) p.get("selection"); // nullable

            if (docId == null || docId.isBlank())
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "docId is required");
            if (action == null || action.isBlank())
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "action is required");

            String ownerId = resolveOwner(connectionId);
            var doc = documentService.get(docId, ownerId);
            SessionEntity session = sessionService.findOrCreateDocumentSession(docId, ownerId);
            String prompt = buildPrompt(action, doc.getContent(), selection);

            // Delegate to existing agent.run pipeline via synthetic request
            // NOTE: Verify the exact payload keys AgentRunHandler expects (sessionId, content).
            var syntheticReq = RpcRequest.builder()
                    .id(UUID.randomUUID().toString())
                    .method("agent.run")
                    .payload(Map.of("sessionId", session.getId(), "content", prompt))
                    .build();

            return null; // sentinel — actual return happens in flatMap below
        }).subscribeOn(Schedulers.boundedElastic())
          // Rebuild as Mono chain to properly sequence async work:
          .then(Mono.defer(() -> {
              Map<String, Object> p = objectMapper.convertValue(request.getPayload(), Map.class);
              String docId  = (String) p.get("docId");
              String action = (String) p.get("action");
              String sel    = (String) p.get("selection");
              String own    = resolveOwner(connectionId);
              var doc       = documentService.get(docId, own);
              var session   = sessionService.findOrCreateDocumentSession(docId, own);
              String prompt = buildPrompt(action, doc.getContent(), sel);

              var syntheticReq = RpcRequest.builder()
                      .id(UUID.randomUUID().toString())
                      .method("agent.run")
                      .payload(Map.of("sessionId", session.getId(), "content", prompt))
                      .build();

              return agentRunHandler.handle(connectionId, syntheticReq)
                      .map(runResponse -> {
                          // Extract runId from agent.run response and return it
                          if (!runResponse.isSuccess()) {
                              return RpcResponse.error(request.getId(), "AI_ASSIST_FAILED",
                                      "Agent run failed");
                          }
                          Object payload = runResponse.getResult();
                          return RpcResponse.success(request.getId(), payload);
                      });
          }))
          .onErrorResume(e -> {
              log.error("document.ai.assist failed: {}", e.getMessage(), e);
              return Mono.just(RpcResponse.error(request.getId(), "AI_ASSIST_ERROR", e.getMessage()));
          });
    }

    private String buildPrompt(String action, String docContent, String selection) {
        // Extract plain text from TipTap JSON for the prompt
        // TipTap JSON is complex; for now extract the raw JSON as context.
        // Improvement: parse TipTap JSON and extract text nodes (future task).
        String target = (selection != null && !selection.isBlank()) ? selection : docContent;
        return switch (action) {
            case "continue"  -> "请续写以下内容，保持原有文风，只输出续写部分，不重复已有内容：\n\n" + target;
            case "optimize"  -> "请润色以下文本，改善表达，保留原意，只输出润色后的完整文本：\n\n" + target;
            case "rewrite"   -> "请改写以下文本，使其更清晰简洁，只输出改写结果：\n\n" + target;
            case "summarize" -> "请提炼以下文档的核心要点，以3-5条Markdown列表形式输出，只输出摘要：\n\n" + target;
            case "translate" -> "请将以下文本翻译（中英互译），只输出译文：\n\n" + target;
            default          -> "请处理以下内容：\n\n" + target;
        };
    }

    private String resolveOwner(String cid) {
        var principal = connectionManager.getPrincipal(cid);
        return principal != null ? principal.getPrincipalId() : "local-default";
    }
}
```

> **Implementation note:** The `then(Mono.defer(...))` chain above is verbose. After reading the actual `AgentRunHandler` internals, simplify this to a clean reactive chain. The key contract: find/create session → build prompt → call `agentRunHandler.handle()` → return its response (which contains the `runId` the frontend needs).

- [ ] **Step 4: Run test — it must pass**

```bash
./mvnw test -pl . -Dtest=DocumentAiAssistHandlerTest -q 2>&1 | tail -10
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/gateway/rpc/handler/document/DocumentAiAssistHandler.java \
        src/test/java/com/jaguarliu/ai/document/DocumentAiAssistHandlerTest.java
git commit -m "feat(docs): add DocumentAiAssistHandler delegating to agent/run pipeline"
```

---

## Chunk 3: Frontend Foundation

### Task 8: Install TipTap and add types

**Files:**
- Modify: `jaguarclaw-ui/package.json`
- Modify: `jaguarclaw-ui/src/types/index.ts`

- [ ] **Step 1: Install TipTap packages**

```bash
cd /Users/eumenides/Desktop/jaguarliu/core/miniclaw/jaguarclaw-ui
npm install @tiptap/vue-3 @tiptap/starter-kit \
            @tiptap/extension-bubble-menu \
            @tiptap/extension-floating-menu \
            @tiptap/extension-placeholder \
            @tiptap/suggestion
```

- [ ] **Step 2: Verify installation**

```bash
cat package.json | grep tiptap
```

Expected: 6 `@tiptap/` entries in dependencies.

- [ ] **Step 3: Add DocumentNode and Document types to `src/types/index.ts`**

Append to the end of the file:

```typescript
// ─── Document types ───────────────────────────────────────────────────────────

export interface DocumentNode {
  id: string
  parentId: string | null
  title: string
  sortOrder: number
  wordCount: number
  createdAt: string
  updatedAt: string
  children: DocumentNode[]
}

export interface Document extends Omit<DocumentNode, 'children'> {
  content: string   // TipTap JSON string
}
```

- [ ] **Step 4: Type-check**

```bash
npm run type-check 2>&1 | tail -10
```

Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add jaguarclaw-ui/package.json jaguarclaw-ui/package-lock.json \
        jaguarclaw-ui/src/types/index.ts
git commit -m "feat(docs): install TipTap and add document types"
```

---

### Task 9: useDocuments composable

**Files:**
- Create: `jaguarclaw-ui/src/composables/useDocuments.ts`

Follows the exact singleton pattern of `useSchedules.ts`: module-level `ref` state, function returns methods + readonly state.

- [ ] **Step 1: Create `useDocuments.ts`**

```typescript
// jaguarclaw-ui/src/composables/useDocuments.ts
import { ref, readonly } from 'vue'
import { useWebSocket } from './useWebSocket'
import type { Document, DocumentNode } from '@/types'

// ── Module-level singleton state ──────────────────────────────────────────────
const tree = ref<DocumentNode[]>([])
const currentDoc = ref<Document | null>(null)
const loading = ref(false)
const saving = ref(false)
const aiStreaming = ref(false)
const aiStreamContent = ref('')
const error = ref<string | null>(null)

let saveTimer: ReturnType<typeof setTimeout> | null = null
let aiUnsubDelta: (() => void) | null = null
let aiUnsubEnd: (() => void) | null = null

export function useDocuments() {
  const { request, onEvent } = useWebSocket()

  // ── Tree ────────────────────────────────────────────────────────────────────

  async function loadTree() {
    loading.value = true
    error.value = null
    try {
      const result = await request<{ documents: DocumentNode[] }>('document.list')
      tree.value = result.documents
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load documents'
    } finally {
      loading.value = false
    }
  }

  // ── CRUD ────────────────────────────────────────────────────────────────────

  async function loadDocument(id: string) {
    loading.value = true
    error.value = null
    try {
      const doc = await request<Document>('document.get', { id })
      currentDoc.value = doc
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load document'
    } finally {
      loading.value = false
    }
  }

  async function createDocument(title?: string, parentId?: string): Promise<Document> {
    const doc = await request<Document>('document.create', { title: title ?? 'Untitled', parentId })
    await loadTree()
    return doc
  }

  function scheduleSave(id: string, title: string, content: string, wordCount: number) {
    if (saveTimer) clearTimeout(saveTimer)
    saving.value = true
    saveTimer = setTimeout(async () => {
      try {
        await request('document.update', { id, title, content, wordCount })
        // Sync title in tree without full reload
        updateNodeTitle(tree.value, id, title)
      } catch (e) {
        error.value = e instanceof Error ? e.message : 'Save failed'
      } finally {
        saving.value = false
      }
    }, 1500)
  }

  async function deleteDocument(id: string) {
    await request('document.delete', { id })
    if (currentDoc.value?.id === id) currentDoc.value = null
    await loadTree()
  }

  // ── AI assist ───────────────────────────────────────────────────────────────

  async function aiAssist(
    docId: string,
    action: 'continue' | 'optimize' | 'rewrite' | 'summarize' | 'translate',
    selection?: string
  ): Promise<string> {
    // Returns streamRunId so the editor can subscribe to delta events
    aiStreaming.value = true
    aiStreamContent.value = ''

    // Clean up previous subscriptions
    aiUnsubDelta?.(); aiUnsubEnd?.()

    const result = await request<{ runId: string }>('document.ai.assist', { docId, action, selection })
    const streamRunId = result.runId

    aiUnsubDelta = onEvent('assistant.delta', (event: any) => {
      if (event.runId === streamRunId || event.payload?.runId === streamRunId) {
        aiStreamContent.value += (event.payload?.content ?? event.content ?? '')
      }
    })

    aiUnsubEnd = onEvent('lifecycle.end', (event: any) => {
      if (event.runId === streamRunId || event.payload?.runId === streamRunId) {
        aiStreaming.value = false
        aiUnsubDelta?.(); aiUnsubEnd?.()
      }
    })

    return streamRunId
  }

  function stopAiStream() {
    aiStreaming.value = false
    aiStreamContent.value = ''
    aiUnsubDelta?.(); aiUnsubEnd?.()
    aiUnsubDelta = null; aiUnsubEnd = null
  }

  return {
    tree: readonly(tree),
    currentDoc: readonly(currentDoc),
    loading: readonly(loading),
    saving: readonly(saving),
    aiStreaming: readonly(aiStreaming),
    aiStreamContent: readonly(aiStreamContent),
    error: readonly(error),
    loadTree,
    loadDocument,
    createDocument,
    scheduleSave,
    deleteDocument,
    aiAssist,
    stopAiStream,
  }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function updateNodeTitle(nodes: DocumentNode[], id: string, title: string) {
  for (const node of nodes) {
    if (node.id === id) { node.title = title; return }
    if (node.children.length) updateNodeTitle(node.children, id, title)
  }
}
```

- [ ] **Step 2: Type-check**

```bash
cd /Users/eumenides/Desktop/jaguarliu/core/miniclaw/jaguarclaw-ui
npm run type-check 2>&1 | tail -15
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add jaguarclaw-ui/src/composables/useDocuments.ts
git commit -m "feat(docs): add useDocuments composable with CRUD and AI streaming"
```

---

## Chunk 4: Frontend Components

### Task 10: DocumentSidebar

**Files:**
- Create: `jaguarclaw-ui/src/components/documents/DocumentSidebar.vue`

- [ ] **Step 1: Create DocumentSidebar.vue**

```vue
<!-- jaguarclaw-ui/src/components/documents/DocumentSidebar.vue -->
<script setup lang="ts">
import { ref } from 'vue'
import type { DocumentNode } from '@/types'

const props = defineProps<{
  tree: DocumentNode[]
  activeId: string | null
}>()

const emit = defineEmits<{
  select: [id: string]
  create: [parentId?: string]
  delete: [id: string]
}>()

const expandedIds = ref<Set<string>>(new Set())

function toggle(id: string) {
  expandedIds.value.has(id) ? expandedIds.value.delete(id) : expandedIds.value.add(id)
}

const contextMenu = ref<{ x: number; y: number; node: DocumentNode } | null>(null)

function showContext(e: MouseEvent, node: DocumentNode) {
  e.preventDefault()
  contextMenu.value = { x: e.clientX, y: e.clientY, node }
}

function closeContext() { contextMenu.value = null }
</script>

<template>
  <aside class="doc-sidebar" @click="closeContext">
    <div class="doc-sidebar__header">
      <span class="doc-sidebar__title">文档</span>
      <button class="doc-sidebar__new" @click="emit('create')" title="新建页面">＋</button>
    </div>

    <div class="doc-sidebar__tree">
      <DocTreeNode
        v-for="node in tree"
        :key="node.id"
        :node="node"
        :active-id="activeId"
        :expanded-ids="expandedIds"
        @select="emit('select', $event)"
        @toggle="toggle"
        @contextmenu="showContext"
        @create-child="emit('create', $event)"
      />
    </div>

    <!-- Context menu -->
    <div
      v-if="contextMenu"
      class="doc-context-menu"
      :style="{ top: contextMenu.y + 'px', left: contextMenu.x + 'px' }"
    >
      <button @click="emit('create', contextMenu!.node.id); closeContext()">新建子页面</button>
      <button class="danger" @click="emit('delete', contextMenu!.node.id); closeContext()">删除</button>
    </div>
  </aside>
</template>

<style scoped>
.doc-sidebar {
  width: var(--sidebar-width, 260px);
  border-right: var(--border);
  display: flex;
  flex-direction: column;
  background: var(--color-gray-50);
  overflow: hidden;
}
.doc-sidebar__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-3) var(--space-4);
  border-bottom: var(--border);
}
.doc-sidebar__title { font-size: 12px; font-weight: 600; color: var(--color-gray-500); text-transform: uppercase; letter-spacing: 0.05em; }
.doc-sidebar__new { background: none; border: none; cursor: pointer; font-size: 18px; color: var(--color-gray-500); padding: 0 4px; line-height: 1; }
.doc-sidebar__new:hover { color: var(--color-gray-900); }
.doc-sidebar__tree { flex: 1; overflow-y: auto; padding: var(--space-2) 0; }
.doc-context-menu {
  position: fixed; z-index: 100;
  background: var(--color-white); border: var(--border); border-radius: var(--radius-md);
  box-shadow: var(--shadow-md); padding: var(--space-1) 0; min-width: 140px;
}
.doc-context-menu button { display: block; width: 100%; text-align: left; padding: var(--space-2) var(--space-3); font-size: 13px; background: none; border: none; cursor: pointer; }
.doc-context-menu button:hover { background: var(--color-gray-100); }
.doc-context-menu button.danger { color: var(--color-error, #e53e3e); }
</style>
```

- [ ] **Step 2: Create the recursive DocTreeNode component**

```vue
<!-- jaguarclaw-ui/src/components/documents/DocTreeNode.vue -->
<script setup lang="ts">
import type { DocumentNode } from '@/types'

defineProps<{
  node: DocumentNode
  activeId: string | null
  expandedIds: Set<string>
}>()

defineEmits<{
  select: [id: string]
  toggle: [id: string]
  contextmenu: [e: MouseEvent, node: DocumentNode]
  'create-child': [parentId: string]
}>()
</script>

<template>
  <div class="tree-node">
    <div
      class="tree-node__row"
      :class="{ active: node.id === activeId }"
      @click="$emit('select', node.id)"
      @contextmenu="$emit('contextmenu', $event, node)"
    >
      <span
        class="tree-node__arrow"
        :style="{ opacity: node.children.length ? 1 : 0 }"
        @click.stop="$emit('toggle', node.id)"
      >{{ expandedIds.has(node.id) ? '▾' : '▸' }}</span>
      <span class="tree-node__icon">📄</span>
      <span class="tree-node__title">{{ node.title }}</span>
    </div>
    <div v-if="expandedIds.has(node.id) && node.children.length" class="tree-node__children">
      <DocTreeNode
        v-for="child in node.children"
        :key="child.id"
        :node="child"
        :active-id="activeId"
        :expanded-ids="expandedIds"
        @select="$emit('select', $event)"
        @toggle="$emit('toggle', $event)"
        @contextmenu="$emit('contextmenu', $event, $event)"
        @create-child="$emit('create-child', $event)"
      />
    </div>
  </div>
</template>

<style scoped>
.tree-node__row {
  display: flex; align-items: center; gap: 4px;
  padding: 4px 8px; cursor: pointer; border-radius: var(--radius-sm);
  font-size: 13px; color: var(--color-gray-700);
}
.tree-node__row:hover { background: var(--color-gray-100); }
.tree-node__row.active { background: var(--color-gray-200); font-weight: 500; }
.tree-node__arrow { font-size: 10px; color: var(--color-gray-400); width: 12px; cursor: pointer; }
.tree-node__icon { font-size: 12px; }
.tree-node__title { flex: 1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.tree-node__children { padding-left: 16px; }
</style>
```

- [ ] **Step 3: Type-check**

```bash
npm run type-check 2>&1 | tail -10
```

- [ ] **Step 4: Commit**

```bash
git add jaguarclaw-ui/src/components/documents/
git commit -m "feat(docs): add DocumentSidebar and DocTreeNode components"
```

---

### Task 11: DocumentEditor with TipTap

**Files:**
- Create: `jaguarclaw-ui/src/components/documents/DocumentEditor.vue`
- Create: `jaguarclaw-ui/src/components/documents/DocumentBubbleMenu.vue`

- [ ] **Step 1: Create DocumentEditor.vue**

```vue
<!-- jaguarclaw-ui/src/components/documents/DocumentEditor.vue -->
<script setup lang="ts">
import { useEditor, EditorContent, BubbleMenu, FloatingMenu } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Placeholder from '@tiptap/extension-placeholder'
import { watch, onBeforeUnmount, computed } from 'vue'
import type { Document } from '@/types'
import DocumentBubbleMenu from './DocumentBubbleMenu.vue'

const props = defineProps<{
  document: Document | null
  saving: boolean
  aiStreaming: boolean
}>()

const emit = defineEmits<{
  change: [title: string, content: string, wordCount: number]
  aiAction: [action: string, selection?: string]
}>()

const editor = useEditor({
  extensions: [
    StarterKit,
    Placeholder.configure({ placeholder: '开始输入…' }),
  ],
  editorProps: {
    attributes: { class: 'doc-editor__prose' },
  },
  onUpdate({ editor }) {
    if (!props.document) return
    const content  = JSON.stringify(editor.getJSON())
    const wordCount = editor.getText().trim().split(/\s+/).filter(Boolean).length
    emit('change', titleValue.value, content, wordCount)
  },
})

const titleValue = computed({
  get: () => props.document?.title ?? '',
  set: () => {},
})

// Load document content into editor when document changes
watch(() => props.document, (doc) => {
  if (!editor.value || !doc) return
  try {
    const json = JSON.parse(doc.content)
    editor.value.commands.setContent(json, false)
  } catch {
    editor.value.commands.setContent(doc.content || '', false)
  }
}, { immediate: true })

onBeforeUnmount(() => editor.value?.destroy())

function onTitleInput(e: Event) {
  const title = (e.target as HTMLInputElement).value
  if (!props.document) return
  const content  = editor.value ? JSON.stringify(editor.value.getJSON()) : props.document.content
  const wordCount = editor.value?.getText().trim().split(/\s+/).filter(Boolean).length ?? 0
  emit('change', title, content, wordCount)
}

function handleAiAction(action: string) {
  if (!editor.value) return
  const { from, to, empty } = editor.value.state.selection
  const selection = empty ? undefined : editor.value.state.doc.textBetween(from, to)
  emit('aiAction', action, selection)
}
</script>

<template>
  <div class="doc-editor" v-if="document">
    <!-- Toolbar -->
    <div class="doc-editor__toolbar">
      <input
        class="doc-editor__title"
        :value="document.title"
        placeholder="Untitled"
        @input="onTitleInput"
      />
      <div class="doc-editor__actions">
        <button @click="handleAiAction('continue')" :disabled="aiStreaming">续写</button>
        <button @click="handleAiAction('optimize')" :disabled="aiStreaming">润色全文</button>
        <button @click="handleAiAction('summarize')" :disabled="aiStreaming">总结</button>
        <span class="doc-editor__save-status">
          {{ saving ? '保存中…' : '已保存' }}
        </span>
      </div>
    </div>

    <!-- BubbleMenu (shown on text selection) -->
    <BubbleMenu v-if="editor" :editor="editor" :tippy-options="{ duration: 100 }">
      <DocumentBubbleMenu :ai-streaming="aiStreaming" @action="handleAiAction" />
    </BubbleMenu>

    <!-- Editor body -->
    <div class="doc-editor__body">
      <EditorContent :editor="editor" />
    </div>
  </div>
  <div v-else class="doc-editor__empty">
    <p>从左侧选择一个文档，或点击「＋」新建。</p>
  </div>
</template>

<style scoped>
.doc-editor {
  flex: 1; display: flex; flex-direction: column; overflow: hidden;
  background: var(--color-white);
}
.doc-editor__toolbar {
  display: flex; align-items: center; gap: var(--space-3);
  padding: var(--space-3) var(--space-6); border-bottom: var(--border);
  flex-shrink: 0;
}
.doc-editor__title {
  flex: 1; font-size: 18px; font-weight: 600; border: none; outline: none;
  background: transparent; color: var(--color-gray-900);
}
.doc-editor__actions { display: flex; align-items: center; gap: var(--space-2); }
.doc-editor__actions button {
  padding: var(--space-1) var(--space-3); font-size: 12px;
  border: var(--border); border-radius: var(--radius-md);
  background: var(--color-white); cursor: pointer;
}
.doc-editor__actions button:hover:not(:disabled) { background: var(--color-gray-100); }
.doc-editor__actions button:disabled { opacity: 0.5; cursor: default; }
.doc-editor__save-status { font-size: 11px; color: var(--color-gray-400); }
.doc-editor__body {
  flex: 1; overflow-y: auto;
  padding: var(--space-6) var(--space-8);
  max-width: 760px; margin: 0 auto; width: 100%;
}
.doc-editor__empty {
  flex: 1; display: flex; align-items: center; justify-content: center;
  color: var(--color-gray-400); font-size: 14px;
}
/* TipTap prose styles */
:global(.doc-editor__prose) {
  outline: none; font-family: var(--font-ui); font-size: 15px; line-height: 1.7;
  color: var(--color-gray-900); min-height: 400px;
}
:global(.doc-editor__prose p) { margin: 0 0 var(--space-2); }
:global(.doc-editor__prose h1) { font-size: 26px; font-weight: 700; margin: var(--space-4) 0 var(--space-2); }
:global(.doc-editor__prose h2) { font-size: 20px; font-weight: 600; margin: var(--space-3) 0 var(--space-2); }
:global(.doc-editor__prose h3) { font-size: 16px; font-weight: 600; margin: var(--space-2) 0 var(--space-1); }
:global(.doc-editor__prose ul, .doc-editor__prose ol) { padding-left: var(--space-5); margin: var(--space-2) 0; }
:global(.doc-editor__prose code) { background: var(--color-gray-100); padding: 1px 5px; border-radius: 3px; font-family: var(--font-mono); font-size: 13px; }
:global(.doc-editor__prose pre) { background: var(--color-gray-900); color: var(--color-gray-100); padding: var(--space-4); border-radius: var(--radius-md); overflow-x: auto; }
:global(.doc-editor__prose blockquote) { border-left: 3px solid var(--color-gray-300); padding-left: var(--space-3); color: var(--color-gray-500); }
:global(.doc-editor__prose .is-editor-empty:first-child::before) {
  content: attr(data-placeholder); color: var(--color-gray-400); float: left; pointer-events: none; height: 0;
}
</style>
```

- [ ] **Step 2: Create DocumentBubbleMenu.vue**

```vue
<!-- jaguarclaw-ui/src/components/documents/DocumentBubbleMenu.vue -->
<script setup lang="ts">
defineProps<{ aiStreaming: boolean }>()
defineEmits<{ action: [action: string] }>()
</script>

<template>
  <div class="bubble-menu">
    <button :disabled="aiStreaming" @click="$emit('action', 'rewrite')">改写</button>
    <button :disabled="aiStreaming" @click="$emit('action', 'optimize')">优化</button>
    <button :disabled="aiStreaming" @click="$emit('action', 'translate')">翻译</button>
    <button :disabled="aiStreaming" @click="$emit('action', 'continue')">续写</button>
  </div>
</template>

<style scoped>
.bubble-menu {
  display: flex; gap: 2px;
  background: var(--color-gray-900); border-radius: var(--radius-md);
  padding: 4px; box-shadow: var(--shadow-md);
}
.bubble-menu button {
  padding: 4px 10px; font-size: 12px; color: var(--color-white);
  background: transparent; border: none; border-radius: var(--radius-sm);
  cursor: pointer;
}
.bubble-menu button:hover:not(:disabled) { background: rgba(255,255,255,0.15); }
.bubble-menu button:disabled { opacity: 0.4; cursor: default; }
</style>
```

- [ ] **Step 3: Type-check**

```bash
npm run type-check 2>&1 | tail -10
```

- [ ] **Step 4: Commit**

```bash
git add jaguarclaw-ui/src/components/documents/
git commit -m "feat(docs): add DocumentEditor with TipTap and BubbleMenu"
```

---

### Task 12: DocumentAiIndicator

**Files:**
- Create: `jaguarclaw-ui/src/components/documents/DocumentAiIndicator.vue`

Shown at the bottom of the editor during/after AI streaming. Lets user keep or discard the inserted text.

- [ ] **Step 1: Create DocumentAiIndicator.vue**

```vue
<!-- jaguarclaw-ui/src/components/documents/DocumentAiIndicator.vue -->
<script setup lang="ts">
defineProps<{ streaming: boolean }>()
defineEmits<{ keep: []; discard: [] }>()
</script>

<template>
  <div class="ai-indicator">
    <span v-if="streaming" class="ai-indicator__label">
      <span class="ai-indicator__dot" />
      AI 正在写作…
    </span>
    <span v-else class="ai-indicator__label">AI 写作完成</span>
    <div class="ai-indicator__actions" v-if="!streaming">
      <button class="keep" @click="$emit('keep')">✓ 保留</button>
      <button class="discard" @click="$emit('discard')">↩ 撤销</button>
    </div>
  </div>
</template>

<style scoped>
.ai-indicator {
  display: flex; align-items: center; justify-content: space-between;
  padding: var(--space-2) var(--space-4);
  background: var(--color-gray-50); border-top: var(--border);
  font-size: 12px; color: var(--color-gray-600);
  flex-shrink: 0;
}
.ai-indicator__label { display: flex; align-items: center; gap: var(--space-2); }
.ai-indicator__dot {
  width: 6px; height: 6px; border-radius: 50%;
  background: var(--color-primary, #4f46e5);
  animation: pulse 1s infinite;
}
@keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: 0.3; } }
.ai-indicator__actions { display: flex; gap: var(--space-2); }
.ai-indicator__actions button {
  padding: var(--space-1) var(--space-3); font-size: 12px;
  border-radius: var(--radius-md); border: var(--border); cursor: pointer;
}
.keep { background: var(--color-gray-900); color: white; border-color: transparent; }
.discard { background: white; color: var(--color-gray-700); }
</style>
```

- [ ] **Step 2: Commit**

```bash
git add jaguarclaw-ui/src/components/documents/DocumentAiIndicator.vue
git commit -m "feat(docs): add DocumentAiIndicator component"
```

---

## Chunk 5: View + Navigation Wiring

### Task 13: DocumentView (orchestrator)

**Files:**
- Create: `jaguarclaw-ui/src/views/DocumentView.vue`

The view wires `useDocuments()` to child components and handles the AI inline insertion flow.

- [ ] **Step 1: Create DocumentView.vue**

```vue
<!-- jaguarclaw-ui/src/views/DocumentView.vue -->
<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useDocuments } from '@/composables/useDocuments'
import DocumentSidebar from '@/components/documents/DocumentSidebar.vue'
import DocumentEditor from '@/components/documents/DocumentEditor.vue'
import DocumentAiIndicator from '@/components/documents/DocumentAiIndicator.vue'

const props = defineProps<{ id?: string }>()
const router = useRouter()

const {
  tree, currentDoc, loading, saving, aiStreaming, aiStreamContent, error,
  loadTree, loadDocument, createDocument, scheduleSave, deleteDocument,
  aiAssist, stopAiStream,
} = useDocuments()

// Snapshot for undo support
const editorRef = ref<InstanceType<typeof DocumentEditor> | null>(null)
let preAiSnapshot: any = null
let showAiIndicator = ref(false)

onMounted(async () => {
  await loadTree()
  if (props.id) await loadDocument(props.id)
})

watch(() => props.id, async (id) => {
  if (id) await loadDocument(id)
})

async function onSelect(id: string) {
  router.push(`/documents/${id}`)
}

async function onCreate(parentId?: string) {
  const doc = await createDocument('Untitled', parentId)
  router.push(`/documents/${doc.id}`)
}

async function onDelete(id: string) {
  await deleteDocument(id)
  if (props.id === id) router.push('/documents')
}

function onChange(title: string, content: string, wordCount: number) {
  if (!currentDoc.value) return
  scheduleSave(currentDoc.value.id, title, content, wordCount)
}

async function onAiAction(action: string, selection?: string) {
  if (!currentDoc.value) return
  // Capture snapshot before AI modifies the editor
  // The editor component exposes getSnapshot via ref — see Note below.
  showAiIndicator.value = true
  await aiAssist(currentDoc.value.id, action as any, selection)
}

function onAiKeep() {
  showAiIndicator.value = false
  preAiSnapshot = null
  stopAiStream()
}

function onAiDiscard() {
  // Editor restore handled by emitting 'restore' — editor watches for snapshot signal
  showAiIndicator.value = false
  stopAiStream()
  // Force-reload document from server to discard AI content
  if (currentDoc.value) loadDocument(currentDoc.value.id)
}
</script>

<template>
  <div class="document-view">
    <DocumentSidebar
      :tree="tree"
      :active-id="id ?? null"
      @select="onSelect"
      @create="onCreate"
      @delete="onDelete"
    />

    <div class="document-view__main">
      <DocumentEditor
        ref="editorRef"
        :document="currentDoc"
        :saving="saving"
        :ai-streaming="aiStreaming"
        @change="onChange"
        @ai-action="onAiAction"
      />

      <DocumentAiIndicator
        v-if="showAiIndicator"
        :streaming="aiStreaming"
        @keep="onAiKeep"
        @discard="onAiDiscard"
      />
    </div>
  </div>
</template>

<style scoped>
.document-view {
  display: flex; height: 100%; overflow: hidden;
}
.document-view__main {
  flex: 1; display: flex; flex-direction: column; overflow: hidden;
}
</style>
```

> **Note on discard/undo:** The simplest reliable undo for AI insertions is reloading the document from the server (`loadDocument`). This avoids complex TipTap history management. For UX improvements (no server round-trip), a future iteration can capture `editor.getJSON()` before AI insertion and restore with `editor.commands.setContent(snapshot)`.

- [ ] **Step 2: Type-check**

```bash
npm run type-check 2>&1 | tail -10
```

- [ ] **Step 3: Commit**

```bash
git add jaguarclaw-ui/src/views/DocumentView.vue
git commit -m "feat(docs): add DocumentView orchestrator"
```

---

### Task 14: Router + ModeSwitcher

**Files:**
- Modify: `jaguarclaw-ui/src/router/index.ts`
- Modify: `jaguarclaw-ui/src/components/layout/ModeSwitcher.vue`

- [ ] **Step 1: Add document routes to router**

Open `src/router/index.ts` and add two routes after the existing `settings` route:

```typescript
import DocumentView from '@/views/DocumentView.vue'

// Add inside the routes array:
{
  path: '/documents',
  name: 'documents',
  component: DocumentView,
},
{
  path: '/documents/:id',
  name: 'document-detail',
  component: DocumentView,
  props: true,
},
```

- [ ] **Step 2: Update ModeSwitcher to add documents mode**

Open `src/components/layout/ModeSwitcher.vue`. Current `currentMode` returns `'workspace' | 'settings'`. Update the full file:

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const currentMode = computed(() => {
  if (route.path.startsWith('/settings')) return 'settings'
  if (route.path.startsWith('/documents')) return 'documents'
  return 'workspace'
})

function switchTo(mode: 'workspace' | 'settings' | 'documents') {
  if (mode === 'workspace')  router.push('/')
  else if (mode === 'settings') router.push('/settings')
  else router.push('/documents')
}
</script>

<template>
  <div class="mode-switcher">
    <button class="mode-btn" :class="{ active: currentMode === 'workspace' }"
            @click="switchTo('workspace')" title="Workspace">
      <span class="icon">&#9671;</span>
    </button>
    <button class="mode-btn" :class="{ active: currentMode === 'documents' }"
            @click="switchTo('documents')" title="Documents">
      <span class="icon">&#9783;</span>
    </button>
    <button class="mode-btn" :class="{ active: currentMode === 'settings' }"
            @click="switchTo('settings')" title="Settings">
      <span class="icon">&#9881;</span>
    </button>
  </div>
</template>

<style scoped>
.mode-switcher { display: flex; gap: 4px; }
.mode-btn {
  width: 28px; height: 28px; display: flex; align-items: center; justify-content: center;
  border: var(--border); background: var(--color-white); font-size: 14px;
  cursor: pointer; border-radius: var(--radius-md);
  transition: all var(--duration-fast) var(--ease-in-out);
}
.mode-btn:hover { background: var(--color-gray-bg); }
.mode-btn.active { background: var(--color-black); color: var(--color-white); }
.icon { line-height: 1; }
</style>
```

- [ ] **Step 3: Type-check**

```bash
npm run type-check 2>&1 | tail -10
```

Expected: no errors.

- [ ] **Step 4: Run the frontend dev server and verify navigation**

```bash
cd /Users/eumenides/Desktop/jaguarliu/core/miniclaw/jaguarclaw-ui
npm run dev
```

Open http://localhost:5173. Verify:
- Three buttons appear in ModeSwitcher
- Clicking the document icon navigates to `/documents`
- DocumentSidebar and empty editor state render without errors

- [ ] **Step 5: Commit**

```bash
git add jaguarclaw-ui/src/router/index.ts \
        jaguarclaw-ui/src/components/layout/ModeSwitcher.vue
git commit -m "feat(docs): wire document routes and add mode button to ModeSwitcher"
```

---

## End-to-end Acceptance Checklist

Run through these manually with both backend and frontend running (`./mvnw spring-boot:run` + `npm run dev`):

- [ ] Flyway V25 applied cleanly — `documents` table exists
- [ ] Click `+` in sidebar → new document appears in tree, editor opens with "Untitled"
- [ ] Type in editor → "保存中…" appears → "已保存" returns (1.5s autosave)
- [ ] Refresh page → document content persists
- [ ] Create child page (right-click → 新建子页面) → appears nested under parent
- [ ] Delete a document → removed from sidebar
- [ ] Select text → BubbleMenu appears with 改写/优化/翻译/续写 buttons
- [ ] Click 优化 → AI streams text inline into editor (assistant.delta events arrive)
- [ ] After streaming, AI indicator shows → click 撤销 → content restored
- [ ] Click 润色全文 in toolbar → entire doc content replaced by AI stream
- [ ] Chat sidebar (workspace view) shows NO document sessions
