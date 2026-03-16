package com.jaguarliu.ai.im.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.im.config.ImLettuceConfig;
import com.jaguarliu.ai.im.crypto.ImCryptoService;
import com.jaguarliu.ai.im.entity.ImContactEntity;
import com.jaguarliu.ai.im.entity.ImConversationEntity;
import com.jaguarliu.ai.im.entity.ImIdentityEntity;
import com.jaguarliu.ai.im.event.ImEventPublisher;
import com.jaguarliu.ai.im.repository.ImContactRepository;
import com.jaguarliu.ai.im.repository.ImConversationRepository;
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
    private final ImConversationRepository conversationRepo;
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
                case "PAIR_REQUEST"    -> handlePairRequest(msg);
                case "PAIR_ACCEPT"     -> handlePairAccept(msg);
                case "PAIR_REJECT"     -> handlePairReject(msg);
                case "PROFILE_UPDATE"  -> handleProfileUpdate(msg);
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
            "fromAvatarStyle", msg.getOrDefault("fromAvatarStyle", "thumbs"),
            "fromAvatarSeed",  msg.getOrDefault("fromAvatarSeed", ""),
            "timestamp",       timestamp
        ));
        log.info("[IM] PAIR_REQUEST from {} ({})", fromDisplayName, fromNodeId);
    }

    private void handlePairAccept(Map<String, Object> msg) throws Exception {
        String fromNodeId      = (String) msg.get("fromNodeId");
        String fromDisplayName = (String) msg.getOrDefault("fromDisplayName", "Unknown");
        String fromPubEd25519  = (String) msg.get("fromPubEd25519");
        String fromPubX25519   = (String) msg.get("fromPubX25519");
        String signatureB64    = (String) msg.get("signature");
        long ts = ((Number) msg.get("timestamp")).longValue();

        // Verify signature — use existing contact's key, or the key supplied in the message
        Optional<ImContactEntity> existingContact = contactRepo.findById(fromNodeId);
        byte[] pubKeyDer;
        if (existingContact.isPresent()) {
            pubKeyDer = Base64.getDecoder().decode(existingContact.get().getPublicKeyEd25519());
        } else if (fromPubEd25519 != null) {
            pubKeyDer = Base64.getDecoder().decode(fromPubEd25519);
        } else {
            log.warn("[IM] PAIR_ACCEPT from unknown node {} with no public key", fromNodeId);
            return;
        }

        byte[] signedData = (fromNodeId + ":PAIR_ACCEPT:" + ts)
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] sig = Base64.getDecoder().decode(signatureB64);
        if (!crypto.verify(signedData, sig, crypto.ed25519PublicKey(pubKeyDer))) {
            log.warn("[IM] PAIR_ACCEPT signature invalid from {}", fromNodeId);
            return;
        }

        // Save B as a contact on A's side (if not already saved)
        if (existingContact.isEmpty() && fromPubEd25519 != null) {
            ImContactEntity newContact = ImContactEntity.builder()
                .nodeId(fromNodeId)
                .displayName(fromDisplayName)
                .publicKeyEd25519(fromPubEd25519)
                .publicKeyX25519(fromPubX25519 != null ? fromPubX25519 : "")
                .pairedAt(LocalDateTime.now())
                .status("active")
                .avatarStyle((String) msg.get("fromAvatarStyle"))
                .avatarSeed((String) msg.get("fromAvatarSeed"))
                .build();
            contactRepo.save(newContact);
        }

        eventPublisher.broadcast("im.pair_accepted", Map.of("nodeId", fromNodeId));

        // Create conversation entry so the requester can immediately open the chat
        String displayName = existingContact.map(ImContactEntity::getDisplayName)
            .orElse(fromDisplayName);
        conversationRepo.findById(fromNodeId).orElseGet(() -> {
            ImConversationEntity conv = ImConversationEntity.builder()
                    .id(fromNodeId)
                    .displayName(displayName)
                    .unreadCount(0)
                    .build();
            return conversationRepo.save(conv);
        });
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

    private void handleProfileUpdate(Map<String, Object> msg) throws Exception {
        String fromNodeId   = (String) msg.get("fromNodeId");
        String displayName  = (String) msg.get("displayName");
        String avatarStyle  = (String) msg.get("avatarStyle");
        String avatarSeed   = (String) msg.get("avatarSeed");
        String signatureB64 = (String) msg.get("signature");
        long ts = ((Number) msg.get("timestamp")).longValue();

        Optional<ImContactEntity> contactOpt = contactRepo.findById(fromNodeId);
        if (contactOpt.isEmpty()) {
            log.debug("[IM] PROFILE_UPDATE from unknown node {}, ignoring", fromNodeId);
            return;
        }
        byte[] pubKeyDer = Base64.getDecoder().decode(contactOpt.get().getPublicKeyEd25519());
        byte[] signedData = (fromNodeId + ":PROFILE_UPDATE:" + ts)
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] sig = Base64.getDecoder().decode(signatureB64);
        if (!crypto.verify(signedData, sig, crypto.ed25519PublicKey(pubKeyDer))) {
            log.warn("[IM] PROFILE_UPDATE signature invalid from {}", fromNodeId);
            return;
        }

        ImContactEntity contact = contactOpt.get();
        if (displayName != null && !displayName.isBlank()) contact.setDisplayName(displayName);
        if (avatarStyle != null && !avatarStyle.isBlank()) contact.setAvatarStyle(avatarStyle);
        if (avatarSeed  != null) contact.setAvatarSeed(avatarSeed);
        contactRepo.save(contact);

        // Also update conversation display name if it exists
        conversationRepo.findById(fromNodeId).ifPresent(conv -> {
            if (displayName != null && !displayName.isBlank()) conv.setDisplayName(displayName);
            conversationRepo.save(conv);
        });

        eventPublisher.broadcast("im.profile_updated", Map.of(
            "nodeId",      fromNodeId,
            "displayName", displayName  != null ? displayName : "",
            "avatarStyle", avatarStyle  != null ? avatarStyle : "",
            "avatarSeed",  avatarSeed   != null ? avatarSeed  : ""
        ));
        log.info("[IM] PROFILE_UPDATE from {}: displayName={}", fromNodeId, displayName);
    }

    /** Publish our updated profile to all active contacts */
    public void broadcastProfileUpdate() {
        List<ImContactEntity> activeContacts = contactRepo.findByStatus("active");
        if (activeContacts.isEmpty()) return;

        lettuceConfig.getClient().ifPresent(client -> {
            try {
                ImIdentityEntity self = identityService.getCached();
                long ts = System.currentTimeMillis();
                byte[] dataToSign = (self.getNodeId() + ":PROFILE_UPDATE:" + ts)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                byte[] privKeyDer = Base64.getDecoder().decode(self.getPrivateKeyEd25519());
                byte[] sig = crypto.sign(dataToSign, crypto.ed25519PrivateKey(privKeyDer));

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("type",        "PROFILE_UPDATE");
                payload.put("fromNodeId",  self.getNodeId());
                payload.put("displayName", self.getDisplayName());
                payload.put("avatarStyle", self.getAvatarStyle());
                payload.put("avatarSeed",  self.getAvatarSeed());
                payload.put("timestamp",   ts);
                payload.put("signature",   Base64.getEncoder().encodeToString(sig));
                String json = objectMapper.writeValueAsString(payload);

                try (StatefulRedisConnection<String, String> conn = client.connect()) {
                    for (ImContactEntity contact : activeContacts) {
                        String channel = "im:requests:" + contact.getNodeId();
                        conn.sync().publish(channel, json);
                    }
                }
                log.info("[IM] Broadcasted PROFILE_UPDATE to {} contact(s)", activeContacts.size());
            } catch (Exception e) {
                log.warn("[IM] Failed to broadcast PROFILE_UPDATE", e);
            }
        });
    }

    /** A sends PAIR_REQUEST to B */    public void sendPairRequest(String targetNodeId, String ignored) {
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
                payload.put("fromAvatarStyle",     self.getAvatarStyle());
                payload.put("fromAvatarSeed",      self.getAvatarSeed());
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
                                      String fromAvatarStyle, String fromAvatarSeed,
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
                .avatarStyle(fromAvatarStyle)
                .avatarSeed(fromAvatarSeed)
                .build();
            contactRepo.save(contact);
            // Create conversation so the accepter can immediately open the chat
            conversationRepo.findById(fromNodeId).orElseGet(() -> {
                ImConversationEntity conv = ImConversationEntity.builder()
                        .id(fromNodeId)
                        .displayName(fromDisplayName)
                        .unreadCount(0)
                        .build();
                return conversationRepo.save(conv);
            });
        }

        String msgType = accept ? "PAIR_ACCEPT" : "PAIR_REJECT";
        lettuceConfig.getClient().ifPresent(client -> {
            try {
                long ts = System.currentTimeMillis();
                byte[] dataToSign = (self.getNodeId() + ":" + msgType + ":" + ts)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                byte[] privKeyDer = Base64.getDecoder().decode(self.getPrivateKeyEd25519());
                byte[] sig = crypto.sign(dataToSign, crypto.ed25519PrivateKey(privKeyDer));

                Map<String, Object> payload;
                if (accept) {
                    // Include B's own public keys so A can save them as a contact
                    payload = new LinkedHashMap<>();
                    payload.put("type",            msgType);
                    payload.put("fromNodeId",       self.getNodeId());
                    payload.put("fromDisplayName",  self.getDisplayName());
                    payload.put("fromPubEd25519",   self.getPublicKeyEd25519());
                    payload.put("fromPubX25519",    self.getPublicKeyX25519());
                    payload.put("fromAvatarStyle",  self.getAvatarStyle());
                    payload.put("fromAvatarSeed",   self.getAvatarSeed());
                    payload.put("timestamp",        ts);
                    payload.put("signature",        Base64.getEncoder().encodeToString(sig));
                } else {
                    payload = Map.of(
                        "type",       msgType,
                        "fromNodeId", self.getNodeId(),
                        "timestamp",  ts,
                        "signature",  Base64.getEncoder().encodeToString(sig)
                    );
                }
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
