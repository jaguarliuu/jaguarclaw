package com.jaguarliu.ai.im.service;

import com.jaguarliu.ai.im.crypto.ImCryptoService;
import com.jaguarliu.ai.im.entity.ImIdentityEntity;
import com.jaguarliu.ai.im.repository.ImIdentityRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImIdentityService {

    private final ImIdentityRepository repo;
    private final ImCryptoService crypto;

    private ImIdentityEntity cachedIdentity;

    @PostConstruct
    public void init() {
        try {
            cachedIdentity = getOrInit();
            log.info("[IM] Identity initialized: nodeId={}, displayName={}",
                cachedIdentity.getNodeId(), cachedIdentity.getDisplayName());
        } catch (Exception e) {
            log.error("[IM] Failed to initialize identity", e);
        }
    }

    public ImIdentityEntity getOrInit() {
        List<ImIdentityEntity> all = repo.findAll();
        if (!all.isEmpty()) {
            return all.get(0);
        }
        return createIdentity();
    }

    public ImIdentityEntity getCached() {
        if (cachedIdentity == null) cachedIdentity = getOrInit();
        return cachedIdentity;
    }

    /** Call after user updates displayName or Redis config */
    public ImIdentityEntity save(ImIdentityEntity entity) {
        ImIdentityEntity saved = repo.save(entity);
        cachedIdentity = saved;
        return saved;
    }

    private ImIdentityEntity createIdentity() {
        try {
            KeyPair ed25519Pair = crypto.generateEd25519KeyPair();
            KeyPair x25519Pair  = crypto.generateX25519KeyPair();
            String nodeId = crypto.deriveNodeId(ed25519Pair.getPublic());

            ImIdentityEntity entity = ImIdentityEntity.builder()
                .nodeId(nodeId)
                .displayName("Me")
                .publicKeyEd25519(b64(ed25519Pair.getPublic().getEncoded()))
                .publicKeyX25519(b64(x25519Pair.getPublic().getEncoded()))
                .privateKeyEd25519(b64(ed25519Pair.getPrivate().getEncoded()))
                .privateKeyX25519(b64(x25519Pair.getPrivate().getEncoded()))
                .createdAt(LocalDateTime.now())
                .build();

            return repo.save(entity);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate IM identity", e);
        }
    }

    private static String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
