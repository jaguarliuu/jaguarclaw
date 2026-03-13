package com.jaguarliu.ai.im.dto;
import lombok.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ImContactDto {
    private String nodeId;
    private String displayName;
    private String pairedAt;
    private String status;
}
