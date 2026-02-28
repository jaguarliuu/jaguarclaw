package com.jaguarliu.ai.gateway.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 连接主体信息（设备身份）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionPrincipal {

    /**
     * 主体 ID（桌面端默认等于 deviceId）
     */
    private String principalId;

    /**
     * 角色列表
     */
    private List<String> roles;
}

