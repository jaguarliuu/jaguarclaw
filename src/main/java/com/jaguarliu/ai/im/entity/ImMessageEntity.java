package com.jaguarliu.ai.im.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "im_messages")
public class ImMessageEntity {
    @Id
    private String id;  // UUID from sender

    @Column(nullable = false)
    private String conversationId;

    @Column(nullable = false)
    private String senderNodeId;

    @Column(nullable = false)
    private String type;  // TEXT | IMAGE | FILE | AGENT_MESSAGE

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;  // 解密后明文 JSON

    private String localFilePath;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private String status;  // sent | delivered | failed
}
