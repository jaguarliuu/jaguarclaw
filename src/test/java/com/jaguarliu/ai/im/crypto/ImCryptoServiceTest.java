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
