package com.jaguarliu.ai.gateway.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SecurityProperties Tests")
class SecurityPropertiesTest {

    @Test
    @DisplayName("默认模式为 local_desktop")
    void defaultMode() {
        SecurityProperties properties = new SecurityProperties();
        assertEquals("local_desktop", properties.getMode());
    }

    @Test
    @DisplayName("默认匿名方法白名单包含 bootstrap")
    void defaultAllowAnonymousMethods() {
        SecurityProperties properties = new SecurityProperties();
        assertTrue(properties.getWs().getAllowAnonymousMethods().contains("auth.local.bootstrap"));
        assertTrue(properties.getWs().getAllowAnonymousMethods().contains("auth.refresh"));
        assertTrue(properties.getWs().getAllowAnonymousMethods().contains("ping"));
    }

    @Test
    @DisplayName("默认限流参数正确")
    void defaultRateLimit() {
        SecurityProperties properties = new SecurityProperties();
        assertEquals(20, properties.getRateLimit().getConnectionPerMinutePerIp());
        assertEquals(60, properties.getRateLimit().getMessagePerMinutePerConnection());
        assertEquals(1_000_000, properties.getRateLimit().getTokenPerDayPerPrincipal());
    }
}

