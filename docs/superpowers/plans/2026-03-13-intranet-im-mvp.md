# Intranet IM MVP Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a working encrypted intranet chat between MiniClaw nodes — identity generation, Redis-based node discovery, pairing authorization, and E2EE text messaging.

**Architecture:** Each node generates a local Ed25519 + X25519 key pair on first startup; nodeId = first 16 bytes of SHA-256(ed25519_pub_key) as hex. Redis is used only as a pub/sub signaling layer (not for storing messages). All messages are hybrid-encrypted (X25519 ECDH + AES-256-GCM) and signed (Ed25519) before publishing; only paired contacts whose messages survive signature verification are stored in SQLite.

**Tech Stack:** Bouncy Castle 1.80 (crypto), Lettuce 6.5 (Redis client — managed manually, NOT Spring Data Redis autoconfigure), Spring Boot @Scheduled (heartbeat), JPA/SQLite (local storage), Vue 3 + new `useIm.ts` composable, existing EventBus + ConnectionManager (server→client push).

**Scope notes:**
- This plan = MVP: identity + pairing + text messaging
- File P2P transfer → separate plan (Plan B)
- Agent message routing → separate plan (Plan C)

---

## File Structure

### New — Backend

```
src/main/resources/db/migration/
  V27__im_schema.sql

src/main/java/com/jaguarliu/ai/im/
  config/
    ImLettuceConfig.java          — Lettuce client factory; optional, connects only when Redis is configured
  crypto/
    ImCryptoService.java          — Ed25519 sign/verify, X25519 sealed-box, AES-GCM
  entity/
    ImIdentityEntity.java         — Local identity (keys + displayName)
    ImContactEntity.java          — Paired contacts
    ImConversationEntity.java     — Per-peer conversation (last msg, unread count)
    ImMessageEntity.java          — Decrypted messages
  repository/
    ImIdentityRepository.java
    ImContactRepository.java
    ImConversationRepository.java
    ImMessageRepository.java
  dto/
    ImNodeDto.java                — Online node (from Redis scan)
    ImContactDto.java
    ImConversationDto.java
    ImMessageDto.java
  service/
    ImIdentityService.java        — Key gen + nodeId; auto-init on startup
    ImRegistryService.java        — Redis: register node, heartbeat, scan nodes
    ImPairingService.java         — PAIR_REQUEST/ACCEPT/REJECT pub/sub + push to frontend
    ImMessagingService.java       — E2EE send/receive, decrypt, persist
  event/
    ImEventPublisher.java         — Wraps ConnectionManager; broadcasts IM events to all WS sessions
  handler/
    ImSettingsGetHandler.java     — im.settings.get
    ImSettingsSaveHandler.java    — im.settings.save
    ImNodesListHandler.java       — im.nodes.list
    ImPairRequestHandler.java     — im.pair.request
    ImPairRespondHandler.java     — im.pair.respond
    ImContactsListHandler.java    — im.contacts.list
    ImMessageSendHandler.java     — im.message.send
    ImConversationsListHandler.java — im.conversations.list
    ImMessagesListHandler.java    — im.messages.list
```

### New — Tests

```
src/test/java/com/jaguarliu/ai/im/
  crypto/ImCryptoServiceTest.java
  service/ImIdentityServiceTest.java
```

### New — Frontend

```
jaguarclaw-ui/src/
  composables/useIm.ts
  views/ImView.vue
  components/im/
    ImContactList.vue
    ImChatWindow.vue
    ImPairToast.vue
  components/settings/ImConfigSection.vue
```

### Modified

```
pom.xml                                         — add Bouncy Castle + Lettuce
src/main/resources/db/migration/                — new V27
jaguarclaw-ui/src/types/index.ts                — add IM types
jaguarclaw-ui/src/router/index.ts               — add /im route
jaguarclaw-ui/src/components/layout/ModeSwitcher.vue  — add IM button
jaguarclaw-ui/src/views/SettingsView.vue        — add ImConfigSection
jaguarclaw-ui/src/components/settings/SettingsSidebar.vue  — add im nav item
```

---

## Chunk 1: Foundation — Dependencies, DB, Entities, Repositories

### Task 1: Add Bouncy Castle and Lettuce dependencies

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add dependencies**

In `pom.xml`, inside `<dependencies>`:

```xml
<!-- Bouncy Castle — Ed25519, X25519, AES-GCM -->
<dependency>
  <groupId>org.bouncycastle</groupId>
  <artifactId>bcprov-jdk18on</artifactId>
  <version>1.80</version>
</dependency>

<!-- Lettuce — Redis Pub/Sub client (managed manually, no Spring autoconfigure) -->
<dependency>
  <groupId>io.lettuce</groupId>
  <artifactId>lettuce-core</artifactId>
  <version>6.5.0.RELEASE</version>
</dependency>
```

- [ ] **Step 2: Verify build resolves dependencies**

```bash
cd /path/to/miniclaw
mvn dependency:resolve -q
```
Expected: no download errors for `bcprov-jdk18on:1.80` and `lettuce-core:6.5.0.RELEASE`.

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "feat(im): add Bouncy Castle and Lettuce dependencies"
```

---

### Task 2: Database migration — IM schema

**Files:**
- Create: `src/main/resources/db/migration/V27__im_schema.sql`

- [ ] **Step 1: Write migration**

```sql
-- V27__im_schema.sql
-- 内网 IM 模块：本地身份、联系人、会话、消息

CREATE TABLE IF NOT EXISTS im_identity (
    node_id               VARCHAR(36)  PRIMARY KEY,
    display_name          TEXT         NOT NULL,
    public_key_ed25519    TEXT         NOT NULL,   -- Base64-encoded DER
    public_key_x25519     TEXT         NOT NULL,   -- Base64-encoded DER
    private_key_ed25519   TEXT         NOT NULL,   -- Base64-encoded DER (PKCS#8)
    private_key_x25519    TEXT         NOT NULL,   -- Base64-encoded DER (PKCS#8)
    redis_url             TEXT,                    -- e.g. redis://192.168.1.10:6379
    redis_password        TEXT,
    created_at            TIMESTAMP    NOT NULL    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS im_contacts (
    node_id               VARCHAR(36)  PRIMARY KEY,
    display_name          TEXT         NOT NULL,
    public_key_ed25519    TEXT         NOT NULL,
    public_key_x25519     TEXT         NOT NULL,
    paired_at             TIMESTAMP    NOT NULL    DEFAULT CURRENT_TIMESTAMP,
    status                VARCHAR(16)  NOT NULL    DEFAULT 'active'  -- 'active' | 'blocked'
);

CREATE TABLE IF NOT EXISTS im_conversations (
    id                    VARCHAR(36)  PRIMARY KEY,  -- peer nodeId
    display_name          TEXT,
    last_msg              TEXT,
    last_msg_at           TIMESTAMP,
    unread_count          INTEGER      NOT NULL    DEFAULT 0
);

CREATE TABLE IF NOT EXISTS im_messages (
    id                    VARCHAR(36)  PRIMARY KEY,  -- messageId (UUID from sender)
    conversation_id       VARCHAR(36)  NOT NULL,
    sender_node_id        VARCHAR(36)  NOT NULL,
    type                  VARCHAR(16)  NOT NULL,     -- TEXT | IMAGE | FILE | AGENT_MESSAGE
    content               TEXT         NOT NULL,     -- 解密后明文 JSON
    local_file_path       TEXT,
    created_at            TIMESTAMP    NOT NULL,
    status                VARCHAR(16)  NOT NULL      -- 'sent' | 'delivered' | 'failed'
);

CREATE INDEX IF NOT EXISTS idx_im_messages_conversation ON im_messages(conversation_id, created_at);
```

- [ ] **Step 2: Verify migration applies on startup**

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8090"
# Check logs for: Flyway: Successfully applied 1 migration to schema
# Then Ctrl+C
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V27__im_schema.sql
git commit -m "feat(im): add im_* schema migration V27"
```

---

### Task 3: JPA Entities and Repositories

**Files:**
- Create: all 4 entity + 4 repository files listed above

- [ ] **Step 1: Create ImIdentityEntity**

`src/main/java/com/jaguarliu/ai/im/entity/ImIdentityEntity.java`:

```java
package com.jaguarliu.ai.im.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "im_identity")
public class ImIdentityEntity {
    @Id
    private String nodeId;

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String publicKeyEd25519;   // Base64 DER

    @Column(nullable = false, columnDefinition = "TEXT")
    private String publicKeyX25519;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String privateKeyEd25519;  // Base64 PKCS#8

    @Column(nullable = false, columnDefinition = "TEXT")
    private String privateKeyX25519;

    @Column(columnDefinition = "TEXT")
    private String redisUrl;

    @Column(columnDefinition = "TEXT")
    private String redisPassword;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 2: Create ImContactEntity**

`src/main/java/com/jaguarliu/ai/im/entity/ImContactEntity.java`:

```java
package com.jaguarliu.ai.im.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "im_contacts")
public class ImContactEntity {
    @Id
    private String nodeId;

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String publicKeyEd25519;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String publicKeyX25519;

    @Column(nullable = false)
    private LocalDateTime pairedAt;

    @Column(nullable = false)
    @Builder.Default
    private String status = "active";  // "active" | "blocked"
}
```

- [ ] **Step 3: Create ImConversationEntity**

`src/main/java/com/jaguarliu/ai/im/entity/ImConversationEntity.java`:

```java
package com.jaguarliu.ai.im.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "im_conversations")
public class ImConversationEntity {
    @Id
    private String id;  // peer nodeId

    private String displayName;
    private String lastMsg;
    private LocalDateTime lastMsgAt;

    @Column(nullable = false)
    @Builder.Default
    private int unreadCount = 0;
}
```

- [ ] **Step 4: Create ImMessageEntity**

`src/main/java/com/jaguarliu/ai/im/entity/ImMessageEntity.java`:

```java
package com.jaguarliu.ai.im.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "im_messages")
public class ImMessageEntity {
    @Id
    private String id;  // UUID from sender

    @Column(nullable = false)
    private String conversationId;

    @Column(nullable = false)
    private String senderNodeId;

    @Column(nullable = false)
    private String type;  // TEXT | IMAGE | FILE | AGENT_MESSAGE

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;  // 解密后明文 JSON

    private String localFilePath;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private String status;  // sent | delivered | failed
}
```

- [ ] **Step 5: Create the four repositories**

`ImIdentityRepository.java`:
```java
package com.jaguarliu.ai.im.repository;
import com.jaguarliu.ai.im.entity.ImIdentityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ImIdentityRepository extends JpaRepository<ImIdentityEntity, String> {}
```

`ImContactRepository.java`:
```java
package com.jaguarliu.ai.im.repository;
import com.jaguarliu.ai.im.entity.ImContactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ImContactRepository extends JpaRepository<ImContactEntity, String> {
    List<ImContactEntity> findByStatus(String status);
}
```

`ImConversationRepository.java`:
```java
package com.jaguarliu.ai.im.repository;
import com.jaguarliu.ai.im.entity.ImConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
public interface ImConversationRepository extends JpaRepository<ImConversationEntity, String> {
    @Query("SELECT c FROM ImConversationEntity c ORDER BY c.lastMsgAt DESC NULLS LAST")
    List<ImConversationEntity> findAllOrderByLastMsgAtDesc();
}
```

`ImMessageRepository.java`:
```java
package com.jaguarliu.ai.im.repository;
import com.jaguarliu.ai.im.entity.ImMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ImMessageRepository extends JpaRepository<ImMessageEntity, String> {
    List<ImMessageEntity> findByConversationIdOrderByCreatedAtAsc(String conversationId);
}
```

- [ ] **Step 6: Compile**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/im/
git commit -m "feat(im): add im entities and repositories"
```

---

## Chunk 2: Crypto + Identity + Registry + Settings

### Task 4: ImCryptoService (TDD)

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/im/crypto/ImCryptoService.java`
- Test: `src/test/java/com/jaguarliu/ai/im/crypto/ImCryptoServiceTest.java`

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/jaguarliu/ai/im/crypto/ImCryptoServiceTest.java`:

```java
package com.jaguarliu.ai.im.crypto;

import org.junit.jupiter.api.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ImCryptoService")
class ImCryptoServiceTest {

    private ImCryptoService crypto;

    @BeforeEach
    void setUp() {
        crypto = new ImCryptoService();
    }

    @Nested
    @DisplayName("Ed25519 sign/verify")
    class SignVerify {
        @Test
        @DisplayName("sign then verify succeeds")
        void signAndVerify() throws Exception {
            KeyPair pair = crypto.generateEd25519KeyPair();
            byte[] data = "hello world".getBytes(StandardCharsets.UTF_8);
            byte[] sig = crypto.sign(data, pair.getPrivate());
            assertTrue(crypto.verify(data, sig, pair.getPublic()));
        }

        @Test
        @DisplayName("tampered data fails verification")
        void tamperedDataFails() throws Exception {
            KeyPair pair = crypto.generateEd25519KeyPair();
            byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
            byte[] sig = crypto.sign(data, pair.getPrivate());
            byte[] tampered = "world".getBytes(StandardCharsets.UTF_8);
            assertFalse(crypto.verify(tampered, sig, pair.getPublic()));
        }
    }

    @Nested
    @DisplayName("X25519 sealed box — AES-GCM")
    class SealedBox {
        @Test
        @DisplayName("encrypt then decrypt recovers plaintext")
        void encryptDecrypt() throws Exception {
            KeyPair recipient = crypto.generateX25519KeyPair();
            byte[] plaintext = "secret session key".getBytes(StandardCharsets.UTF_8);
            byte[] encrypted = crypto.sealedBoxEncrypt(plaintext, recipient.getPublic());
            byte[] decrypted = crypto.sealedBoxDecrypt(encrypted, recipient.getPrivate(), recipient.getPublic());
            assertArrayEquals(plaintext, decrypted);
        }

        @Test
        @DisplayName("wrong private key fails decryption")
        void wrongKeyFails() throws Exception {
            KeyPair recipient = crypto.generateX25519KeyPair();
            KeyPair attacker = crypto.generateX25519KeyPair();
            byte[] plaintext = "secret".getBytes(StandardCharsets.UTF_8);
            byte[] encrypted = crypto.sealedBoxEncrypt(plaintext, recipient.getPublic());
            assertThrows(Exception.class,
                () -> crypto.sealedBoxDecrypt(encrypted, attacker.getPrivate(), attacker.getPublic()));
        }
    }

    @Nested
    @DisplayName("AES-GCM")
    class AesGcm {
        @Test
        @DisplayName("encrypt then decrypt roundtrip")
        void roundtrip() throws Exception {
            byte[] key = new byte[32];
            new java.security.SecureRandom().nextBytes(key);
            byte[] plaintext = "message body".getBytes(StandardCharsets.UTF_8);
            byte[] encrypted = crypto.aesGcmEncrypt(plaintext, key);
            byte[] decrypted = crypto.aesGcmDecrypt(encrypted, key);
            assertArrayEquals(plaintext, decrypted);
        }
    }

    @Test
    @DisplayName("deriveNodeId returns 32-char hex")
    void nodeId() throws Exception {
        KeyPair pair = crypto.generateEd25519KeyPair();
        String nodeId = crypto.deriveNodeId(pair.getPublic());
        assertEquals(32, nodeId.length());
        assertTrue(nodeId.matches("[0-9a-f]+"));
    }
}
```

- [ ] **Step 2: Run tests — expect failure (class not found)**

```bash
mvn test -pl . -Dtest=ImCryptoServiceTest -q 2>&1 | tail -5
```
Expected: COMPILATION ERROR or test failure (class not found).

- [ ] **Step 3: Implement ImCryptoService**

`src/main/java/com/jaguarliu/ai/im/crypto/ImCryptoService.java`:

```java
package com.jaguarliu.ai.im.crypto;

import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;

@Service
public class ImCryptoService {

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    // ── Key Generation ──────────────────────────────────────────────────────

    public KeyPair generateEd25519KeyPair() throws GeneralSecurityException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519", "BC");
        return kpg.generateKeyPair();
    }

    public KeyPair generateX25519KeyPair() throws GeneralSecurityException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519", "BC");
        return kpg.generateKeyPair();
    }

    // Reconstruct public key from DER bytes
    public PublicKey ed25519PublicKey(byte[] der) throws GeneralSecurityException {
        return KeyFactory.getInstance("Ed25519", "BC").generatePublic(new X509EncodedKeySpec(der));
    }

    public PublicKey x25519PublicKey(byte[] der) throws GeneralSecurityException {
        return KeyFactory.getInstance("X25519", "BC").generatePublic(new X509EncodedKeySpec(der));
    }

    public PrivateKey ed25519PrivateKey(byte[] pkcs8) throws GeneralSecurityException {
        return KeyFactory.getInstance("Ed25519", "BC").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
    }

    public PrivateKey x25519PrivateKey(byte[] pkcs8) throws GeneralSecurityException {
        return KeyFactory.getInstance("X25519", "BC").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
    }

    // ── nodeId = hex(SHA-256(ed25519_pub_DER)[0..15]) ───────────────────────

    public String deriveNodeId(PublicKey ed25519pub) throws GeneralSecurityException {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(ed25519pub.getEncoded());
        return bytesToHex(Arrays.copyOf(hash, 16));
    }

    // ── Ed25519 sign / verify ───────────────────────────────────────────────

    public byte[] sign(byte[] data, PrivateKey privateKey) throws GeneralSecurityException {
        Signature signer = Signature.getInstance("Ed25519", "BC");
        signer.initSign(privateKey);
        signer.update(data);
        return signer.sign();
    }

    public boolean verify(byte[] data, byte[] signature, PublicKey publicKey) {
        try {
            Signature verifier = Signature.getInstance("Ed25519", "BC");
            verifier.initVerify(publicKey);
            verifier.update(data);
            return verifier.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }

    // ── X25519 Sealed Box ───────────────────────────────────────────────────
    // Format: ephemeral_pub_DER(bytes) | len(2 bytes BE) | iv(12) | ciphertext+tag

    public byte[] sealedBoxEncrypt(byte[] plaintext, PublicKey recipientX25519Pub)
            throws GeneralSecurityException {
        // 1. Ephemeral X25519 pair
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519", "BC");
        KeyPair ephemeral = kpg.generateKeyPair();

        // 2. ECDH
        KeyAgreement ka = KeyAgreement.getInstance("X25519", "BC");
        ka.init(ephemeral.getPrivate());
        ka.doPhase(recipientX25519Pub, true);
        byte[] sharedSecret = ka.generateSecret();

        // 3. HKDF-SHA256: ikm = sharedSecret || ephemeral_pub || recipient_pub
        byte[] ephPubDer = ephemeral.getPublic().getEncoded();
        byte[] recPubDer = recipientX25519Pub.getEncoded();
        byte[] ikm = concat(sharedSecret, ephPubDer, recPubDer);
        byte[] aesKey = hkdf(ikm, 32);

        // 4. AES-GCM encrypt
        byte[] encrypted = aesGcmEncrypt(plaintext, aesKey);

        // 5. Pack: [2-byte ephPubLen][ephPubDer][encrypted]
        byte[] lenBytes = new byte[]{(byte)(ephPubDer.length >> 8), (byte)(ephPubDer.length & 0xff)};
        return concat(lenBytes, ephPubDer, encrypted);
    }

    public byte[] sealedBoxDecrypt(byte[] packed, PrivateKey recipientX25519Priv,
                                    PublicKey recipientX25519Pub)
            throws GeneralSecurityException {
        // Unpack
        int ephLen = ((packed[0] & 0xff) << 8) | (packed[1] & 0xff);
        byte[] ephPubDer = Arrays.copyOfRange(packed, 2, 2 + ephLen);
        byte[] encrypted = Arrays.copyOfRange(packed, 2 + ephLen, packed.length);

        PublicKey ephPub = x25519PublicKey(ephPubDer);

        // ECDH
        KeyAgreement ka = KeyAgreement.getInstance("X25519", "BC");
        ka.init(recipientX25519Priv);
        ka.doPhase(ephPub, true);
        byte[] sharedSecret = ka.generateSecret();

        // HKDF (same inputs as encryption)
        byte[] recPubDer = recipientX25519Pub.getEncoded();
        byte[] ikm = concat(sharedSecret, ephPubDer, recPubDer);
        byte[] aesKey = hkdf(ikm, 32);

        return aesGcmDecrypt(encrypted, aesKey);
    }

    // ── AES-256-GCM ─────────────────────────────────────────────────────────
    // Format: iv(12) | ciphertext+authTag(16)

    public byte[] aesGcmEncrypt(byte[] plaintext, byte[] key) throws GeneralSecurityException {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] ciphertext = cipher.doFinal(plaintext);
        return concat(iv, ciphertext);
    }

    public byte[] aesGcmDecrypt(byte[] packed, byte[] key) throws GeneralSecurityException {
        byte[] iv = Arrays.copyOf(packed, 12);
        byte[] ciphertext = Arrays.copyOfRange(packed, 12, packed.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        return cipher.doFinal(ciphertext);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private byte[] hkdf(byte[] ikm, int outputLen) {
        HKDFBytesGenerator gen = new HKDFBytesGenerator(new SHA256Digest());
        gen.init(new HKDFParameters(ikm, null, null));
        byte[] out = new byte[outputLen];
        gen.generateBytes(out, 0, outputLen);
        return out;
    }

    private static byte[] concat(byte[]... arrays) {
        int total = 0;
        for (byte[] a : arrays) total += a.length;
        byte[] result = new byte[total];
        int pos = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, result, pos, a.length);
            pos += a.length;
        }
        return result;
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }
}
```

- [ ] **Step 4: Run tests — expect pass**

```bash
mvn test -Dtest=ImCryptoServiceTest -q
```
Expected: Tests run: 5, Failures: 0, Errors: 0

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/im/crypto/ src/test/java/com/jaguarliu/ai/im/
git commit -m "feat(im): add ImCryptoService with Ed25519, X25519, AES-GCM"
```

---

### Task 5: ImIdentityService (TDD)

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/im/service/ImIdentityService.java`
- Test: `src/test/java/com/jaguarliu/ai/im/service/ImIdentityServiceTest.java`

- [ ] **Step 1: Write failing test**

`src/test/java/com/jaguarliu/ai/im/service/ImIdentityServiceTest.java`:

```java
package com.jaguarliu.ai.im.service;

import com.jaguarliu.ai.im.crypto.ImCryptoService;
import com.jaguarliu.ai.im.entity.ImIdentityEntity;
import com.jaguarliu.ai.im.repository.ImIdentityRepository;
import org.junit.jupiter.api.*;
import org.mockito.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("ImIdentityService")
class ImIdentityServiceTest {

    @InjectMocks ImIdentityService service;
    @Mock ImIdentityRepository repo;
    @Mock ImCryptoService crypto;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("getOrInit creates identity when none exists")
    void createsOnFirstCall() throws Exception {
        when(repo.findAll()).thenReturn(java.util.List.of());
        var ed25519Pair = new ImCryptoService().generateEd25519KeyPair();
        var x25519Pair  = new ImCryptoService().generateX25519KeyPair();
        when(crypto.generateEd25519KeyPair()).thenReturn(ed25519Pair);
        when(crypto.generateX25519KeyPair()).thenReturn(x25519Pair);
        String expectedNodeId = new ImCryptoService().deriveNodeId(ed25519Pair.getPublic());
        when(crypto.deriveNodeId(any())).thenReturn(expectedNodeId);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ImIdentityEntity result = service.getOrInit();

        assertNotNull(result);
        assertEquals(expectedNodeId, result.getNodeId());
        assertEquals("Me", result.getDisplayName());
        verify(repo).save(any());
    }

    @Test
    @DisplayName("getOrInit returns existing identity when present")
    void returnsExisting() {
        ImIdentityEntity existing = ImIdentityEntity.builder()
            .nodeId("abc123")
            .displayName("Alice")
            .publicKeyEd25519("...")
            .publicKeyX25519("...")
            .privateKeyEd25519("...")
            .privateKeyX25519("...")
            .createdAt(java.time.LocalDateTime.now())
            .build();
        when(repo.findAll()).thenReturn(java.util.List.of(existing));

        ImIdentityEntity result = service.getOrInit();

        assertEquals("abc123", result.getNodeId());
        verify(repo, never()).save(any());
    }
}
```

- [ ] **Step 2: Run test — expect failure**

```bash
mvn test -Dtest=ImIdentityServiceTest -q 2>&1 | tail -5
```

- [ ] **Step 3: Implement ImIdentityService**

`src/main/java/com/jaguarliu/ai/im/service/ImIdentityService.java`:

```java
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
```

- [ ] **Step 4: Run tests — expect pass**

```bash
mvn test -Dtest=ImIdentityServiceTest -q
```
Expected: Tests run: 2, Failures: 0

- [ ] **Step 5: Compile full project**

```bash
mvn compile -q
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/im/service/ImIdentityService.java \
        src/test/java/com/jaguarliu/ai/im/service/ImIdentityServiceTest.java
git commit -m "feat(im): add ImIdentityService with auto key-gen on startup"
```

---

### Task 6: ImEventPublisher + ImLettuceConfig

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/im/event/ImEventPublisher.java`
- Create: `src/main/java/com/jaguarliu/ai/im/config/ImLettuceConfig.java`

- [ ] **Step 1: Check how ConnectionManager broadcasts**

Read `src/main/java/com/jaguarliu/ai/gateway/ws/ConnectionManager.java`. Find the method signature for emitting to a connection (likely `emit(String connectionId, String json)`). Note all active connection IDs are accessible (a `Map<String, ConnectionContext>` or similar).

- [ ] **Step 2: Create ImEventPublisher**

`src/main/java/com/jaguarliu/ai/im/event/ImEventPublisher.java`:

```java
package com.jaguarliu.ai.im.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Broadcasts IM-specific events to all active WebSocket connections.
 * Uses event type prefix "im." so the frontend useIm.ts can subscribe via onEvent().
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImEventPublisher {

    private final ConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    /**
     * Push an IM event to all connected sessions.
     *
     * @param eventType e.g. "im.pair_request", "im.pair_accepted", "im.message"
     * @param payload   any serializable object
     */
    public void broadcast(String eventType, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                "type", "event",
                "event", eventType,
                "payload", payload
            ));
            // ConnectionManager.activeConnectionIds() returns all live connection IDs
            // Adjust the method name to match what ConnectionManager actually exposes
            connectionManager.activeConnectionIds().forEach(connId -> {
                try {
                    connectionManager.emit(connId, json);
                } catch (Exception e) {
                    log.debug("[IM] Failed to emit to connection {}: {}", connId, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("[IM] Failed to broadcast event {}", eventType, e);
        }
    }
}
```

> **Note:** The method `connectionManager.activeConnectionIds()` may not exist by that name.
> After reading ConnectionManager, adapt the call to use whatever method returns active connection IDs.
> If there's no such method, add `public Set<String> activeConnectionIds() { return Collections.unmodifiableSet(connections.keySet()); }` to ConnectionManager.

- [ ] **Step 3: Create ImLettuceConfig**

`src/main/java/com/jaguarliu/ai/im/config/ImLettuceConfig.java`:

```java
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
            RedisURI.Builder builder = RedisURI.builder().withUri(redisUrl)
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
```

- [ ] **Step 4: Compile**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS (fix any ConnectionManager API mismatch before proceeding)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/im/event/ src/main/java/com/jaguarliu/ai/im/config/
git commit -m "feat(im): add ImEventPublisher and ImLettuceConfig"
```

---

### Task 7: ImRegistryService — Redis node registration, heartbeat, discovery

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/im/service/ImRegistryService.java`

- [ ] **Step 1: Implement ImRegistryService**

`src/main/java/com/jaguarliu/ai/im/service/ImRegistryService.java`:

```java
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

    /** Register this node. Called by ImPairingService after Redis is configured. */
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

    private ImNodeDto buildSelfDto(ImIdentityEntity identity) {
        return ImNodeDto.builder()
            .nodeId(identity.getNodeId())
            .displayName(identity.getDisplayName())
            .publicKeyEd25519(identity.getPublicKeyEd25519())
            .publicKeyX25519(identity.getPublicKeyX25519())
            .lastSeen(System.currentTimeMillis())
            .build();
    }
}
```

- [ ] **Step 2: Create ImNodeDto**

`src/main/java/com/jaguarliu/ai/im/dto/ImNodeDto.java`:

```java
package com.jaguarliu.ai.im.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImNodeDto {
    private String nodeId;
    private String displayName;
    private String publicKeyEd25519;
    private String publicKeyX25519;
    private Long lastSeen;
}
```

Also create the remaining DTOs:

`ImContactDto.java`:
```java
package com.jaguarliu.ai.im.dto;
import lombok.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ImContactDto {
    private String nodeId;
    private String displayName;
    private String pairedAt;
    private String status;
}
```

`ImConversationDto.java`:
```java
package com.jaguarliu.ai.im.dto;
import lombok.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ImConversationDto {
    private String id;
    private String displayName;
    private String lastMsg;
    private String lastMsgAt;
    private int unreadCount;
}
```

`ImMessageDto.java`:
```java
package com.jaguarliu.ai.im.dto;
import lombok.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ImMessageDto {
    private String id;
    private String conversationId;
    private String senderNodeId;
    private boolean isMe;
    private String type;
    private String content;  // 解密后明文 JSON
    private String createdAt;
    private String status;
}
```

- [ ] **Step 3: Compile**

```bash
mvn compile -q
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/im/service/ImRegistryService.java \
        src/main/java/com/jaguarliu/ai/im/dto/
git commit -m "feat(im): add ImRegistryService and DTOs"
```

---

### Task 8: IM Settings and Node Discovery RPC Handlers

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/im/handler/ImSettingsGetHandler.java`
- Create: `src/main/java/com/jaguarliu/ai/im/handler/ImSettingsSaveHandler.java`
- Create: `src/main/java/com/jaguarliu/ai/im/handler/ImNodesListHandler.java`

- [ ] **Step 1: ImSettingsGetHandler**

```java
package com.jaguarliu.ai.im.handler;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.*;
import com.jaguarliu.ai.im.entity.ImIdentityEntity;
import com.jaguarliu.ai.im.service.ImIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.Map;

@Component @RequiredArgsConstructor
public class ImSettingsGetHandler implements RpcHandler {
    private final ImIdentityService identityService;

    @Override public String getMethod() { return "im.settings.get"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            ImIdentityEntity id = identityService.getCached();
            return RpcResponse.success(request.getId(), Map.of(
                "nodeId",       id.getNodeId(),
                "displayName",  id.getDisplayName(),
                "redisUrl",     id.getRedisUrl() != null ? id.getRedisUrl() : "",
                "redisConfigured", id.getRedisUrl() != null && !id.getRedisUrl().isBlank()
            ));
        });
    }
}
```

- [ ] **Step 2: ImSettingsSaveHandler**

```java
package com.jaguarliu.ai.im.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.*;
import com.jaguarliu.ai.im.config.ImLettuceConfig;
import com.jaguarliu.ai.im.entity.ImIdentityEntity;
import com.jaguarliu.ai.im.service.ImIdentityService;
import com.jaguarliu.ai.im.service.ImRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.Map;

@Component @RequiredArgsConstructor
public class ImSettingsSaveHandler implements RpcHandler {
    private final ImIdentityService identityService;
    private final ImLettuceConfig lettuceConfig;
    private final ImRegistryService registryService;
    private final ObjectMapper objectMapper;

    @Override public String getMethod() { return "im.settings.save"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> params = objectMapper.convertValue(request.getPayload(), Map.class);
            String displayName = (String) params.get("displayName");
            String redisUrl    = (String) params.get("redisUrl");
            String redisPwd    = (String) params.get("redisPassword");

            ImIdentityEntity id = identityService.getCached();
            if (displayName != null && !displayName.isBlank()) id.setDisplayName(displayName);
            if (redisUrl != null) id.setRedisUrl(redisUrl.trim());
            if (redisPwd != null) id.setRedisPassword(redisPwd);
            identityService.save(id);

            // Reconnect Redis with new config
            lettuceConfig.configure(id.getRedisUrl(), id.getRedisPassword());
            if (lettuceConfig.isConfigured()) {
                registryService.registerSelf();
            }

            return RpcResponse.success(request.getId(), Map.of("ok", true));
        });
    }
}
```

- [ ] **Step 3: ImNodesListHandler**

```java
package com.jaguarliu.ai.im.handler;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.*;
import com.jaguarliu.ai.im.service.ImRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component @RequiredArgsConstructor
public class ImNodesListHandler implements RpcHandler {
    private final ImRegistryService registryService;

    @Override public String getMethod() { return "im.nodes.list"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() ->
            RpcResponse.success(request.getId(), registryService.listOnlineNodes())
        );
    }
}
```

- [ ] **Step 4: Compile and verify 3 new handlers are auto-detected**

```bash
mvn compile -q
# Start app briefly, check log for "[RPC] Registered handler: im.settings.get", etc.
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/im/handler/ImSettingsGetHandler.java \
        src/main/java/com/jaguarliu/ai/im/handler/ImSettingsSaveHandler.java \
        src/main/java/com/jaguarliu/ai/im/handler/ImNodesListHandler.java
git commit -m "feat(im): add settings + node discovery RPC handlers"
```

---

## Chunk 3: Pairing, Messaging, and Frontend

### Task 9: ImPairingService — PAIR_REQUEST/ACCEPT/REJECT

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/im/service/ImPairingService.java`

The pairing service subscribes to `im:requests:{nodeId}` on Redis Pub/Sub and handles incoming PAIR_REQUEST/PAIR_ACCEPT/PAIR_REJECT messages. On accept it persists the contact.

- [ ] **Step 1: Implement ImPairingService**

`src/main/java/com/jaguarliu/ai/im/service/ImPairingService.java`:

```java
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

    /** Call after Redis is successfully configured (from ImSettingsSaveHandler) */
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

        // Build the data that was signed: fromNodeId + fromDisplayName + timestamp
        byte[] signedData = (fromNodeId + ":" + fromDisplayName + ":" + timestamp)
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] sig = Base64.getDecoder().decode(signatureB64);
        byte[] pubKeyDer = Base64.getDecoder().decode(fromPubEd25519);

        if (!crypto.verify(signedData, sig, crypto.ed25519PublicKey(pubKeyDer))) {
            log.warn("[IM] PAIR_REQUEST signature invalid from {}", fromNodeId);
            return;
        }

        // Push event to frontend for user to see notification
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
        // We sent a request to them, they accepted — they should already be in contacts
        // (we stored them optimistically when we sent the request)
        // Just notify the frontend
        eventPublisher.broadcast("im.pair_accepted", Map.of("nodeId", fromNodeId));
        log.info("[IM] PAIR_ACCEPT from {}", fromNodeId);
    }

    private void handlePairReject(Map<String, Object> msg) {
        String fromNodeId = (String) msg.get("fromNodeId");
        // Remove any optimistic contact entry
        contactRepo.deleteById(fromNodeId);
        eventPublisher.broadcast("im.pair_rejected", Map.of("nodeId", fromNodeId));
        log.info("[IM] PAIR_REJECT from {}", fromNodeId);
    }

    /** A sends PAIR_REQUEST to B */
    public void sendPairRequest(String targetNodeId, String targetPubEd25519IP) {
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
            // Store contact
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
                    "type",      msgType,
                    "fromNodeId",self.getNodeId(),
                    "timestamp", ts,
                    "signature", Base64.getEncoder().encodeToString(sig)
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
```

- [ ] **Step 2: Create pairing RPC handlers**

`ImPairRequestHandler.java` — sends a PAIR_REQUEST to a target node:

```java
package com.jaguarliu.ai.im.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.*;
import com.jaguarliu.ai.im.service.ImPairingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.Map;

@Component @RequiredArgsConstructor
public class ImPairRequestHandler implements RpcHandler {
    private final ImPairingService pairingService;
    private final ObjectMapper objectMapper;

    @Override public String getMethod() { return "im.pair.request"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> p = objectMapper.convertValue(request.getPayload(), Map.class);
            String targetNodeId = (String) p.get("targetNodeId");
            if (targetNodeId == null || targetNodeId.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "targetNodeId required");
            }
            pairingService.sendPairRequest(targetNodeId, null);
            return RpcResponse.success(request.getId(), Map.of("ok", true));
        });
    }
}
```

`ImPairRespondHandler.java` — accept or reject an incoming request:

```java
package com.jaguarliu.ai.im.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.*;
import com.jaguarliu.ai.im.service.ImPairingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.Map;

@Component @RequiredArgsConstructor
public class ImPairRespondHandler implements RpcHandler {
    private final ImPairingService pairingService;
    private final ObjectMapper objectMapper;

    @Override public String getMethod() { return "im.pair.respond"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> p = objectMapper.convertValue(request.getPayload(), Map.class);
            String fromNodeId      = (String) p.get("fromNodeId");
            String fromDisplayName = (String) p.get("fromDisplayName");
            String fromPubEd25519  = (String) p.get("fromPubEd25519");
            String fromPubX25519   = (String) p.get("fromPubX25519");
            boolean accept = Boolean.TRUE.equals(p.get("accept"));

            pairingService.respondToPairRequest(fromNodeId, fromDisplayName,
                fromPubEd25519, fromPubX25519, accept);
            return RpcResponse.success(request.getId(), Map.of("ok", true));
        });
    }
}
```

`ImContactsListHandler.java`:

```java
package com.jaguarliu.ai.im.handler;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.*;
import com.jaguarliu.ai.im.dto.ImContactDto;
import com.jaguarliu.ai.im.repository.ImContactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.List;

@Component @RequiredArgsConstructor
public class ImContactsListHandler implements RpcHandler {
    private final ImContactRepository contactRepo;

    @Override public String getMethod() { return "im.contacts.list"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            List<ImContactDto> contacts = contactRepo.findByStatus("active").stream()
                .map(c -> ImContactDto.builder()
                    .nodeId(c.getNodeId())
                    .displayName(c.getDisplayName())
                    .pairedAt(c.getPairedAt().toString())
                    .status(c.getStatus())
                    .build())
                .toList();
            return RpcResponse.success(request.getId(), contacts);
        });
    }
}
```

- [ ] **Step 3: Compile**

```bash
mvn compile -q
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/im/service/ImPairingService.java \
        src/main/java/com/jaguarliu/ai/im/handler/ImPairRequestHandler.java \
        src/main/java/com/jaguarliu/ai/im/handler/ImPairRespondHandler.java \
        src/main/java/com/jaguarliu/ai/im/handler/ImContactsListHandler.java
git commit -m "feat(im): add pairing service and handlers"
```

---

### Task 10: ImMessagingService — E2EE send/receive

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/im/service/ImMessagingService.java`
- Create: handlers: ImMessageSendHandler, ImConversationsListHandler, ImMessagesListHandler

- [ ] **Step 1: Implement ImMessagingService**

`src/main/java/com/jaguarliu/ai/im/service/ImMessagingService.java`:

```java
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

    // ── SEND ─────────────────────────────────────────────────────────────────

    /**
     * Encrypt and publish a TEXT message to a contact.
     * @param toNodeId  recipient nodeId
     * @param text      plaintext
     * @return messageId (UUID)
     */
    public String sendText(String toNodeId, String text) {
        ImContactEntity contact = contactRepo.findById(toNodeId)
            .orElseThrow(() -> new IllegalArgumentException("Not a paired contact: " + toNodeId));

        try {
            ImIdentityEntity self = identityService.getCached();
            String messageId = UUID.randomUUID().toString();
            long ts = System.currentTimeMillis();

            // 1. Session key (AES-256)
            byte[] sessionKey = new byte[32];
            new SecureRandom().nextBytes(sessionKey);

            // 2. Encrypt plaintext with session key
            String contentJson = objectMapper.writeValueAsString(Map.of("text", text));
            byte[] plaintext = contentJson.getBytes(StandardCharsets.UTF_8);
            byte[] encryptedBody = crypto.aesGcmEncrypt(plaintext, sessionKey);

            // 3. Seal session key with recipient's X25519 public key
            byte[] recipientX25519Der = Base64.getDecoder().decode(contact.getPublicKeyX25519());
            byte[] encryptedKey = crypto.sealedBoxEncrypt(sessionKey,
                crypto.x25519PublicKey(recipientX25519Der));

            // 4. Sign: encryptedKey + encryptedBody + timestamp
            byte[] privKeyDer = Base64.getDecoder().decode(self.getPrivateKeyEd25519());
            byte[] dataToSign = concat(encryptedKey, encryptedBody, longToBytes(ts));
            byte[] sig = crypto.sign(dataToSign, crypto.ed25519PrivateKey(privKeyDer));

            // 5. Build wire message
            Map<String, Object> wireMsg = new LinkedHashMap<>();
            wireMsg.put("type",         "MESSAGE");
            wireMsg.put("fromNodeId",   self.getNodeId());
            wireMsg.put("fromAgentId",  null);
            wireMsg.put("toAgentId",    null);
            wireMsg.put("messageId",    messageId);
            wireMsg.put("timestamp",    ts);
            wireMsg.put("encryptedKey", Base64.getEncoder().encodeToString(encryptedKey));
            wireMsg.put("encryptedBody",Base64.getEncoder().encodeToString(encryptedBody));
            wireMsg.put("signature",    Base64.getEncoder().encodeToString(sig));

            // 6. Publish to Redis
            String channel = "im:messages:" + toNodeId;
            lettuceConfig.getClient().ifPresent(client -> {
                try (StatefulRedisConnection<String, String> conn = client.connect()) {
                    conn.sync().publish(channel, objectMapper.writeValueAsString(wireMsg));
                } catch (Exception e) { throw new RuntimeException(e); }
            });

            // 7. Persist as sent message
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

    // ── RECEIVE ───────────────────────────────────────────────────────────────

    private void handleIncomingMessage(String json) {
        try {
            Map<String, Object> msg = objectMapper.readValue(json, Map.class);
            String fromNodeId = (String) msg.get("fromNodeId");
            String messageId  = (String) msg.get("messageId");

            // 1. Must be from a paired contact
            Optional<ImContactEntity> contactOpt = contactRepo.findById(fromNodeId);
            if (contactOpt.isEmpty()) {
                log.debug("[IM] Dropping message from unpaired node: {}", fromNodeId);
                return;
            }
            ImContactEntity contact = contactOpt.get();
            if ("blocked".equals(contact.getStatus())) return;

            // 2. Verify signature
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

            // 3. Decrypt session key
            ImIdentityEntity self = identityService.getCached();
            byte[] privKeyDer = Base64.getDecoder().decode(self.getPrivateKeyX25519());
            byte[] pubKeyDer  = Base64.getDecoder().decode(self.getPublicKeyX25519());
            byte[] sessionKey = crypto.sealedBoxDecrypt(encryptedKey,
                crypto.x25519PrivateKey(privKeyDer), crypto.x25519PublicKey(pubKeyDer));

            // 4. Decrypt body
            byte[] plaintext = crypto.aesGcmDecrypt(encryptedBody, sessionKey);
            String contentJson = new String(plaintext, StandardCharsets.UTF_8);

            // 5. Persist
            ImMessageEntity entity = ImMessageEntity.builder()
                .id(messageId)
                .conversationId(fromNodeId)
                .senderNodeId(fromNodeId)
                .type("TEXT")
                .content(contentJson)
                .createdAt(LocalDateTime.ofEpochSecond(ts / 1000, 0,
                    java.time.ZoneOffset.UTC))
                .status("delivered")
                .build();
            messageRepo.save(entity);

            // 6. Update conversation
            String preview = objectMapper.readTree(contentJson).path("text").asText("");
            updateConversation(fromNodeId, contact.getDisplayName(), preview, entity.getCreatedAt());

            // 7. Push to frontend
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
        conv.setLastMsg(lastMsg.length() > 80 ? lastMsg.substring(0, 80) + "…" : lastMsg);
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

    private static final java.security.SecureRandom SR = new java.security.SecureRandom();
    private static class SecureRandom extends java.security.SecureRandom {}
}
```

- [ ] **Step 2: Create message-related RPC handlers**

`ImMessageSendHandler.java`:

```java
package com.jaguarliu.ai.im.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.*;
import com.jaguarliu.ai.im.service.ImMessagingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.Map;

@Component @RequiredArgsConstructor
public class ImMessageSendHandler implements RpcHandler {
    private final ImMessagingService messagingService;
    private final ObjectMapper objectMapper;

    @Override public String getMethod() { return "im.message.send"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> p = objectMapper.convertValue(request.getPayload(), Map.class);
            String toNodeId = (String) p.get("toNodeId");
            String text     = (String) p.get("text");
            if (toNodeId == null || text == null || text.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "toNodeId and text required");
            }
            String messageId = messagingService.sendText(toNodeId, text);
            return RpcResponse.success(request.getId(), Map.of("messageId", messageId));
        }).onErrorResume(e ->
            Mono.just(RpcResponse.error(request.getId(), "SEND_FAILED", e.getMessage()))
        );
    }
}
```

`ImConversationsListHandler.java`:

```java
package com.jaguarliu.ai.im.handler;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.*;
import com.jaguarliu.ai.im.dto.ImConversationDto;
import com.jaguarliu.ai.im.repository.ImConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.List;

@Component @RequiredArgsConstructor
public class ImConversationsListHandler implements RpcHandler {
    private final ImConversationRepository conversationRepo;

    @Override public String getMethod() { return "im.conversations.list"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            List<ImConversationDto> convs = conversationRepo.findAllOrderByLastMsgAtDesc().stream()
                .map(c -> ImConversationDto.builder()
                    .id(c.getId())
                    .displayName(c.getDisplayName())
                    .lastMsg(c.getLastMsg())
                    .lastMsgAt(c.getLastMsgAt() != null ? c.getLastMsgAt().toString() : null)
                    .unreadCount(c.getUnreadCount())
                    .build())
                .toList();
            return RpcResponse.success(request.getId(), convs);
        });
    }
}
```

`ImMessagesListHandler.java`:

```java
package com.jaguarliu.ai.im.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.*;
import com.jaguarliu.ai.im.dto.ImMessageDto;
import com.jaguarliu.ai.im.repository.ImMessageRepository;
import com.jaguarliu.ai.im.service.ImIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.Map;

@Component @RequiredArgsConstructor
public class ImMessagesListHandler implements RpcHandler {
    private final ImMessageRepository messageRepo;
    private final ImIdentityService identityService;
    private final ObjectMapper objectMapper;

    @Override public String getMethod() { return "im.messages.list"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> p = objectMapper.convertValue(request.getPayload(), Map.class);
            String conversationId = (String) p.get("conversationId");
            if (conversationId == null) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "conversationId required");
            }
            String selfNodeId = identityService.getCached().getNodeId();
            List<ImMessageDto> msgs = messageRepo
                .findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(m -> ImMessageDto.builder()
                    .id(m.getId())
                    .conversationId(m.getConversationId())
                    .senderNodeId(m.getSenderNodeId())
                    .isMe(m.getSenderNodeId().equals(selfNodeId))
                    .type(m.getType())
                    .content(m.getContent())
                    .createdAt(m.getCreatedAt().toString())
                    .status(m.getStatus())
                    .build())
                .toList();
            return RpcResponse.success(request.getId(), msgs);
        });
    }
}
```

- [ ] **Step 3: Wire subscriptions — start on Redis configure**

In `ImSettingsSaveHandler`, after `registryService.registerSelf()`, add:

```java
// Start message + pairing subscriptions with new Redis config
pairingService.startSubscriptions();
messagingService.startSubscriptions();
```

Add `ImPairingService` and `ImMessagingService` to `ImSettingsSaveHandler`'s constructor injection.

Also add `@PostConstruct` in `ImSettingsSaveHandler` (or a new `ImStartupService`) that reconnects if Redis was already saved:

```java
@PostConstruct
void onStartup() {
    ImIdentityEntity id = identityService.getCached();
    if (id.getRedisUrl() != null && !id.getRedisUrl().isBlank()) {
        lettuceConfig.configure(id.getRedisUrl(), id.getRedisPassword());
        registryService.registerSelf();
        pairingService.startSubscriptions();
        messagingService.startSubscriptions();
    }
}
```

- [ ] **Step 4: Compile the entire backend**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/im/
git commit -m "feat(im): add messaging service + all IM RPC handlers"
```

---

### Task 11: Frontend — Types and useIm.ts composable

**Files:**
- Modify: `jaguarclaw-ui/src/types/index.ts`
- Create: `jaguarclaw-ui/src/composables/useIm.ts`

- [ ] **Step 1: Add IM types to types/index.ts**

Append to end of `jaguarclaw-ui/src/types/index.ts`:

```typescript
// ── IM Types ────────────────────────────────────────────────────────────────

export interface ImSettings {
  nodeId: string
  displayName: string
  redisUrl: string
  redisConfigured: boolean
}

export interface ImNode {
  nodeId: string
  displayName: string
  publicKeyEd25519: string
  publicKeyX25519: string
  lastSeen: number
}

export interface ImContact {
  nodeId: string
  displayName: string
  pairedAt: string
  status: 'active' | 'blocked'
}

export interface ImConversation {
  id: string          // peer nodeId
  displayName: string
  lastMsg: string
  lastMsgAt: string | null
  unreadCount: number
}

export interface ImMessage {
  id: string
  conversationId: string
  senderNodeId: string
  isMe: boolean
  type: 'TEXT' | 'IMAGE' | 'FILE' | 'AGENT_MESSAGE'
  content: string     // JSON string: { "text": "..." }
  createdAt: string
  status: 'sent' | 'delivered' | 'failed'
}

export interface ImPairRequestEvent {
  fromNodeId: string
  fromDisplayName: string
  fromPubEd25519: string
  fromPubX25519: string
  timestamp: number
}
```

- [ ] **Step 2: Create useIm.ts**

`jaguarclaw-ui/src/composables/useIm.ts`:

```typescript
import { ref, onUnmounted } from 'vue'
import { useWebSocket } from './useWebSocket'
import type {
  ImSettings, ImNode, ImContact, ImConversation, ImMessage, ImPairRequestEvent
} from '@/types'

// Module-level state — shared across all consumers
const settings    = ref<ImSettings | null>(null)
const nodes       = ref<ImNode[]>([])
const contacts    = ref<ImContact[]>([])
const conversations = ref<ImConversation[]>([])
const messages    = ref<Record<string, ImMessage[]>>({})  // conversationId → messages
const pendingPairRequests = ref<ImPairRequestEvent[]>([])
const activeConversationId = ref<string | null>(null)
const loading     = ref(false)
const error       = ref<string | null>(null)

let listenersSetup = false

export function useIm() {
  const { request, onEvent } = useWebSocket()

  // ── Settings ───────────────────────────────────────────────────────────────

  async function loadSettings(): Promise<void> {
    loading.value = true
    error.value = null
    try {
      settings.value = await request<ImSettings>('im.settings.get')
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load IM settings'
    } finally {
      loading.value = false
    }
  }

  async function saveSettings(input: {
    displayName?: string
    redisUrl?: string
    redisPassword?: string
  }): Promise<void> {
    await request('im.settings.save', input)
    await loadSettings()
  }

  // ── Discovery ──────────────────────────────────────────────────────────────

  async function refreshNodes(): Promise<void> {
    nodes.value = await request<ImNode[]>('im.nodes.list')
  }

  // ── Contacts ───────────────────────────────────────────────────────────────

  async function loadContacts(): Promise<void> {
    contacts.value = await request<ImContact[]>('im.contacts.list')
  }

  async function sendPairRequest(targetNodeId: string): Promise<void> {
    await request('im.pair.request', { targetNodeId })
  }

  async function respondToPairRequest(
    event: ImPairRequestEvent,
    accept: boolean
  ): Promise<void> {
    await request('im.pair.respond', {
      fromNodeId:      event.fromNodeId,
      fromDisplayName: event.fromDisplayName,
      fromPubEd25519:  event.fromPubEd25519,
      fromPubX25519:   event.fromPubX25519,
      accept,
    })
    // Remove from pending
    pendingPairRequests.value = pendingPairRequests.value
      .filter(r => r.fromNodeId !== event.fromNodeId)
    if (accept) await loadContacts()
  }

  // ── Conversations + Messages ───────────────────────────────────────────────

  async function loadConversations(): Promise<void> {
    conversations.value = await request<ImConversation[]>('im.conversations.list')
  }

  async function loadMessages(conversationId: string): Promise<void> {
    const msgs = await request<ImMessage[]>('im.messages.list', { conversationId })
    messages.value = { ...messages.value, [conversationId]: msgs }
    activeConversationId.value = conversationId
  }

  async function sendMessage(toNodeId: string, text: string): Promise<void> {
    const { messageId } = await request<{ messageId: string }>('im.message.send', {
      toNodeId,
      text,
    })
    // Optimistically append sent message
    const sentMsg: ImMessage = {
      id:             messageId,
      conversationId: toNodeId,
      senderNodeId:   settings.value?.nodeId ?? '',
      isMe:           true,
      type:           'TEXT',
      content:        JSON.stringify({ text }),
      createdAt:      new Date().toISOString(),
      status:         'sent',
    }
    messages.value = {
      ...messages.value,
      [toNodeId]: [...(messages.value[toNodeId] ?? []), sentMsg],
    }
    // Refresh conversation list for updated last_msg
    await loadConversations()
  }

  // ── Event Listeners (set up once) ─────────────────────────────────────────

  function setupListeners() {
    if (listenersSetup) return
    listenersSetup = true

    onEvent('im.pair_request', (event) => {
      const payload = event.payload as ImPairRequestEvent
      pendingPairRequests.value = [...pendingPairRequests.value, payload]
    })

    onEvent('im.pair_accepted', () => {
      loadContacts()
    })

    onEvent('im.pair_rejected', (event) => {
      const { nodeId } = event.payload as { nodeId: string }
      contacts.value = contacts.value.filter(c => c.nodeId !== nodeId)
    })

    onEvent('im.message', (event) => {
      const msg = event.payload as {
        conversationId: string
        messageId: string
        senderNodeId: string
        displayName: string
        type: string
        content: string
        createdAt: string
      }
      const incoming: ImMessage = {
        id:             msg.messageId,
        conversationId: msg.conversationId,
        senderNodeId:   msg.senderNodeId,
        isMe:           false,
        type:           msg.type as ImMessage['type'],
        content:        msg.content,
        createdAt:      msg.createdAt,
        status:         'delivered',
      }
      const conv = msg.conversationId
      messages.value = {
        ...messages.value,
        [conv]: [...(messages.value[conv] ?? []), incoming],
      }
      loadConversations()
    })
  }

  setupListeners()

  return {
    settings,
    nodes,
    contacts,
    conversations,
    messages,
    pendingPairRequests,
    activeConversationId,
    loading,
    error,
    loadSettings,
    saveSettings,
    refreshNodes,
    loadContacts,
    sendPairRequest,
    respondToPairRequest,
    loadConversations,
    loadMessages,
    sendMessage,
  }
}
```

- [ ] **Step 3: TypeScript check**

```bash
cd jaguarclaw-ui && npx tsc --noEmit 2>&1 | head -20
```
Expected: no errors

- [ ] **Step 4: Commit**

```bash
git add jaguarclaw-ui/src/types/index.ts jaguarclaw-ui/src/composables/useIm.ts
git commit -m "feat(im): add IM types and useIm composable"
```

---

### Task 12: Frontend UI — ImView, components, settings, routing

**Files:**
- Create: `jaguarclaw-ui/src/views/ImView.vue`
- Create: `jaguarclaw-ui/src/components/im/ImContactList.vue`
- Create: `jaguarclaw-ui/src/components/im/ImChatWindow.vue`
- Create: `jaguarclaw-ui/src/components/im/ImPairToast.vue`
- Create: `jaguarclaw-ui/src/components/settings/ImConfigSection.vue`
- Modify: `jaguarclaw-ui/src/router/index.ts`
- Modify: `jaguarclaw-ui/src/components/layout/ModeSwitcher.vue`
- Modify: `jaguarclaw-ui/src/views/SettingsView.vue`
- Modify: `jaguarclaw-ui/src/components/settings/SettingsSidebar.vue`

- [ ] **Step 1: Create ImView.vue**

`jaguarclaw-ui/src/views/ImView.vue`:

```vue
<script setup lang="ts">
import { onMounted } from 'vue'
import { useIm } from '@/composables/useIm'
import ImContactList from '@/components/im/ImContactList.vue'
import ImChatWindow from '@/components/im/ImChatWindow.vue'
import ImPairToast from '@/components/im/ImPairToast.vue'

const {
  contacts, conversations, messages, pendingPairRequests,
  activeConversationId, settings,
  loadSettings, loadContacts, loadConversations,
  loadMessages, sendMessage,
  sendPairRequest, respondToPairRequest,
} = useIm()

onMounted(async () => {
  await loadSettings()
  await loadContacts()
  await loadConversations()
})
</script>

<template>
  <div class="im-view">
    <!-- Pair request toasts -->
    <ImPairToast
      v-for="req in pendingPairRequests"
      :key="req.fromNodeId"
      :request="req"
      @accept="respondToPairRequest(req, true)"
      @reject="respondToPairRequest(req, false)"
    />

    <!-- Contact/conversation list -->
    <ImContactList
      :contacts="contacts"
      :conversations="conversations"
      :active-id="activeConversationId"
      @select="loadMessages"
      @pair-request="sendPairRequest"
    />

    <!-- Chat window -->
    <ImChatWindow
      v-if="activeConversationId"
      :conversation-id="activeConversationId"
      :messages="messages[activeConversationId] ?? []"
      :self-node-id="settings?.nodeId ?? ''"
      @send="(text) => sendMessage(activeConversationId!, text)"
    />

    <div v-else class="im-empty">
      <p>Select a conversation or pair with a new contact</p>
    </div>
  </div>
</template>

<style scoped>
.im-view {
  display: flex;
  height: 100%;
  background: var(--content-bg);
  position: relative;
}
.im-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-gray-400);
  font-size: 14px;
}
</style>
```

- [ ] **Step 2: Create ImContactList.vue**

`jaguarclaw-ui/src/components/im/ImContactList.vue`:

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { useIm } from '@/composables/useIm'
import type { ImContact, ImConversation } from '@/types'

const props = defineProps<{
  contacts: ImContact[]
  conversations: ImConversation[]
  activeId: string | null
}>()

const emit = defineEmits<{
  select: [conversationId: string]
  'pair-request': [targetNodeId: string]
}>()

const { nodes, refreshNodes } = useIm()
const showDiscover = ref(false)
const discovering = ref(false)

async function discover() {
  discovering.value = true
  await refreshNodes()
  discovering.value = false
  showDiscover.value = true
}
</script>

<template>
  <div class="contact-list">
    <div class="contact-header">
      <span class="title">Messages</span>
      <button class="discover-btn" @click="discover" :disabled="discovering" title="Find contacts">
        +
      </button>
    </div>

    <!-- Existing conversations -->
    <div
      v-for="conv in conversations"
      :key="conv.id"
      class="conv-item"
      :class="{ active: conv.id === activeId }"
      @click="emit('select', conv.id)"
    >
      <div class="conv-avatar">{{ conv.displayName?.[0]?.toUpperCase() ?? '?' }}</div>
      <div class="conv-info">
        <div class="conv-name">{{ conv.displayName }}</div>
        <div class="conv-last">{{ conv.lastMsg }}</div>
      </div>
      <div v-if="conv.unreadCount > 0" class="unread-badge">{{ conv.unreadCount }}</div>
    </div>

    <!-- Discover panel -->
    <div v-if="showDiscover" class="discover-panel">
      <div class="discover-title">Online nodes</div>
      <div v-if="nodes.length === 0" class="discover-empty">No other nodes online</div>
      <div
        v-for="node in nodes"
        :key="node.nodeId"
        class="node-item"
        @click="emit('pair-request', node.nodeId); showDiscover = false"
      >
        <div class="conv-avatar">{{ node.displayName?.[0]?.toUpperCase() ?? '?' }}</div>
        <div class="conv-info">
          <div class="conv-name">{{ node.displayName }}</div>
          <div class="conv-last">{{ node.nodeId.slice(0, 8) }}…</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.contact-list {
  width: 260px;
  border-right: var(--border);
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  background: var(--sidebar-panel-bg);
}
.contact-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: var(--border);
  font-weight: 600;
  font-size: 14px;
}
.discover-btn {
  width: 24px; height: 24px;
  border: var(--border);
  background: var(--color-white);
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
}
.conv-item, .node-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  cursor: pointer;
  border-bottom: var(--border-light);
}
.conv-item:hover, .node-item:hover { background: var(--sidebar-item-hover-bg); }
.conv-item.active { background: var(--color-gray-100); }
.conv-avatar {
  width: 36px; height: 36px;
  border-radius: var(--radius-full);
  background: var(--color-primary);
  color: white;
  display: flex; align-items: center; justify-content: center;
  font-weight: 600; font-size: 14px;
  flex-shrink: 0;
}
.conv-info { flex: 1; min-width: 0; }
.conv-name { font-size: 13px; font-weight: 600; }
.conv-last { font-size: 12px; color: var(--color-gray-500); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.unread-badge {
  background: var(--color-primary);
  color: white;
  border-radius: var(--radius-full);
  font-size: 11px;
  min-width: 18px; height: 18px;
  display: flex; align-items: center; justify-content: center;
  padding: 0 4px;
}
.discover-panel { border-top: var(--border); padding: 8px 0; }
.discover-title { padding: 4px 16px; font-size: 11px; color: var(--color-gray-500); text-transform: uppercase; letter-spacing: 0.05em; }
.discover-empty { padding: 8px 16px; font-size: 13px; color: var(--color-gray-400); }
</style>
```

- [ ] **Step 3: Create ImChatWindow.vue**

`jaguarclaw-ui/src/components/im/ImChatWindow.vue`:

```vue
<script setup lang="ts">
import { ref, nextTick, watch } from 'vue'
import type { ImMessage } from '@/types'

const props = defineProps<{
  conversationId: string
  messages: ImMessage[]
  selfNodeId: string
}>()

const emit = defineEmits<{ send: [text: string] }>()

const inputText = ref('')
const containerRef = ref<HTMLElement | null>(null)

watch(() => props.messages.length, async () => {
  await nextTick()
  if (containerRef.value) containerRef.value.scrollTop = containerRef.value.scrollHeight
})

function handleSend() {
  const text = inputText.value.trim()
  if (!text) return
  emit('send', text)
  inputText.value = ''
}

function extractText(content: string): string {
  try { return JSON.parse(content).text ?? content } catch { return content }
}
</script>

<template>
  <div class="chat-window">
    <div class="messages" ref="containerRef">
      <div
        v-for="msg in messages"
        :key="msg.id"
        class="bubble-row"
        :class="{ me: msg.isMe }"
      >
        <div class="bubble" :class="{ me: msg.isMe }">
          {{ extractText(msg.content) }}
        </div>
        <div class="bubble-time">{{ new Date(msg.createdAt).toLocaleTimeString() }}</div>
      </div>
    </div>

    <div class="input-bar">
      <textarea
        v-model="inputText"
        class="msg-input"
        placeholder="Type a message…"
        rows="1"
        @keydown.enter.exact.prevent="handleSend"
      />
      <button class="send-btn" @click="handleSend">Send</button>
    </div>
  </div>
</template>

<style scoped>
.chat-window { flex: 1; display: flex; flex-direction: column; }
.messages { flex: 1; overflow-y: auto; padding: 20px; display: flex; flex-direction: column; gap: 8px; }
.bubble-row { display: flex; flex-direction: column; }
.bubble-row.me { align-items: flex-end; }
.bubble {
  max-width: 70%; padding: 8px 12px;
  border-radius: var(--radius-lg);
  background: var(--color-gray-100);
  font-size: 14px; line-height: 1.5;
  white-space: pre-wrap; word-break: break-word;
}
.bubble.me { background: var(--color-primary); color: white; }
.bubble-time { font-size: 11px; color: var(--color-gray-400); margin-top: 2px; }
.input-bar { border-top: var(--border); padding: 12px 16px; display: flex; gap: 8px; }
.msg-input {
  flex: 1; resize: none;
  border: var(--border); border-radius: var(--radius-md);
  padding: 8px 12px; font-family: var(--font-ui); font-size: 14px;
  outline: none;
}
.msg-input:focus { border-color: var(--color-primary); }
.send-btn {
  padding: 8px 16px;
  background: var(--color-primary); color: white;
  border: none; border-radius: var(--radius-md);
  font-size: 13px; font-weight: 500; cursor: pointer;
}
.send-btn:hover { opacity: 0.85; }
</style>
```

- [ ] **Step 4: Create ImPairToast.vue**

`jaguarclaw-ui/src/components/im/ImPairToast.vue`:

```vue
<script setup lang="ts">
import type { ImPairRequestEvent } from '@/types'
defineProps<{ request: ImPairRequestEvent }>()
const emit = defineEmits<{ accept: []; reject: [] }>()
</script>

<template>
  <div class="pair-toast">
    <div class="pair-info">
      <strong>「{{ request.fromDisplayName }}」</strong> wants to connect
      <span class="fingerprint">{{ request.fromNodeId.slice(0, 8) }}…</span>
    </div>
    <div class="pair-actions">
      <button class="btn-accept" @click="emit('accept')">Accept</button>
      <button class="btn-reject" @click="emit('reject')">Reject</button>
    </div>
  </div>
</template>

<style scoped>
.pair-toast {
  position: fixed; top: 16px; right: 16px; z-index: 9999;
  background: white; border: var(--border); border-radius: var(--radius-lg);
  padding: 14px 16px; box-shadow: var(--shadow-lg);
  display: flex; align-items: center; gap: 12px;
  max-width: 340px;
}
.pair-info { flex: 1; font-size: 13px; }
.fingerprint { font-family: var(--font-mono); font-size: 11px; color: var(--color-gray-500); margin-left: 4px; }
.pair-actions { display: flex; gap: 6px; }
.btn-accept {
  padding: 5px 12px; background: var(--color-primary); color: white;
  border: none; border-radius: var(--radius-md); font-size: 12px; cursor: pointer;
}
.btn-reject {
  padding: 5px 12px; border: var(--border); border-radius: var(--radius-md);
  background: white; font-size: 12px; cursor: pointer;
}
</style>
```

- [ ] **Step 5: Create ImConfigSection.vue** (minimal settings form)

`jaguarclaw-ui/src/components/settings/ImConfigSection.vue`:

```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useIm } from '@/composables/useIm'

const { settings, loadSettings, saveSettings } = useIm()

const displayName = ref('')
const redisUrl    = ref('')
const redisPassword = ref('')
const saving = ref(false)
const saved  = ref(false)

onMounted(async () => {
  await loadSettings()
  displayName.value = settings.value?.displayName ?? ''
  redisUrl.value    = settings.value?.redisUrl ?? ''
})

async function save() {
  saving.value = true
  saved.value  = false
  try {
    await saveSettings({ displayName: displayName.value, redisUrl: redisUrl.value, redisPassword: redisPassword.value })
    saved.value = true
    setTimeout(() => { saved.value = false }, 2000)
  } finally { saving.value = false }
}
</script>

<template>
  <div class="im-config">
    <h2>IM Settings</h2>
    <p class="node-id" v-if="settings">Node ID: <code>{{ settings.nodeId }}</code></p>

    <div class="field">
      <label>Display Name</label>
      <input v-model="displayName" type="text" placeholder="Your name shown to contacts" />
    </div>

    <div class="field">
      <label>Redis URL</label>
      <input v-model="redisUrl" type="text" placeholder="redis://192.168.1.10:6379" />
    </div>

    <div class="field">
      <label>Redis Password (optional)</label>
      <input v-model="redisPassword" type="password" placeholder="Leave blank if none" />
    </div>

    <button class="save-btn" @click="save" :disabled="saving">
      {{ saving ? 'Saving…' : saved ? 'Saved ✓' : 'Save' }}
    </button>
  </div>
</template>

<style scoped>
.im-config { padding: 32px; max-width: 480px; }
h2 { font-size: 18px; font-weight: 600; margin-bottom: 20px; }
.node-id { font-size: 12px; color: var(--color-gray-500); margin-bottom: 20px; }
.node-id code { font-family: var(--font-mono); background: var(--color-gray-100); padding: 2px 4px; border-radius: 3px; }
.field { margin-bottom: 16px; }
label { display: block; font-size: 13px; font-weight: 500; margin-bottom: 6px; }
input {
  width: 100%; padding: 8px 12px;
  border: var(--border); border-radius: var(--radius-md);
  font-size: 14px; outline: none;
}
input:focus { border-color: var(--color-primary); }
.save-btn {
  padding: 8px 20px; background: var(--color-primary); color: white;
  border: none; border-radius: var(--radius-md); font-size: 13px;
  font-weight: 500; cursor: pointer; margin-top: 8px;
}
.save-btn:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
```

- [ ] **Step 6: Add IM route to router**

In `jaguarclaw-ui/src/router/index.ts`, add:

```typescript
import ImView from '@/views/ImView.vue'
```

And add to the `routes` array:

```typescript
{
  path: '/im',
  name: 'im',
  component: ImView,
},
```

- [ ] **Step 7: Add IM button to ModeSwitcher**

In `jaguarclaw-ui/src/components/layout/ModeSwitcher.vue`:

In the `switchTo` function add:
```typescript
else if (mode === 'im') router.push('/im')
```

In `currentMode` computed add:
```typescript
if (route.path.startsWith('/im')) return 'im'
```

Add to the template after the documents button:

```html
<button class="mode-btn" :class="{ active: currentMode === 'im' }"
        @click="switchTo('im' as any)" title="IM">
  <span class="icon">&#9993;</span>
</button>
```

- [ ] **Step 8: Add IM section to SettingsView and SettingsSidebar**

In `SettingsView.vue`, add import and v-else-if:

```typescript
import ImConfigSection from '@/components/settings/ImConfigSection.vue'
```

```html
<ImConfigSection v-else-if="currentSection === 'im'" />
```

In `SettingsSidebar.vue`, add to the integration group:

```typescript
{ id: 'im', label: 'IM' },
```

- [ ] **Step 9: TypeScript check + build**

```bash
cd jaguarclaw-ui && npx tsc --noEmit 2>&1 | head -20
```
Expected: no errors

```bash
npm run build 2>&1 | tail -10
```
Expected: BUILD SUCCESS (or equivalent)

- [ ] **Step 10: Commit**

```bash
git add jaguarclaw-ui/src/
git commit -m "feat(im): add IM UI — ImView, chat components, settings, routing"
```

---

## End-to-End Verification (manual test with 2 nodes)

- [ ] Run two instances of the app (or on two machines on the same network)
- [ ] On each: set Display Name + same Redis URL in Settings → IM
- [ ] Node A: click "+" to discover nodes, see Node B, click to send pair request
- [ ] Node B: sees pair request toast, clicks Accept
- [ ] Node A: receives accepted notification
- [ ] Both: can now see each other in conversation list
- [ ] Node A: types a message, Node B receives it in real time
- [ ] Verify: wrong password / tampered message does not get delivered

---

## Next Plans

- **Plan B** — File P2P transfer (JDK HttpServer, token-based, SHA-256 verify)
- **Plan C** — Agent integration (fromAgentId/toAgentId routing, Agent pub/sub subscription)
