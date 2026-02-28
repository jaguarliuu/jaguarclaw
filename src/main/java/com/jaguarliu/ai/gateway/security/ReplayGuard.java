package com.jaguarliu.ai.gateway.security;

import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RPC 重放防护（timestamp + nonce + idempotencyKey）
 */
@Component
public class ReplayGuard {

    private final SecurityProperties securityProperties;
    private final Map<String, Long> nonceCache = new ConcurrentHashMap<>();
    private final Map<String, Long> idempotencyCache = new ConcurrentHashMap<>();

    public ReplayGuard(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    public ValidationResult validate(RpcRequest request, boolean strict) {
        if (!securityProperties.getReplay().isEnabled()) {
            return ValidationResult.allow();
        }

        cleanupExpired();

        if (request == null) {
            return ValidationResult.deny("INVALID_REQUEST", "Request is null");
        }

        if (!strict && request.getTimestamp() == null && request.getNonce() == null) {
            return ValidationResult.allow();
        }

        Long timestamp = request.getTimestamp();
        String nonce = request.getNonce();
        if (timestamp == null || nonce == null || nonce.isBlank()) {
            // 兼容旧客户端：未传 replay 字段时先放行
            return ValidationResult.allow();
        }

        long now = System.currentTimeMillis();
        long windowMs = securityProperties.getReplay().getTimestampWindowSeconds() * 1000L;
        if (Math.abs(now - timestamp) > windowMs) {
            return ValidationResult.deny("REQUEST_EXPIRED", "Request timestamp is outside allowed window");
        }

        long ttlMs = securityProperties.getReplay().getNonceTtlSeconds() * 1000L;
        long nonceExpireAt = now + ttlMs;
        Long existingNonce = nonceCache.putIfAbsent(request.getMethod() + ":" + nonce, nonceExpireAt);
        if (existingNonce != null && existingNonce > now) {
            return ValidationResult.deny("REPLAY_DETECTED", "Duplicate nonce detected");
        }

        String idempotencyKey = request.getIdempotencyKey();
        if (strict && idempotencyKey != null && !idempotencyKey.isBlank()) {
            long idemExpireAt = now + ttlMs;
            Long existingIdempotency = idempotencyCache.putIfAbsent(
                    request.getMethod() + ":" + idempotencyKey,
                    idemExpireAt
            );
            if (existingIdempotency != null && existingIdempotency > now) {
                return ValidationResult.deny("DUPLICATE_REQUEST", "Duplicate idempotency key");
            }
        }

        return ValidationResult.allow();
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        nonceCache.entrySet().removeIf(entry -> entry.getValue() <= now);
        idempotencyCache.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    public record ValidationResult(boolean allowed, String code, String message) {
        public static ValidationResult allow() {
            return new ValidationResult(true, null, null);
        }

        public static ValidationResult deny(String code, String message) {
            return new ValidationResult(false, code, message);
        }
    }
}
