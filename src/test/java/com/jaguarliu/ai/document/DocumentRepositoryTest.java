package com.jaguarliu.ai.document;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
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
