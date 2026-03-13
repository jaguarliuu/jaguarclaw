package com.jaguarliu.ai.im.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.im.config.ImLettuceConfig;
import com.jaguarliu.ai.im.crypto.ImCryptoService;
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

import java.nio.charset.StandardCharsets;
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

    private volatile StatefulRedisPubSubConnection<String, String> msgSubConn;

    public synchronized void startSubscriptions() {
        if (!lettuceConfig.isConfigured()) return;
        stopSubscriptions();

        ImIdentityEntity identity = identityService.getCached();
        String channel = "im:messages:" + identity.getNodeId();

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
        });
    }

    public synchronized void stopSubscriptions() {
        if (msgSubConn != null) {
            try { msgSubConn.close(); } catch (Exception ignored) {}
            msgSubConn = null;
        }
    }

    public String sendText(String toNodeId, String text) {
        ImContactEntity contact = contactRepo.findById(toNodeId)
            .orElseThrow(() -> new IllegalArgumentException("Not a paired contact: " + toNodeId));

        try {
            ImIdentityEntity self = identityService.getCached();
            String messageId = UUID.randomUUID().toString();
            long ts = System.currentTimeMillis();

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
            wireMsg.put("fromAgentId",   null);
            wireMsg.put("toAgentId",     null);
            wireMsg.put("messageId",     messageId);
            wireMsg.put("timestamp",     ts);
            wireMsg.put("encryptedKey",  Base64.getEncoder().encodeToString(encryptedKey));
            wireMsg.put("encryptedBody", Base64.getEncoder().encodeToString(encryptedBody));
            wireMsg.put("signature",     Base64.getEncoder().encodeToString(sig));

            String channel = "im:messages:" + toNodeId;
            lettuceConfig.getClient().ifPresent(client -> {
                try (StatefulRedisConnection<String, String> conn = client.connect()) {
                    conn.sync().publish(channel, objectMapper.writeValueAsString(wireMsg));
                } catch (Exception e) { throw new RuntimeException(e); }
            });

            ImMessageEntity entity = ImMessageEntity.builder()
                .id(messageId)
                .conversationId(toNodeId)
                .senderNodeId(self.getNodeId())
                .type("TEXT")
                .content(contentJson)
                .createdAt(LocalDateTime.now())
                .status("sent")
                .build();
            messageRepo.save(entity);
            updateConversation(toNodeId, contact.getDisplayName(), text, entity.getCreatedAt());

            return messageId;
        } catch (Exception e) {
            throw new RuntimeException("Failed to send message to " + toNodeId, e);
        }
    }

    private void handleIncomingMessage(String json) {
        try {
            Map<String, Object> msg = objectMapper.readValue(json, Map.class);
            String fromNodeId = (String) msg.get("fromNodeId");
            String messageId  = (String) msg.get("messageId");

            Optional<ImContactEntity> contactOpt = contactRepo.findById(fromNodeId);
            if (contactOpt.isEmpty()) {
                log.debug("[IM] Dropping message from unpaired node: {}", fromNodeId);
                return;
            }
            ImContactEntity contact = contactOpt.get();
            if ("blocked".equals(contact.getStatus())) return;

            byte[] encryptedKey  = Base64.getDecoder().decode((String) msg.get("encryptedKey"));
            byte[] encryptedBody = Base64.getDecoder().decode((String) msg.get("encryptedBody"));
            long ts = ((Number) msg.get("timestamp")).longValue();
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

            ImMessageEntity entity = ImMessageEntity.builder()
                .id(messageId)
                .conversationId(fromNodeId)
                .senderNodeId(fromNodeId)
                .type("TEXT")
                .content(contentJson)
                .createdAt(LocalDateTime.ofEpochSecond(ts / 1000, 0, java.time.ZoneOffset.UTC))
                .status("delivered")
                .build();
            messageRepo.save(entity);

            String preview = objectMapper.readTree(contentJson).path("text").asText("");
            updateConversation(fromNodeId, contact.getDisplayName(), preview, entity.getCreatedAt());

            eventPublisher.broadcast("im.message", Map.of(
                "conversationId", fromNodeId,
                "messageId",      messageId,
                "senderNodeId",   fromNodeId,
                "displayName",    contact.getDisplayName(),
                "type",           "TEXT",
                "content",        contentJson,
                "createdAt",      entity.getCreatedAt().toString()
            ));

            log.info("[IM] Received message {} from {}", messageId, fromNodeId);
        } catch (Exception e) {
            log.warn("[IM] Failed to handle incoming message", e);
        }
    }

    private void updateConversation(String peerId, String displayName, String lastMsg,
                                     LocalDateTime ts) {
        ImConversationEntity conv = conversationRepo.findById(peerId)
            .orElse(ImConversationEntity.builder().id(peerId).unreadCount(0).build());
        conv.setDisplayName(displayName);
        conv.setLastMsg(lastMsg.length() > 80 ? lastMsg.substring(0, 80) + "\u2026" : lastMsg);
        conv.setLastMsgAt(ts);
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
