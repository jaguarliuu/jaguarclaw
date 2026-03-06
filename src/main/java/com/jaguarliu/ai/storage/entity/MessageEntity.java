package com.jaguarliu.ai.storage.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageEntity {

    @Id
    private String id;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "run_id")
    private String runId;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "owner_principal_id")
    private String ownerPrincipalId;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (ownerPrincipalId == null || ownerPrincipalId.isBlank()) {
            ownerPrincipalId = "local-default";
        }
        createdAt = LocalDateTime.now();
    }
}
