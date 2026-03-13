package com.jaguarliu.ai.im.dto;
import lombok.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ImConversationDto {
    private String id;
    private String displayName;
    private String lastMsg;
    private String lastMsgAt;
    private int unreadCount;
}
