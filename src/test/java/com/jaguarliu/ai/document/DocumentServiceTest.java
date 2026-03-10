package com.jaguarliu.ai.document;

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
        when(documentRepository.findRoots("u1")).thenReturn(List.of(root));
        when(documentRepository.findByParentIdOrderBySortOrderAsc("r1")).thenReturn(List.of(child));
        when(documentRepository.findByParentIdOrderBySortOrderAsc("c1")).thenReturn(List.of());

        var tree = documentService.getTree("u1");

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).id()).isEqualTo("r1");
        assertThat(tree.get(0).children()).hasSize(1);
        assertThat(tree.get(0).children().get(0).id()).isEqualTo("c1");
    }
}
