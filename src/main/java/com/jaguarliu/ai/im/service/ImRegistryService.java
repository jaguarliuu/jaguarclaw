package com.jaguarliu.ai.im.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.im.config.ImLettuceConfig;
import com.jaguarliu.ai.im.dto.ImNodeDto;
import com.jaguarliu.ai.im.entity.ImIdentityEntity;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImRegistryService {

    private static final long NODE_TTL_SECONDS = 60;
    private final ImLettuceConfig lettuceConfig;
    private final ImIdentityService identityService;
    private final ObjectMapper objectMapper;

    private static final String NODE_KEY_PREFIX = "im:nodes:";

    /** Register this node. Called after Redis is configured. */
    public void registerSelf() {
        lettuceConfig.getClient().ifPresent(client -> {
            try (StatefulRedisConnection<String, String> conn = client.connect()) {
                RedisCommands<String, String> cmds = conn.sync();
                ImIdentityEntity identity = identityService.getCached();
                String nodeJson = objectMapper.writeValueAsString(buildSelfDto(identity));
                cmds.setex(NODE_KEY_PREFIX + identity.getNodeId(), NODE_TTL_SECONDS, nodeJson);
                log.info("[IM] Registered self in Redis: nodeId={}", identity.getNodeId());
            } catch (Exception e) {
                log.warn("[IM] Failed to register self in Redis", e);
            }
        });
    }

    /** Heartbeat: refresh TTL every 30s to stay visible */
    @Scheduled(fixedDelay = 30_000)
    public void heartbeat() {
        if (!lettuceConfig.isConfigured()) return;
        registerSelf();
    }

    /** Scan all im:nodes:* keys and return online peers (excluding self) */
    public List<ImNodeDto> listOnlineNodes() {
        if (!lettuceConfig.isConfigured()) return List.of();

        String selfNodeId = identityService.getCached().getNodeId();
        List<ImNodeDto> result = new ArrayList<>();

        lettuceConfig.getClient().ifPresent(client -> {
            try (StatefulRedisConnection<String, String> conn = client.connect()) {
                RedisCommands<String, String> cmds = conn.sync();
                List<String> keys = cmds.keys(NODE_KEY_PREFIX + "*");
                for (String key : keys) {
                    String json = cmds.get(key);
                    if (json == null) continue;
                    ImNodeDto node = objectMapper.readValue(json, ImNodeDto.class);
                    if (!node.getNodeId().equals(selfNodeId)) {
                        result.add(node);
                    }
                }
            } catch (Exception e) {
                log.warn("[IM] Failed to list online nodes", e);
            }
        });

        return result;
    }

    /** Get a single node by nodeId from the registry */
    public Optional<ImNodeDto> getNode(String nodeId) {
        if (!lettuceConfig.isConfigured()) return Optional.empty();
        try (StatefulRedisConnection<String, String> conn = lettuceConfig.getClient().get().connect()) {
            String json = conn.sync().get(NODE_KEY_PREFIX + nodeId);
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, ImNodeDto.class));
        } catch (Exception e) {
            log.warn("[IM] Failed to get node {}", nodeId, e);
            return Optional.empty();
        }
    }

    private ImNodeDto buildSelfDto(ImIdentityEntity identity) {
        return ImNodeDto.builder()
            .nodeId(identity.getNodeId())
            .displayName(identity.getDisplayName())
            .publicKeyEd25519(identity.getPublicKeyEd25519())
            .publicKeyX25519(identity.getPublicKeyX25519())
            .lastSeen(System.currentTimeMillis())
            .avatarStyle(identity.getAvatarStyle())
            .avatarSeed(identity.getAvatarSeed())
            .build();
    }
}
