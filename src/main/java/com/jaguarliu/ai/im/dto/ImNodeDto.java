package com.jaguarliu.ai.im.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImNodeDto {
    private String nodeId;
    private String displayName;
    private String publicKeyEd25519;
    private String publicKeyX25519;
    private Long lastSeen;
    private String avatarStyle;
    private String avatarSeed;
}
