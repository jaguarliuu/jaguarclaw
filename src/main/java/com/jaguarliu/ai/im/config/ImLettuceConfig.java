package com.jaguarliu.ai.im.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the Lettuce RedisClient lifecycle.
 * Connection is lazy — only created when a Redis URL is configured.
 * This avoids Spring Boot Redis autoconfigure and startup failures when Redis is not set.
 */
@Slf4j
@Component
public class ImLettuceConfig {

    private final AtomicReference<RedisClient> clientRef = new AtomicReference<>();
    private volatile String currentUrl;
    private volatile String currentPassword;

    /** Call after user saves IM settings with a Redis URL */
    public synchronized void configure(String redisUrl, String redisPassword) {
        shutdown();
        if (redisUrl == null || redisUrl.isBlank()) return;

        try {
            RedisURI baseUri = RedisURI.create(redisUrl);
            RedisURI.Builder builder = RedisURI.builder(baseUri)
                .withTimeout(Duration.ofSeconds(5));
            if (redisPassword != null && !redisPassword.isBlank()) {
                builder.withPassword(redisPassword.toCharArray());
            }
            clientRef.set(RedisClient.create(builder.build()));
            currentUrl = redisUrl;
            currentPassword = redisPassword;
            log.info("[IM] Redis client configured: {}", redisUrl);
        } catch (Exception e) {
            log.error("[IM] Failed to configure Redis client", e);
        }
    }

    public Optional<RedisClient> getClient() {
        return Optional.ofNullable(clientRef.get());
    }

    public synchronized void shutdown() {
        RedisClient old = clientRef.getAndSet(null);
        if (old != null) {
            try { old.shutdown(); } catch (Exception ignored) {}
            log.info("[IM] Redis client shutdown");
        }
    }

    public boolean isConfigured() {
        return clientRef.get() != null;
    }
}
