package com.jaguarliu.ai.gateway.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RpcAuthorizationService Tests")
class RpcAuthorizationServiceTest {

    private final RpcAuthorizationService service = new RpcAuthorizationService();

    @Test
    @DisplayName("PUBLIC 方法在无主体时可访问")
    void publicMethodAllowedWithoutPrincipal() {
        assertTrue(service.isAuthorized(null, "ping"));
    }

    @Test
    @DisplayName("未认证主体不能访问 WRITE 方法")
    void writeDeniedWithoutPrincipal() {
        assertFalse(service.isAuthorized(null, "session.create"));
    }

    @Test
    @DisplayName("local_admin 可访问 DANGEROUS 方法")
    void localAdminCanAccessDangerous() {
        ConnectionPrincipal principal = ConnectionPrincipal.builder()
                .principalId("device-1")
                .roles(List.of("local_admin"))
                .build();
        assertTrue(service.isAuthorized(principal, "tool.execute"));
    }

    @Test
    @DisplayName("local_limited 不能访问 CONFIG 方法")
    void localLimitedCannotAccessConfig() {
        ConnectionPrincipal principal = ConnectionPrincipal.builder()
                .principalId("device-2")
                .roles(List.of("local_limited"))
                .build();
        assertFalse(service.isAuthorized(principal, "llm.config.save"));
    }

    @Test
    @DisplayName("未知方法默认 WRITE")
    void unknownMethodDefaultsToWrite() {
        assertEquals(RpcPermission.WRITE, service.resolveRequiredPermission("unknown.method"));
    }
}

