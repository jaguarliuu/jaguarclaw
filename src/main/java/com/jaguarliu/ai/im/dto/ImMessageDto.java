package com.jaguarliu.ai.im.dto;
import lombok.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ImMessageDto {
    private String id;
    private String conversationId;
    private String senderNodeId;
    private boolean isMe;
    private String type;
    private String content;
    private String createdAt;
    private String status;
}
