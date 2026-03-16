package com.jaguarliu.ai.im.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ImMessageDto {
    private String id;
    private String conversationId;
    private String senderNodeId;
    @JsonProperty("isMe")
    private boolean isMe;
    private String type;
    private String content;
    private String createdAt;
    private String status;
    // File attachment fields (non-null when type = IMAGE or FILE)
    private String fileUrl;
    private String fileName;
    private String mimeType;
    private Long fileSize;
}
