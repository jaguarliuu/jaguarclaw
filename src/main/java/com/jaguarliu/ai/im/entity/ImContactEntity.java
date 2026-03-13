package com.jaguarliu.ai.im.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "im_contacts")
public class ImContactEntity {
    @Id
    private String nodeId;

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String publicKeyEd25519;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String publicKeyX25519;

    @Column(nullable = false)
    private LocalDateTime pairedAt;

    @Column(nullable = false)
    @Builder.Default
    private String status = "active";  // "active" | "blocked"
}
