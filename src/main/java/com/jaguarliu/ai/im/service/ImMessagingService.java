package com.jaguarliu.ai.im.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.im.config.ImLettuceConfig;
import com.jaguarliu.ai.im.crypto.ImCryptoService;
import com.jaguarliu.ai.im.dto.ImNodeDto;
import com.jaguarliu.ai.im.entity.*;
import com.jaguarliu.ai.im.event.ImEventPublisher;
import com.jaguarliu.ai.im.repository.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImMessagingService {

    private final ImLettuceConfig lettuceConfig;
    private final ImIdentityService identityService;
    private final ImContactRepository contactRepo;
    private final ImMessageRepository messageRepo;
    private final ImConversationRepository conversationRepo;
    private final ImCryptoService crypto;
    private final ImEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final ImRegistryService registryService;

    private volatile StatefulRedisPubSubConnection<String, String> msgSubConn;

    public synchronized void startSubscriptions() {
        if (!lettuceConfig.isConfigured()) return;
        stopSubscriptions();

        ImIdentityEntity identity = identityService.getCached();
        String channel    = "im:messages:" + identity.getNodeId();
        String offlineKey = "im:offline:"  + identity.getNodeId();

        lettuceConfig.getClient().ifPresent(client -> {
            msgSubConn = client.connectPubSub();
            msgSubConn.addListener(new RedisPubSubAdapter<>() {
                @Override public void message(String ch, String msg) {
                    handleIncomingMessage(msg);
                }
            });
            RedisPubSubCommands<String, String> sub = msgSubConn.sync();
            sub.subscribe(channel);
            log.info("[IM] Subscribed to message channel: {}", channel);

            // Drain offline queue — messages sent while we were offline
            // Messages are in a Sorted Set ordered by timestamp score
            try (StatefulRedisConnection<String, String> conn = client.connect()) {
                List<String> offline = conn.sync()
                    .zrangebyscore(offlineKey,
                        io.lettuce.core.Range.unbounded());
                if (!offline.isEmpty()) {
                    log.info("[IM] Draining {} offline message(s)", offline.size());
                    for (String msg : offline) {
                        handleIncomingMessage(msg);
                    }
                    conn.sync().del(offlineKey);
                }
            } catch (Exception e) {
                log.warn("[IM] Failed to drain offline queue", e);
            }
        });
    }

    public synchronized void stopSubscriptions() {
        if (msgSubConn != null) {
            try { msgSubConn.close(); } catch (Exception ignored) {}
            msgSubConn = null;
        }
    }

    public String sendText(String toNodeId, String text) {
        // Auto-register contact from registry if not already stored
        ImContactEntity contact = contactRepo.findById(toNodeId)
            .orElseGet(() -> autoRegisterFromRegistry(toNodeId));
        if (contact == null)
            throw new IllegalArgumentException("Node not found or offline: " + toNodeId);

        try {
            ImIdentityEntity self = identityService.getCached();
            String messageId = UUID.randomUUID().toString();
            long ts = nowMillis();

            byte[] sessionKey = new byte[32];
            new SecureRandom().nextBytes(sessionKey);

            String contentJson = objectMapper.writeValueAsString(Map.of("text", text));
            byte[] plaintext = contentJson.getBytes(StandardCharsets.UTF_8);
            byte[] encryptedBody = crypto.aesGcmEncrypt(plaintext, sessionKey);

            byte[] recipientX25519Der = Base64.getDecoder().decode(contact.getPublicKeyX25519());
            byte[] encryptedKey = crypto.sealedBoxEncrypt(sessionKey,
                crypto.x25519PublicKey(recipientX25519Der));

            byte[] privKeyDer = Base64.getDecoder().decode(self.getPrivateKeyEd25519());
            byte[] dataToSign = concat(encryptedKey, encryptedBody, longToBytes(ts));
            byte[] sig = crypto.sign(dataToSign, crypto.ed25519PrivateKey(privKeyDer));

            Map<String, Object> wireMsg = new LinkedHashMap<>();
            wireMsg.put("type",          "MESSAGE");
            wireMsg.put("fromNodeId",    self.getNodeId());
            wireMsg.put("fromDisplayName", self.getDisplayName());
            wireMsg.put("fromPubEd25519",  self.getPublicKeyEd25519());
            wireMsg.put("fromPubX25519",   self.getPublicKeyX25519());
            wireMsg.put("fromAgentId",   null);
            wireMsg.put("toAgentId",     null);
            wireMsg.put("messageId",     messageId);
            wireMsg.put("contentType",   "TEXT");
            wireMsg.put("timestamp",     ts);
            wireMsg.put("encryptedKey",  Base64.getEncoder().encodeToString(encryptedKey));
            wireMsg.put("encryptedBody", Base64.getEncoder().encodeToString(encryptedBody));
            wireMsg.put("signature",     Base64.getEncoder().encodeToString(sig));

            String channel = "im:messages:" + toNodeId;
            lettuceConfig.getClient().ifPresent(client -> {
                try (StatefulRedisConnection<String, String> conn = client.connect()) {
                    String wireMsgJson = objectMapper.writeValueAsString(wireMsg);
                    conn.sync().publish(channel, wireMsgJson);
                    // Also write to offline queue (Sorted Set, score = timestamp)
                    // Recipient drains this on connect; TTL = 7 days
                    String offlineKey = "im:offline:" + toNodeId;
                    conn.sync().zadd(offlineKey, ts, wireMsgJson);
                    conn.sync().expire(offlineKey, 7 * 24 * 3600L);
                } catch (Exception e) { throw new RuntimeException(e); }
            });

            ImMessageEntity entity = ImMessageEntity.builder()
                .id(messageId)
                .conversationId(toNodeId)
                .senderNodeId(self.getNodeId())
                .type("TEXT")
                .content(contentJson)
                .createdAt(fromEpochMillis(ts))
                .status("sent")
                .build();
            messageRepo.save(entity);
            updateConversation(toNodeId, contact.getDisplayName(), text, entity.getCreatedAt(), false);

            return messageId;
        } catch (Exception e) {
            throw new RuntimeException("Failed to send message to " + toNodeId, e);
        }
    }

    public String sendFile(String toNodeId, String filename, String mimeType, byte[] fileData) {
        ImContactEntity contact = contactRepo.findById(toNodeId)
            .orElseGet(() -> autoRegisterFromRegistry(toNodeId));
        if (contact == null)
            throw new IllegalArgumentException("Node not found or offline: " + toNodeId);

        try {
            ImIdentityEntity self = identityService.getCached();
            String messageId = UUID.randomUUID().toString();
            long ts = nowMillis();

            byte[] sessionKey = new byte[32];
            new SecureRandom().nextBytes(sessionKey);

            boolean isImage = mimeType != null && mimeType.startsWith("image/");
            String msgType = isImage ? "IMAGE" : "FILE";

            // Wire body includes base64 file data
            String contentJsonWire = objectMapper.writeValueAsString(Map.of(
                "filename", filename,
                "mimeType", mimeType != null ? mimeType : "application/octet-stream",
                "size",     fileData.length,
                "data",     Base64.getEncoder().encodeToString(fileData)
            ));
            // Local storage: metadata only (no data)
            String contentJsonLocal = objectMapper.writeValueAsString(Map.of(
                "filename", filename,
                "mimeType", mimeType != null ? mimeType : "application/octet-stream",
                "size",     fileData.length
            ));

            byte[] plaintext = contentJsonWire.getBytes(StandardCharsets.UTF_8);
            byte[] encryptedBody = crypto.aesGcmEncrypt(plaintext, sessionKey);

            byte[] recipientX25519Der = Base64.getDecoder().decode(contact.getPublicKeyX25519());
            byte[] encryptedKey = crypto.sealedBoxEncrypt(sessionKey,
                crypto.x25519PublicKey(recipientX25519Der));

            byte[] privKeyDer = Base64.getDecoder().decode(self.getPrivateKeyEd25519());
            byte[] dataToSign = concat(encryptedKey, encryptedBody, longToBytes(ts));
            byte[] sig = crypto.sign(dataToSign, crypto.ed25519PrivateKey(privKeyDer));

            Map<String, Object> wireMsg = new LinkedHashMap<>();
            wireMsg.put("type",          "MESSAGE");
            wireMsg.put("fromNodeId",    self.getNodeId());
            wireMsg.put("fromDisplayName", self.getDisplayName());
            wireMsg.put("fromPubEd25519",  self.getPublicKeyEd25519());
            wireMsg.put("fromPubX25519",   self.getPublicKeyX25519());
            wireMsg.put("fromAgentId",   null);
            wireMsg.put("toAgentId",     null);
            wireMsg.put("messageId",     messageId);
            wireMsg.put("contentType",   msgType);
            wireMsg.put("timestamp",     ts);
            wireMsg.put("encryptedKey",  Base64.getEncoder().encodeToString(encryptedKey));
            wireMsg.put("encryptedBody", Base64.getEncoder().encodeToString(encryptedBody));
            wireMsg.put("signature",     Base64.getEncoder().encodeToString(sig));

            String channel = "im:messages:" + toNodeId;
            lettuceConfig.getClient().ifPresent(client -> {
                try (StatefulRedisConnection<String, String> conn = client.connect()) {
                    String wireMsgJson = objectMapper.writeValueAsString(wireMsg);
                    conn.sync().publish(channel, wireMsgJson);
                    String offlineKey = "im:offline:" + toNodeId;
                    conn.sync().zadd(offlineKey, ts, wireMsgJson);
                    conn.sync().expire(offlineKey, 7 * 24 * 3600L);
                } catch (Exception e) { throw new RuntimeException(e); }
            });

            // Save file to disk for local serving
            String localPath = saveFileToDisk(messageId, filename, fileData);

            ImMessageEntity entity = ImMessageEntity.builder()
                .id(messageId)
                .conversationId(toNodeId)
                .senderNodeId(self.getNodeId())
                .type(msgType)
                .content(contentJsonLocal)
                .localFilePath(localPath)
                .createdAt(fromEpochMillis(ts))
                .status("sent")
                .build();
            messageRepo.save(entity);
            updateConversation(toNodeId, contact.getDisplayName(), "[" + filename + "]", entity.getCreatedAt(), false);

            return messageId;
        } catch (Exception e) {
            throw new RuntimeException("Failed to send file to " + toNodeId, e);
        }
    }

    private String saveFileToDisk(String messageId, String filename, byte[] data) {
        try {
            Path dir = Paths.get(System.getProperty("user.home"), ".jaguarclaw", "im-files", messageId);
            Files.createDirectories(dir);
            Path file = dir.resolve(filename);
            Files.write(file, data);
            return file.toString();
        } catch (IOException e) {
            log.warn("[IM] Failed to save file {} for message {}", filename, messageId, e);
            return null;
        }
    }

    private ImContactEntity autoRegisterFromRegistry(String nodeId) {
        return registryService.getNode(nodeId).map(node -> {
            ImContactEntity c = contactRepo.findById(nodeId).orElseGet(() ->
                ImContactEntity.builder()
                    .nodeId(node.getNodeId())
                    .pairedAt(nowUtc())
                    .status("active")
                    .build()
            );
            c.setDisplayName(node.getDisplayName());
            c.setPublicKeyEd25519(node.getPublicKeyEd25519());
            c.setPublicKeyX25519(node.getPublicKeyX25519());
            if (node.getAvatarStyle() != null) c.setAvatarStyle(node.getAvatarStyle());
            if (node.getAvatarSeed()  != null) c.setAvatarSeed(node.getAvatarSeed());
            return contactRepo.save(c);
        }).orElse(null);
    }

    private void handleIncomingMessage(String json) {
        try {
            Map<String, Object> msg = objectMapper.readValue(json, Map.class);
            String fromNodeId = (String) msg.get("fromNodeId");
            String messageId  = (String) msg.get("messageId");

            // Skip already-processed messages (dedup for offline queue drain)
            if (messageRepo.existsById(messageId)) {
                log.debug("[IM] Skipping already-processed message {}", messageId);
                return;
            }

            Optional<ImContactEntity> contactOpt = contactRepo.findById(fromNodeId);
            ImContactEntity contact;
            if (contactOpt.isPresent()) {
                contact = contactOpt.get();
                if ("blocked".equals(contact.getStatus())) return;
            } else {
                // TOFU: auto-register from keys embedded in the wire message
                String fromPubEd25519 = (String) msg.get("fromPubEd25519");
                String fromPubX25519  = (String) msg.get("fromPubX25519");
                if (fromPubEd25519 == null || fromPubX25519 == null) {
                    log.debug("[IM] Dropping message from unknown node {} (no public keys in message)", fromNodeId);
                    return;
                }
                String fromDisplayName = (String) msg.getOrDefault("fromDisplayName", fromNodeId.substring(0, 8));
                contact = ImContactEntity.builder()
                    .nodeId(fromNodeId)
                    .displayName(fromDisplayName)
                    .publicKeyEd25519(fromPubEd25519)
                    .publicKeyX25519(fromPubX25519)
                    .pairedAt(nowUtc())
                    .status("active")
                    .build();
                contactRepo.save(contact);
                log.info("[IM] Auto-registered new contact {} ({})", fromDisplayName, fromNodeId);
            }

            long ts = ((Number) msg.get("timestamp")).longValue();
            byte[] encryptedKey  = Base64.getDecoder().decode((String) msg.get("encryptedKey"));
            byte[] encryptedBody = Base64.getDecoder().decode((String) msg.get("encryptedBody"));
            byte[] sig = Base64.getDecoder().decode((String) msg.get("signature"));

            byte[] senderPubDer = Base64.getDecoder().decode(contact.getPublicKeyEd25519());
            byte[] dataToVerify = concat(encryptedKey, encryptedBody, longToBytes(ts));
            if (!crypto.verify(dataToVerify, sig, crypto.ed25519PublicKey(senderPubDer))) {
                log.warn("[IM] Message signature verification failed from {}", fromNodeId);
                return;
            }

            ImIdentityEntity self = identityService.getCached();
            byte[] privKeyDer = Base64.getDecoder().decode(self.getPrivateKeyX25519());
            byte[] pubKeyDer  = Base64.getDecoder().decode(self.getPublicKeyX25519());
            byte[] sessionKey = crypto.sealedBoxDecrypt(encryptedKey,
                crypto.x25519PrivateKey(privKeyDer), crypto.x25519PublicKey(pubKeyDer));

            byte[] plaintext = crypto.aesGcmDecrypt(encryptedBody, sessionKey);
            String contentJson = new String(plaintext, StandardCharsets.UTF_8);

            String contentType = (String) msg.getOrDefault("contentType", "TEXT");
            String localPath = null;
            String localContentJson = contentJson;

            if ("IMAGE".equals(contentType) || "FILE".equals(contentType)) {
                var tree = objectMapper.readTree(contentJson);
                String filename = tree.path("filename").asText("file");
                String dataB64  = tree.path("data").asText("");
                if (!dataB64.isEmpty()) {
                    byte[] fileBytes = Base64.getDecoder().decode(dataB64);
                    localPath = saveFileToDisk(messageId, filename, fileBytes);
                }
                // Strip data from stored content
                localContentJson = objectMapper.writeValueAsString(Map.of(
                    "filename", tree.path("filename").asText(""),
                    "mimeType", tree.path("mimeType").asText("application/octet-stream"),
                    "size",     tree.path("size").asLong(0)
                ));
            }

            ImContactEntity contact2 = contact; // capture for lambda below

            ImMessageEntity entity = ImMessageEntity.builder()
                .id(messageId)
                .conversationId(fromNodeId)
                .senderNodeId(fromNodeId)
                .type(contentType)
                .content(localContentJson)
                .localFilePath(localPath)
                .createdAt(fromEpochMillis(ts))
                .status("delivered")
                .build();
            messageRepo.save(entity);

            String preview;
            if ("TEXT".equals(contentType)) {
                preview = objectMapper.readTree(localContentJson).path("text").asText("");
            } else {
                String fn = objectMapper.readTree(localContentJson).path("filename").asText("file");
                preview = "[" + fn + "]";
            }
            updateConversation(fromNodeId, contact2.getDisplayName(), preview, entity.getCreatedAt(), true);

            eventPublisher.broadcast("im.message", Map.of(
                "conversationId", fromNodeId,
                "messageId",      messageId,
                "senderNodeId",   fromNodeId,
                "displayName",    contact2.getDisplayName(),
                "type",           contentType,
                "content",        localContentJson,
                "createdAt",      entity.getCreatedAt().toString() + "Z"
            ));

            log.info("[IM] Received message {} from {}", messageId, fromNodeId);
        } catch (Exception e) {
            log.warn("[IM] Failed to handle incoming message", e);
        }
    }

    /**
     * Returns current time in milliseconds from Redis server clock.
     * Using Redis TIME ensures all participants share the same time source
     * regardless of individual machine clock differences.
     * Falls back to local clock if Redis is unavailable.
     */
    private long nowMillis() {
        return lettuceConfig.getClient()
            .map(client -> {
                try (StatefulRedisConnection<String, String> conn = client.connect()) {
                    java.util.List<String> t = conn.sync().time();
                    return Long.parseLong(t.get(0)) * 1000L + Long.parseLong(t.get(1)) / 1000L;
                } catch (Exception e) {
                    log.warn("[IM] Failed to get Redis time, falling back to local clock", e);
                    return System.currentTimeMillis();
                }
            })
            .orElse(System.currentTimeMillis());
    }

    private static LocalDateTime nowUtc() {
        return LocalDateTime.now(java.time.ZoneOffset.UTC);
    }

    private static LocalDateTime fromEpochMillis(long epochMillis) {
        long seconds = epochMillis / 1000;
        int nanos    = (int) ((epochMillis % 1000) * 1_000_000);
        return LocalDateTime.ofEpochSecond(seconds, nanos, java.time.ZoneOffset.UTC);
    }

    private void updateConversation(String peerId, String displayName, String lastMsg,
                                     LocalDateTime ts, boolean incrementUnread) {
        ImConversationEntity conv = conversationRepo.findById(peerId)
            .orElse(ImConversationEntity.builder().id(peerId).unreadCount(0).build());
        conv.setDisplayName(displayName);
        conv.setLastMsg(lastMsg.length() > 80 ? lastMsg.substring(0, 80) + "\u2026" : lastMsg);
        conv.setLastMsgAt(ts);
        if (incrementUnread) conv.setUnreadCount(conv.getUnreadCount() + 1);
        conversationRepo.save(conv);
    }

    private static byte[] concat(byte[]... arrays) {
        int total = 0; for (byte[] a : arrays) total += a.length;
        byte[] out = new byte[total]; int pos = 0;
        for (byte[] a : arrays) { System.arraycopy(a, 0, out, pos, a.length); pos += a.length; }
        return out;
    }

    private static byte[] longToBytes(long v) {
        byte[] b = new byte[8];
        for (int i = 7; i >= 0; i--) { b[i] = (byte)(v & 0xff); v >>= 8; }
        return b;
    }
}
