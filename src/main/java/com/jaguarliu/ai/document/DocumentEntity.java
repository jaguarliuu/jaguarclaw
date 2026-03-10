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
