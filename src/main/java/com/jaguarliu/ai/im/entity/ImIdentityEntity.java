package com.jaguarliu.ai.im.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "im_identity")
public class ImIdentityEntity {
    @Id
    private String nodeId;

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String publicKeyEd25519;   // Base64 DER

    @Column(nullable = false, columnDefinition = "TEXT")
    private String publicKeyX25519;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String privateKeyEd25519;  // Base64 PKCS#8

    @Column(nullable = false, columnDefinition = "TEXT")
    private String privateKeyX25519;

    @Column(columnDefinition = "TEXT")
    private String redisUrl;

    @Column(columnDefinition = "TEXT")
    private String redisPassword;

    @Builder.Default
    private String avatarStyle = "thumbs";

    @Column(columnDefinition = "TEXT")
    private String avatarSeed;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
