package com.jaguarliu.ai.gateway.security;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地桌面模式 token 服务（内存实现）
 */
@Service
@RequiredArgsConstructor
public class AuthTokenService {

    private final SecurityProperties securityProperties;

    private final Map<String, TokenRecord> accessTokens = new ConcurrentHashMap<>();
    private final Map<String, TokenRecord> refreshTokens = new ConcurrentHashMap<>();

    public TokenPair issueTokens(String principalId, List<String> roles) {
        cleanupExpired();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime accessExpiresAt = now.plusMinutes(securityProperties.getAuth().getAccessTokenMinutes());
        LocalDateTime refreshExpiresAt = now.plusDays(securityProperties.getAuth().getRefreshTokenDays());

        String accessToken = "atk_" + randomToken();
        String refreshToken = "rtk_" + randomToken();

        TokenRecord accessRecord = TokenRecord.builder()
                .principalId(principalId)
                .roles(roles)
                .expiresAt(accessExpiresAt)
                .build();
        TokenRecord refreshRecord = TokenRecord.builder()
                .principalId(principalId)
                .roles(roles)
                .expiresAt(refreshExpiresAt)
                .build();

        accessTokens.put(accessToken, accessRecord);
        refreshTokens.put(refreshToken, refreshRecord);

        return TokenPair.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .principalId(principalId)
                .roles(roles)
                .accessExpiresAt(accessExpiresAt)
                .refreshExpiresAt(refreshExpiresAt)
                .build();
    }

    public Optional<TokenPair> refresh(String refreshToken) {
        cleanupExpired();

        TokenRecord record = refreshTokens.remove(refreshToken);
        if (record == null || record.getExpiresAt().isBefore(LocalDateTime.now())) {
            return Optional.empty();
        }

        return Optional.of(issueTokens(record.getPrincipalId(), record.getRoles()));
    }

    public Optional<TokenRecord> verifyAccessToken(String accessToken) {
        cleanupExpired();
        TokenRecord record = accessTokens.get(accessToken);
        if (record == null || record.getExpiresAt().isBefore(LocalDateTime.now())) {
            accessTokens.remove(accessToken);
            return Optional.empty();
        }
        return Optional.of(record);
    }

    private void cleanupExpired() {
        LocalDateTime now = LocalDateTime.now();
        accessTokens.entrySet().removeIf(entry -> entry.getValue().getExpiresAt().isBefore(now));
        refreshTokens.entrySet().removeIf(entry -> entry.getValue().getExpiresAt().isBefore(now));
    }

    private String randomToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Data
    @Builder
    public static class TokenRecord {
        private String principalId;
        private List<String> roles;
        private LocalDateTime expiresAt;
    }

    @Data
    @Builder
    public static class TokenPair {
        private String accessToken;
        private String refreshToken;
        private String principalId;
        private List<String> roles;
        private LocalDateTime accessExpiresAt;
        private LocalDateTime refreshExpiresAt;
    }
}

