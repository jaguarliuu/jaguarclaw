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
