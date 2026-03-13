package com.jaguarliu.ai.im.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.im.config.ImLettuceConfig;
import com.jaguarliu.ai.im.crypto.ImCryptoService;
import com.jaguarliu.ai.im.entity.ImContactEntity;
import com.jaguarliu.ai.im.entity.ImIdentityEntity;
import com.jaguarliu.ai.im.event.ImEventPublisher;
import com.jaguarliu.ai.im.repository.ImContactRepository;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImPairingService {

    private final ImLettuceConfig lettuceConfig;
    private final ImIdentityService identityService;
    private final ImContactRepository contactRepo;
    private final ImCryptoService crypto;
    private final ImEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    private volatile StatefulRedisPubSubConnection<String, String> pubSubConn;

    /** Call after Redis is successfully configured */
    public synchronized void startSubscriptions() {
        if (!lettuceConfig.isConfigured()) return;
        stopSubscriptions();

        ImIdentityEntity identity = identityService.getCached();
        String channel = "im:requests:" + identity.getNodeId();

        lettuceConfig.getClient().ifPresent(client -> {
            pubSubConn = client.connectPubSub();
            pubSubConn.addListener(new RedisPubSubAdapter<>() {
                @Override
                public void message(String ch, String message) {
                    handlePairingMessage(message);
                }
            });
            RedisPubSubCommands<String, String> sub = pubSubConn.sync();
            sub.subscribe(channel);
            log.info("[IM] Subscribed to pairing channel: {}", channel);
        });
    }

    public synchronized void stopSubscriptions() {
        if (pubSubConn != null) {
            try { pubSubConn.close(); } catch (Exception ignored) {}
            pubSubConn = null;
        }
    }

    private void handlePairingMessage(String json) {
        try {
            Map<String, Object> msg = objectMapper.readValue(json, Map.class);
            String type = (String) msg.get("type");
            switch (type) {
                case "PAIR_REQUEST" -> handlePairRequest(msg);
                case "PAIR_ACCEPT"  -> handlePairAccept(msg);
                case "PAIR_REJECT"  -> handlePairReject(msg);
                default -> log.warn("[IM] Unknown pairing message type: {}", type);
            }
        } catch (Exception e) {
            log.warn("[IM] Failed to parse pairing message", e);
        }
    }

    private void handlePairRequest(Map<String, Object> msg) throws Exception {
        String fromNodeId       = (String) msg.get("fromNodeId");
        String fromDisplayName  = (String) msg.get("fromDisplayName");
        String fromPubEd25519   = (String) msg.get("fromPublicKeyEd25519");
        String signatureB64     = (String) msg.get("signature");
        Long timestamp          = ((Number) msg.get("timestamp")).longValue();

        byte[] signedData = (fromNodeId + ":" + fromDisplayName + ":" + timestamp)
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] sig = Base64.getDecoder().decode(signatureB64);
        byte[] pubKeyDer = Base64.getDecoder().decode(fromPubEd25519);

        if (!crypto.verify(signedData, sig, crypto.ed25519PublicKey(pubKeyDer))) {
            log.warn("[IM] PAIR_REQUEST signature invalid from {}", fromNodeId);
            return;
        }

        if (Math.abs(System.currentTimeMillis() - timestamp) > 5 * 60 * 1000L) {
            log.warn("[IM] rejected stale pair request from {}", fromNodeId);
            return;
        }

        eventPublisher.broadcast("im.pair_request", Map.of(
            "fromNodeId",      fromNodeId,
            "fromDisplayName", fromDisplayName,
            "fromPubEd25519",  fromPubEd25519,
            "fromPubX25519",   msg.get("fromPublicKeyX25519"),
            "timestamp",       timestamp
        ));
        log.info("[IM] PAIR_REQUEST from {} ({})", fromDisplayName, fromNodeId);
    }

    private void handlePairAccept(Map<String, Object> msg) throws Exception {
        String fromNodeId = (String) msg.get("fromNodeId");
        String signatureB64 = (String) msg.get("signature");
        long ts = ((Number) msg.get("timestamp")).longValue();

        Optional<ImContactEntity> contactOpt = contactRepo.findById(fromNodeId);
        if (contactOpt.isEmpty()) {
            log.warn("[IM] PAIR_ACCEPT from unknown node {}", fromNodeId);
            return;
        }
        byte[] pubKeyDer = Base64.getDecoder().decode(contactOpt.get().getPublicKeyEd25519());
        byte[] signedData = (fromNodeId + ":PAIR_ACCEPT:" + ts)
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] sig = Base64.getDecoder().decode(signatureB64);
        if (!crypto.verify(signedData, sig, crypto.ed25519PublicKey(pubKeyDer))) {
            log.warn("[IM] PAIR_ACCEPT signature invalid from {}", fromNodeId);
            return;
        }

        eventPublisher.broadcast("im.pair_accepted", Map.of("nodeId", fromNodeId));
        log.info("[IM] PAIR_ACCEPT from {}", fromNodeId);
    }

    private void handlePairReject(Map<String, Object> msg) throws Exception {
        String fromNodeId = (String) msg.get("fromNodeId");
        String signatureB64 = (String) msg.get("signature");
        long ts = ((Number) msg.get("timestamp")).longValue();

        Optional<ImContactEntity> contactOpt = contactRepo.findById(fromNodeId);
        if (contactOpt.isEmpty()) {
            log.warn("[IM] PAIR_REJECT from unknown node {}", fromNodeId);
            return;
        }
        byte[] pubKeyDer = Base64.getDecoder().decode(contactOpt.get().getPublicKeyEd25519());
        byte[] signedData = (fromNodeId + ":PAIR_REJECT:" + ts)
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] sig = Base64.getDecoder().decode(signatureB64);
        if (!crypto.verify(signedData, sig, crypto.ed25519PublicKey(pubKeyDer))) {
            log.warn("[IM] PAIR_REJECT signature invalid from {}", fromNodeId);
            return;
        }

        contactRepo.deleteById(fromNodeId);
        eventPublisher.broadcast("im.pair_rejected", Map.of("nodeId", fromNodeId));
        log.info("[IM] PAIR_REJECT from {}", fromNodeId);
    }

    /** A sends PAIR_REQUEST to B */
    public void sendPairRequest(String targetNodeId, String ignored) {
        lettuceConfig.getClient().ifPresent(client -> {
            try {
                ImIdentityEntity self = identityService.getCached();
                long ts = System.currentTimeMillis();
                byte[] dataToSign = (self.getNodeId() + ":" + self.getDisplayName() + ":" + ts)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                byte[] privKeyDer = Base64.getDecoder().decode(self.getPrivateKeyEd25519());
                byte[] sig = crypto.sign(dataToSign, crypto.ed25519PrivateKey(privKeyDer));

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("type",                "PAIR_REQUEST");
                payload.put("fromNodeId",          self.getNodeId());
                payload.put("fromDisplayName",     self.getDisplayName());
                payload.put("fromPublicKeyEd25519",self.getPublicKeyEd25519());
                payload.put("fromPublicKeyX25519", self.getPublicKeyX25519());
                payload.put("timestamp",           ts);
                payload.put("signature",           Base64.getEncoder().encodeToString(sig));

                String channel = "im:requests:" + targetNodeId;
                try (StatefulRedisConnection<String, String> conn = client.connect()) {
                    conn.sync().publish(channel, objectMapper.writeValueAsString(payload));
                }
                log.info("[IM] Sent PAIR_REQUEST to {}", targetNodeId);
            } catch (Exception e) {
                log.error("[IM] Failed to send PAIR_REQUEST to {}", targetNodeId, e);
            }
        });
    }

    /** B calls this to accept or reject a PAIR_REQUEST from A */
    public void respondToPairRequest(String fromNodeId, String fromDisplayName,
                                      String fromPubEd25519, String fromPubX25519,
                                      boolean accept) {
        ImIdentityEntity self = identityService.getCached();

        if (accept) {
            ImContactEntity contact = ImContactEntity.builder()
                .nodeId(fromNodeId)
                .displayName(fromDisplayName)
                .publicKeyEd25519(fromPubEd25519)
                .publicKeyX25519(fromPubX25519)
                .pairedAt(LocalDateTime.now())
                .status("active")
                .build();
            contactRepo.save(contact);
        }

        String msgType = accept ? "PAIR_ACCEPT" : "PAIR_REJECT";
        lettuceConfig.getClient().ifPresent(client -> {
            try {
                long ts = System.currentTimeMillis();
                byte[] dataToSign = (self.getNodeId() + ":" + msgType + ":" + ts)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                byte[] privKeyDer = Base64.getDecoder().decode(self.getPrivateKeyEd25519());
                byte[] sig = crypto.sign(dataToSign, crypto.ed25519PrivateKey(privKeyDer));

                Map<String, Object> payload = Map.of(
                    "type",       msgType,
                    "fromNodeId", self.getNodeId(),
                    "timestamp",  ts,
                    "signature",  Base64.getEncoder().encodeToString(sig)
                );
                String channel = "im:requests:" + fromNodeId;
                try (StatefulRedisConnection<String, String> conn = client.connect()) {
                    conn.sync().publish(channel, objectMapper.writeValueAsString(payload));
                }
                log.info("[IM] Sent {} to {}", msgType, fromNodeId);
            } catch (Exception e) {
                log.error("[IM] Failed to send {} to {}", msgType, fromNodeId, e);
            }
        });
    }
}
