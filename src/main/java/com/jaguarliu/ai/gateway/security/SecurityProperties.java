package com.jaguarliu.ai.gateway.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * WebSocket 安全配置（桌面端本地模式）
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {

    /**
     * 安全模式：
     * - local_desktop: Win/Mac 本地桌面模式（默认）
     * - strict_pairing: 预留给 Linux/公网严格配对模式
     */
    private String mode = "local_desktop";

    /**
     * WebSocket 相关安全配置
     */
    private WsConfig ws = new WsConfig();

    /**
     * Token 生命周期配置
     */
    private AuthConfig auth = new AuthConfig();

    /**
     * 限流配置
     */
    private RateLimitConfig rateLimit = new RateLimitConfig();

    /**
     * 防重放配置
     */
    private ReplayConfig replay = new ReplayConfig();

    @Data
    public static class WsConfig {
        /**
         * 是否仅允许本地连接做 bootstrap
         */
        private boolean localOnlyBootstrap = true;

        /**
         * 未认证连接允许调用的方法
         */
        private List<String> allowAnonymousMethods = List.of(
                "ping",
                "auth.local.bootstrap",
                "auth.refresh"
        );
    }

    @Data
    public static class AuthConfig {
        /**
         * access token 有效期（分钟）
         */
        private int accessTokenMinutes = 30;

        /**
         * refresh token 有效期（天）
         */
        private int refreshTokenDays = 30;
    }

    @Data
    public static class RateLimitConfig {
        /**
         * 每 IP 每分钟最大连接数
         */
        private int connectionPerMinutePerIp = 20;

        /**
         * 每连接每分钟最大消息数
         */
        private int messagePerMinutePerConnection = 60;

        /**
         * 每主体每日最大 token 预算
         */
        private int tokenPerDayPerPrincipal = 1_000_000;
    }

    @Data
    public static class ReplayConfig {
        /**
         * 是否启用防重放
         */
        private boolean enabled = true;

        /**
         * 允许的请求时间窗口（秒）
         */
        private int timestampWindowSeconds = 10;

        /**
         * nonce 过期时间（秒）
         */
        private int nonceTtlSeconds = 60;
    }
}

