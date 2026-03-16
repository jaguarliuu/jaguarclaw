package com.jaguarliu.ai.im.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "im_conversations")
public class ImConversationEntity {
    @Id
    private String id;  // peer nodeId

    private String displayName;
    private String lastMsg;
    private LocalDateTime lastMsgAt;

    @Column(nullable = false)
    @Builder.Default
    private int unreadCount = 0;
}
